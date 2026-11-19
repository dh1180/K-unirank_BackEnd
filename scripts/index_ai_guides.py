"""Build the read-only Spring guide index from operator-reviewed official PDFs.
Usage: python scripts/index_ai_guides.py (requires pypdf; no runtime Python server).
Review title/year, official publication and extracted page text before committing.
"""
import hashlib, io, json, re, urllib.request
from pathlib import Path
from urllib.parse import urlparse
from pypdf import PdfReader
# Use OS certificate roots where the official host omits an intermediate.
try:
    import truststore
    truststore.inject_into_ssl()
except ImportError:
    pass
ROOT = Path(__file__).resolve().parents[1]
ALLOWED = {"ibhak.kau.ac.kr", "admission.gachon.ac.kr"}
index=[]
for document in json.loads((ROOT/"scripts/ai-guide-manifest.json").read_text(encoding="utf-8")):
    url=document["sourceUrl"]
    assert urlparse(url).scheme=="https" and urlparse(url).hostname in ALLOWED
    response=urllib.request.urlopen(url,timeout=40)
    assert urlparse(response.url).hostname in ALLOWED
    raw=response.read(30*1024*1024+1)
    assert len(raw)<=30*1024*1024 and raw.startswith(b"%PDF")
    reader=PdfReader(io.BytesIO(raw))
    assert not reader.is_encrypted and 1<=len(reader.pages)<=500
    pages=[]
    for number,page in enumerate(reader.pages,1):
        text=re.sub(r"[ \t]+"," ",page.extract_text() or "").strip()
        if len(text)>60: pages.append({"page":number,"text":text})
    assert pages
    index.append({**document,"sha256":hashlib.sha256(raw).hexdigest(),"pageCount":len(reader.pages),"pages":pages})
    print(document["university"],document["year"],len(pages),"pages")
output=ROOT/"backend/src/main/resources/ai/official-guides.json"
output.write_text(json.dumps(index,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
