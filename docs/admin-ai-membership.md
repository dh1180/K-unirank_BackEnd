# AI 회원 제한과 운영 조회 확장

AI BFF는 매 요청마다 회원 API의 현재 세션을 검증한 뒤 AI를 호출한다. 익명·만료·로그아웃된 세션은 401, 인증 서버 장애는 503으로 종료한다. 기존 Origin 검사와 AI 호출 제한은 유지한다.

## 명시적 DB 적용

자동 스키마 초기화는 사용하지 않는다. PostgreSQL 관리 계정으로 다음 파일을 순서대로 하나의 트랜잭션에서 실행한다.

1. `backend/src/main/resources/db/ai-usage.sql`
2. `backend/src/main/resources/db/admin-runtime-grants.sql`

테이블·인덱스 생성은 재실행 가능하다. 회원 런타임 역할에는 가입일 SELECT와 AI 이용 기록 SELECT/INSERT만 추가한다. 회원 UPDATE 권한은 부여하지 않는다. 기존 자료의 삭제·수정은 없다. 이전 바이너리로 롤백해도 새 테이블은 보관한다.

## 관리자 API

모든 관리자 조회는 SQL 실행 전에 현재 세션의 관리자 권한을 확인한다.

- `GET /api/v1/admin/overview`: 기존 통계, KST 최근 7일 투표/가입/AI 완료 요청, 활성 세션, 오늘 AI 사용 통계와 최근 10건.
- `GET /api/v1/admin/members?q=&state=all&page=0`: 아이디 검색과 계정 상태 필터, 20건 단위. 비밀번호·이메일은 반환하지 않는다.
- `GET /api/v1/admin/quality?kind=sentinel&page=0`: 99.99 계열, 신경주대 백분위 99, 등급/백분위 범위 이상 지표 원본을 12건 단위로 조회한다. 집계 기준이나 원본을 수정하지 않는다.
- `POST /api/v1/member/ai/usage`: 신뢰된 프런트 BFF 전용. 로그인 회원의 요청 UUID, HTTP 상태, 소요 시간만 기록한다. UUID 중복은 무시한다. 브라우저용 프록시에는 노출하지 않는다.

AI 통계는 이 기능 적용 이후 실제 upstream 호출이 완료된 요청부터 기록한다. 입력 검증·회원 인증·BFF 제한에서 종료된 요청은 제외한다. 질문 원문, IP, 토큰은 저장하지 않는다. 로그아웃 직후 등 기록 실패 시 완료된 AI 응답은 유지되며 통계가 일부 누락될 수 있다.

## 검증

회원/관리자 권한, 만료·로그아웃, 중복 기록, SQL 검색어 escaping, 잘못된 필터, 집계 SQL을 PostgreSQL 통합 테스트로 검증한다. 운영자 계정의 비밀번호나 권한을 변경해 검증하지 않는다.
