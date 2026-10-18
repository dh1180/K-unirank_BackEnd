# Next.js 프런트엔드 이식 — 개발 미리보기

- **현재 상태:** 신규 프런트엔드 초기 구조. 운영 k-unirank.com과 Railway 서비스 연결은 하지 않음.
- Next.js App Router, React, TypeScript.
- 대학 목록(`/universities/`), 학과 입결 비교(`/admissions/compare/`), 모집단위 상세(`/admissions/unit/{id}/`)를 Spring REST API에서 서버 측으로 읽음.
- 기존 Django의 모든 UI·SEO·광고·로그인·랭킹이 이전 완료된 것은 아님. **기존 Django를 계속 운영**해야 함.

## 로컬 실행

```bash
cd backend && mvn spring-boot:run
# 별도 터미널
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

- `API_INTERNAL_URL=http://127.0.0.1:8080`: Next.js **서버 측 환경변수**. 브라우저에 Railway 내부 주소/DB 계정이 노출되지 않도록 `NEXT_PUBLIC_` 접두사 사용 금지.
- API 미연결 환경에서는 모의 대학·입결 데이터를 만들지 않고 사용자에게 연결 오류 표시.
- React 서버 렌더링 시 Spring API 서버에 연결하므로 개발 DB는 기존 PostgreSQL의 복제본을 사용.
- `next.config.ts`의 `trailingSlash: true`로 기존 Django 공개 URL 구조에 가까운 경로 유지.

## 배포 전 반드시 해결

1. Spring Boot API에 읽기 전용 운영 DB 접근 정책 및 성능/캐시 적용.
2. 대학 상세·순위·VS·로그인·마이 페이지 및 대학/학과 URL의 실제 동등성 검증.
3. canonical, sitemap, robots, 구조화 데이터, GA4/AdSense, 대학 로고 포함 전체 SEO 이식.
4. **현재 `robots.ts`와 `layout.tsx`는 개발 미리보기 검색 노출 방지를 위해 noindex/disallow로 고정됨**. 프로덕션 cutover 전에 반드시 변경해야 함.
5. `npm install` 이후 보안 점검 및 `package-lock.json` 생성·버전 고정을 별도 PR로 추가. 초기 골격에서는 동작 우선으로 CI가 패키지를 설치함.
6. 실제 Railway 전환 이전에 QA/롤백 계획 승인. 운영 Django 및 PostgreSQL 연결을 이 PR에서 변경하지 않음.
