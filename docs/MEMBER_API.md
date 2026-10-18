# Django 계정 기반 Spring 회원 기능 — 테스트 환경 전용 (기본 비활성)

신규 백엔드는 기존 Django `auth_user`와 `favorite_universities` 테이블을 재사용하고, 새 인증 테이블이나 스키마 마이그레이션을 요구하지 않습니다.

## API

- POST `/api/v1/member/login` `{"username":"...", "password":"..."}`
- GET `/api/v1/member/me`: 쿠키 기반 사용자 기본 프로필 반환
- POST `/api/v1/member/logout`: 프런트 브라우저의 쿠키 삭제
- GET `/api/v1/member/favorites/units`: 기존 관심 학과/모집단위 목록
- PUT/DELETE `/api/v1/member/favorites/units/{id}`: 관심 모집단위 등록/해제 (idempotent)
- GET `/api/v1/member/favorites/universities`: 기존 관심 대학 목록
- PUT/DELETE `/api/v1/member/favorites/universities/{id}`: 관심 대학 등록/해제 (idempotent)

## 보안·마이그레이션 제한 사항

- `MEMBER_AUTH_ENABLED=false` 기본, `MEMBER_AUTH_SECRET`이 32자 미만이면 503.
- 한 시간 만료 HMAC 서명 세션 쿠키, HttpOnly / SameSite=Strict / Secure 기본. 기존 Django `django_session`은 자동 승계되지 않음.
- 기존 `auth_user.password` 변경 시 기존 Spring 세션 서명이 통과해도 인증을 거부.
- **서버 측 세션 저장소가 없으므로**, 로그아웃은 브라우저 쿠키만 없애며 탈취된 토큰의 서버 측 강제 철회는 최대 1시간 동안 불가능. 정식 운영 전에 서버 측 세션 저장과 철회 구현이 필요.
- 로그인 시도 제한은 단일 JVM 메모리 한정이며 **다중 인스턴스와 서버 재시작을 고려한 공유 저장소·엣지 속도 제한이 별도로 필요**.
- 개발 API가 외부에 노출되지 않게 Railway 비공개 네트워크만 사용. Next.js BFF에서 Origin/CSRF 검증을 추가한 뒤에만 활성화.
- 실사용 데이터 스키마와 사용자 해시 알고리즘 비율은 아직 검증하지 않았으며, 테스트는 독립 DB의 합성 사용자 데이터 기준.
- 운영 배포 시 기존 Django와 Spring이 `favorite_universities`를 동시에 쓰지 않도록 cutover 이전에 한쪽 쓰기를 반드시 중단.
- 추가 회원 정보, 회원가입, 비밀번호 재설정, VS 사용자 세션 결합은 후속 단계.
