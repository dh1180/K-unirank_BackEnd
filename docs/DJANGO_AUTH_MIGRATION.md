# 기존 Django 회원·로그인 이전 계획 (아직 공개 로그인 미구현)

현재 [K-unirank_v2](https://github.com/dh1180/K-unirank_v2)는 Django 기본 `auth.User`와 `auth_user` 테이블을 사용합니다. `config/settings.py`는 기본 Django Password Hasher 설정을 사용하며 커스텀 `AUTH_USER_MODEL`은 없습니다.

## 이 단계에서 구현한 부분

- `DjangoPasswordVerifier`가 Django의 표준 `pbkdf2_sha256$iterations$salt$base64` 및 과거 `pbkdf2_sha1` 형식 비밀번호를 검증합니다.
- UTF-8 한국어 비밀번호의 Python `hashlib.pbkdf2_hmac` 비교 테스트.
- 미지원 `argon2`, `bcrypt`, `scrypt`, 사용 불가 비밀번호, 손상된 해시는 실패 처리.
- **실제 로그인 API/비밀번호 재해싱·새 세션 발급·회원 가입/수정 기능은 아직 없습니다.** 어떠한 운영 계정도 변경하지 않습니다.

## 실제 로그인 API 구현 전에 확인할 사항

1. 운영 PostgreSQL의 백업을 **별도 테스트 DB**로 복원하고 `auth_user`, `favorite_universities`, `favorite_recruitment_units`의 PK 타입, 인덱스, FK 및 password 해시 접두사의 **집계만** 확인합니다. 비밀번호 해시 원문은 로그·PR·문서에 저장하지 않습니다.
2. 로그인은 계정 존재 여부를 노출하지 않도록 동일한 오류 메시지 사용, 로그인/IP 단위 제한과 CSRF 보호를 먼저 적용합니다.
3. Django의 `django_session` 서명 방식은 Spring 세션과 자동 호환되지 않습니다. 별도 HttpOnly Secure 쿠키와 서버 세션 또는 철회 가능한 토큰을 발급하고 개인정보 보호·로그아웃·세션 재발급 정책을 수립합니다.
4. `auth_user.is_active=false` 계정과 미확인 계정을 거부하고, 회원 ID를 유지해 기존 관심 대학/VS 투표 기록과 연관 관계를 보존합니다.
5. 해시 업그레이드는 검증 성공 후 안전한 방식으로 진행하고, 지원하지 않는 기존 알고리즘은 계정 재설정 절차를 설계합니다. **자동 비밀번호 재설정이나 DB 대량 갱신 금지**.
6. 개발/운영 Django와 Spring이 같은 `auth_user` 및 즐겨찾기 테이블을 동시에 갱신하지 않게 쓰기 소유권을 관리합니다.

## 계정별 변경 없는 호환성 검증

독립 테스트 DB를 사용하는 향후 integration test에서 Django의 `make_password`와 Java verifier에 동일한 테스트 전용 비밀번호를 넣어 결과를 대조합니다. 현재의 단위 테스트는 DB를 필요로 하지 않습니다.
