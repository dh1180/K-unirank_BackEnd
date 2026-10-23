# K-unirank Spring Boot Railway 배포 준비

**현재는 배포 가이드만 준비했습니다.** 실제 운영 DB 연결·Railway 서비스 생성·도메인 전환은 수행하지 않았습니다.

## 목표 구성

- [FrontEnd](https://github.com/dh1180/K-unirank_FrontEnd): Railway Next.js `web` 서비스
- [BackEnd](https://github.com/dh1180/K-unirank_BackEnd): Railway Spring Boot `api` 서비스 (이 저장소)
- PostgreSQL: **현재 Railway 운영 DB 보존**
- [AI](https://github.com/dh1180/K-unirank_AI): 집 Raspberry Pi 5 FastAPI, 외부 LLM 및 RAG는 이후

## 사전 준비

1. 운영 PostgreSQL을 복원 가능한 방식으로 백업하고, **별도 테스트 DB**를 생성합니다.
2. 기존 Django 데이터와 Spring API의 검색 결과·입결 수치·PK/FK를 비교 검증합니다.
3. Railway에서 신규 Spring Boot 서비스 생성 시 GitHub 저장소 `K-unirank_BackEnd` 연결, **루트의 Dockerfile** 지정.
4. 앱 DB 변수는 **테스트용 PostgreSQL 연결 정보만** 등록합니다. 비밀번호를 GitHub 커밋에 기록하지 않습니다.
   - `DB_JDBC_URL=jdbc:postgresql://<test-db-host>:5432/<dbname>`
   - `DB_USER=<readonly-db-user>`
   - `DB_PASSWORD=<secret>`
   - `PORT=8080` (Railway가 자동 부여한 PORT 값을 그대로 사용할 수도 있음)
5. Railway 인스턴스에 `/actuator/health` healthcheck를 설정하고 응답 및 로그를 확인합니다.
6. Next.js 서버 측 환경변수 `API_INTERNAL_URL`을 신규 API 사설 도메인에 맞게 설정합니다.

## 안전성

- Spring 설정은 `spring.jpa.hibernate.ddl-auto=validate`: DB 자동 생성·변경하지 않습니다.
- `DB_JDBC_URL`은 `postgres://...` 형식이 아니라 **`jdbc:postgresql://...` 형식**이 필요합니다.
- 운영 전환 전에 읽기 전용 DB 계정으로 성능과 정합성을 검증하세요. 기존 운영 Django에 연결하거나 쓰기 API를 노출하지 마세요.
- Spring Boot가 Railway 내부 주소만 노출하는 설정이라도 인증·네트워크 정책은 별도로 검증해야 합니다.
- FastAPI Raspberry Pi는 Railway 사설 호스트에 직접 연결할 수 없습니다. AI 연동 시 인증된 HTTPS 터널 및 별도 토큰이 필요합니다.
- 초기 Next.js 프런트는 `robots=noindex`. 전체 기능·canonical·sitemap·광고·로그인/VS 비교 후에만 운영 도메인을 옮기고 색인을 허용합니다.
- 백업 검증, 경로별 스모크 테스트, 장애 시 Django 롤백 절차 없이 본 배포로 전환하지 않습니다.

## 로컬 Docker 빌드·실행

```bash
docker build -t kunirank-backend:dev .
docker run --rm -p 8080:8080 \
  -e DB_JDBC_URL='jdbc:postgresql://host.docker.internal:5433/kunirank' \
  -e DB_USER='kunirank_readonly' \
  -e DB_PASSWORD='LOCAL_DEV_PASSWORD' \
  kunirank-backend:dev
```

`host.docker.internal`은 Docker 환경별로 사용할 수 있는 주소가 다르므로 Linux에서는 호스트 네트워크 설정을 확인하세요. 운영 DB 자격 증명은 사용하지 마세요.

CI는 Gradle Wrapper 기반 통합 테스트에 더해 Docker 이미지도 빌드해 Dockerfile 정확성을 확인합니다.
