# Spring Boot → Raspberry Pi 5 FastAPI gateway

- Browser → Next.js BFF → Spring Boot (private Railway API) → HTTPS Tunnel → Raspberry Pi `/api/v1/ai/parse`
- Pi parses natural language into typed `AdmissionAsk` fields only.
- **Spring Boot itself executes the admission comparison** against PostgreSQL and returns the original trusted data. Pi does not need access to Railway private addresses/DB.
- Spring returns evidence-bearing admission rows, not model-generated cutoffs or invented admission probability.

## Configuration (all secrets environment only)

- `AI_GATEWAY_ENABLED=false` default fail closed; true only after risk/abuse review
- `AI_FRONTEND_TOKEN` 32+ chars shared with Next.js BFF via server environment
- `AI_PARSER_URL=https://<private-tunnel-domain>/api/v1/ai/parse`
- `AI_INTERNAL_TOKEN` 32+ chars matching FastAPI `INTERNAL_SERVICE_TOKEN`
- `AI_CF_CLIENT_ID` / `AI_CF_CLIENT_SECRET` for Cloudflare Access service token when enabled

No automatic deployment, no operator secrets in Git. AI endpoint currently has coarse concurrency cap (3); add per-user rate limiting and cost quotas before public launch. Test end-to-end with synthetic development data first. Do not let external callers bypass the Next.js BFF.
