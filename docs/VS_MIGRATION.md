# Django VS → Spring Boot VS

## 이식한 규칙
- 같은 `ranking_boards`, `vote_sessions`, `comparison_votes`, `university_ratings` 테이블 사용
- `overall` 보드만 투표 가능. 80% close, 15% challenge, 5% explore / 최근 대진 쿨다운
- 동일 세션·대진에 대해서 첫 승부 100%, 다음 35%, 이후 0% 점수 영향 (투표/승패 횟수는 기록)
- Glicko-2 tau=0.5, RD 30~350, 비시드 RD 실효 상한 150, 점수당 변동 22(20판 미만)/16
- 건너뛰기는 `vote_count`만 증가, 레이팅과 경기수는 변하지 않음

## 프런트/API
- `GET /api/v1/boards/overall/next`: `kunirank_vs` httpOnly 쿠키, 대진 배열과 5분 HMAC `challenge`
- `POST /api/v1/boards/overall/vote`: JSON `university_a`, `university_b`, `selected_university` / `skipped`, `challenge`
- 서명된 대진은 세션 voteCount에 묶이며 성공하면 재사용 불가. `VS_SIGNING_SECRET` 32글자 이상 없으면 503.
- `VS_COOKIE_SECURE=true` 기본. 로컬 HTTP 개발에서는 `VS_COOKIE_SECURE=false`.
- 브라우저가 Origin을 보내는 경우 `VS_ALLOWED_ORIGIN`과 정확히 일치해야 POST 허용.
- Next.js에서 같은 출처 Route Handler 프록시 구현 시 쿠키/서명 토큰을 안전하게 전달할 것.

## 배포 주의
- 현재 실제 Railway DB 검증·쓰기 기능 배포는 수행하지 않음. 테스트는 임시 PostgreSQL만 사용.
- 기존 Django 투표와 Spring 투표가 같은 운영 DB에 동시에 쓰지 않도록 **cutover 동안 한쪽만 투표 쓰기** 허용.
- 운영 적용 전 동일 투표 시퀀스에서 Django/Python 대비 Glicko 결과의 수치 차이, 동시성, 재전송, CSRF 확인.
- 사용자 데이터의 로그인 세션은 Django와 Spring 간에 자동 호환되지 않음.
