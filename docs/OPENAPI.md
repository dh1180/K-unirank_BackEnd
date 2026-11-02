# Swagger UI / OpenAPI

- 운영: https://k-unirank.com/swagger-ui/index.html
- 로컬 Spring: http://localhost:8080/swagger-ui/index.html
- JSON: `/v3/api-docs`, `/v3/api-docs/{public|member|voting|ai}`
- 운영 JSON: `/api-docs/spec/{public|member|voting|ai}/`

Java 21 / Spring Boot 3.5.6에 호환되는 springdoc-openapi 2.10.0 사용.
컨트롤러·DTO·Bean Validation으로 실제 경로, 요청·응답 스키마와 기본값을 생성합니다.
비밀번호는 writeOnly/password로 문서화합니다. 관리자·회원·투표·AI는 인증 요구를 표시합니다.

운영 Next.js는 고정 private API의 문서·Swagger 정적 자산을 GET으로 중계합니다.
문서에 내부 호스트나 실제 비밀값을 넣지 않으며 토큰·쿠키를 전달하지 않습니다.
운영 Try it out은 공개 조회 GET 경로만 허용합니다. 회원·대진 생성·투표·AI 요청은
기존 BFF와 인증을 사용하며 문서용 경로에서 실행되지 않습니다.
내부 API를 직접 시험하려면 격리된 로컬 개발 환경의 서비스 인증·Origin·쿠키 정책을 사용합니다.
운영 서비스 토큰을 브라우저에 붙여 넣지 않습니다.

새로운 공개 API 도메인/서비스/DB 권한은 만들지 않았습니다.
각 Railway 필터에서 문서 GET/HEAD만 허용하며 API 메서드·인증 경계는 유지합니다.
문서는 no-store 및 noindex/nofollow, 외부 Petstore/validator 요청과 인증 저장을 비활성화합니다.

검증: PostgreSQL 읽기 전용 프로필에서 4개 그룹 스펙 생성·Swagger UI·비밀번호 writeOnly·
쓰기 차단 확인, 프런트 중계의 고정 목적지/조회 allowlist/인증 헤더 미전달 회귀 테스트.
