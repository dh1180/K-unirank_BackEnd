import type { Metadata } from "next";
import Link from "next/link";
import { listUniversities } from "@/lib/api";
import { positivePage, toQuery } from "@/lib/query.mjs";
import { Notice } from "@/components/Notice";
import { Pagination } from "@/components/Pagination";

export const metadata: Metadata = { title: "대학 목록" };

type Search = { q?: string; page?: string };
export default async function UniversitiesPage({
  searchParams,
}: { searchParams: Promise<Search> }) {
  const params = await searchParams;
  const q = typeof params.q === "string" ? params.q.slice(0, 100).trim() : "";
  const page = positivePage(params.page);
  const data = await listUniversities(q, page);

  return (
    <>
      <section className="page-header">
        <h1>대학 찾기</h1>
        <p>기존 PostgreSQL에 저장된 대학 정보를 조회해.</p>
      </section>
      <form action="/universities/" className="search-form" method="get">
        <label htmlFor="university-q">대학명</label>
        <input id="university-q" name="q" type="search" defaultValue={q}
               placeholder="예: 한국항공대학교" maxLength={100} />
        <button className="button primary" type="submit">검색</button>
      </form>
      {data === null ? (
        <Notice>대학 API에 연결할 수 없어. Spring Boot와 API_INTERNAL_URL 설정을 확인해 줘.</Notice>
      ) : (
        <>
          <p className="result-count">검색 결과 {data.totalCount.toLocaleString("ko-KR")}개 대학</p>
          {data.items.length === 0 ? (
            <Notice>조건에 맞는 대학이 없어.</Notice>
          ) : (
            <ul className="item-list">
              {data.items.map(item => (
                <li className="list-item" key={item.id}>
                  <div>
                    <strong><Link href={`/universities/${item.id}/`}>{item.name}</Link></strong>
                    <p className="muted">{item.region ?? "지역 미상"} · 대학 ID {item.id}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <Pagination href="/universities/" page={page} hasNext={data.hasNext} params={{ q }} />
        </>
      )}

    </>
  );
}
