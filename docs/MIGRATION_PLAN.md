# K-unirank Django → Spring Boot + React 단계별 전환

## 기본 원칙

- 기존 [K-unirank_v2](https://github.com/dh1180/K-unirank_v2)의 Django 운영 서비스는 전환 완료 전까지 유지
- 기존 Railway PostgreSQL의 데이터·테이블·PK/FK·UUID·로그 및 회원 관계 보존
- 개발 중 **운영 DB에 연결하지 않음**. 별도의 백업 복제 DB를 사용하고, 필요시 개인정보 마스킹
- Spring Data JPA의 운영 설정은 `ddl-auto=validate`; 자동 DDL 변경 금지
- 생산 중인 DB를 Django와 Spring이 동시에 쓰지 않도록 쓰기 주체 단일화
- `/support/`는 기존 정책대로 비공개 유지

## 현재 Django 호환성 주의사항

1. **대학:** `universities`, `university_campuses` 등의 테이블, 대학·캠퍼스 고유 ID 유지.
2. **입결:** `admission_results`, `admission_metrics`, `admission_aggregates` 조회 결과 일치. 동일 `AdmissionResult`의 학생부 50% 및 70% 컷이 **둘 다 1.0인 비의치한약수 결과만** 제외하는 정책 필수 이식. 한쪽만 1.0이면 유지.
3. **성적 비교:** 70% 컷 우선 / 없으면 50% 컷 사용; 특별전형 기본 제외; 차트 최소 표본 2건; 대학 입학처 출처 우선 정책 유지.
4. **회원:** Django `auth_user` 비밀번호 해시와 Spring Security 검증 호환 전략 필요. 사용자 ID 및 기존 관심 대학 유지.
5. **VS/랭킹:** `comparison_votes` 원본, `university_ratings` 상태, Glicko-2 계산 보존. 같은 세션 동일 대진 영향력 100%/35%/0%, 점수 cap, DB 트랜잭션·동시성 정책 이식.
6. **SEO:** 기존 대학/학과 URL, canonical, robots, sitemap, 메타데이터, 구조화 데이터, AdSense/GA4 유지.

## 구현 순서

1. **PR 1 — Spring Boot 초기 기반 + 대학 조회:** 복제 DB 연결, Entity 매핑, 읽기 전용 대학 검색/페이지 API, Testcontainers + CI.
2. **PR 2 — 입시 도메인 DB 매핑:** 학과·입결·지표 조회 및 정합성 비교, 이상치/출처 선택 로직 재현.
3. **PR 3 — 입결 비교·랭킹 API:** 검색 조건, 통계 및 차트 결과 일치 검증.
4. **PR 4 — Next.js 기반/UI:** 검색·상세·차트·순위 페이지, SEO와 모바일 QA.
5. **PR 5 — 회원/VS:** 해시·세션 호환, 투표 쓰기·Glicko-2 동시성 회귀 테스트.
6. **PR 6 — Railway 병행 검증 및 전환:** 최종 DB 권한 및 쓰기 주체 관리, 도메인 이전, 롤백.

## Railway 목표 구조

- `web`: Next.js/React 프런트엔드
- `api`: Spring Boot
- `postgres`: 기존 Railway PostgreSQL

최종 전환 전에는 별도의 테스트 DB 및 테스트 서비스에서만 검증합니다.
