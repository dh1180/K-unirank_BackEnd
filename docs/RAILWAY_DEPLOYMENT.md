# K-unirank Spring Boot Railway 배포 준비

## 기존 Postgres 직접 연결 (2026-10-29)

기존 `focused-enjoyment` 프로젝트의 `production` 환경에서 Django `web`과 별도인 Spring `api` 서비스를 사용합니다. DB는 현재 `Postgres`를 그대로 참조합니다. 초기 연결은 `railway-readonly` 프로필로 검증합니다.

| api 서비스 변수 | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `railway-readonly` |
| `PGHOST` | `${{Postgres.PGHOST}}` |
| `PGPORT` | `${{Postgres.PGPORT}}` |
| `PGDATABASE` | `${{Postgres.PGDATABASE}}` |
| `PGUSER` | `${{Postgres.PGUSER}}` |
| `PGPASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `PORT` | `8080` |
| `MEMBER_AUTH_ENABLED` | `false` |
| `AI_GATEWAY_ENABLED` | `false` |

Railway 참조 변수로 비밀값을 복사하지 않고 기존 서비스 값을 주입합니다. 프로필이 `jdbc:postgresql://PGHOST:PGPORT/PGDATABASE` URL을 구성하므로 이 프로필에서는 `DB_JDBC_URL`이 필요하지 않습니다. 사설 네트워크 접속은 Railway 컨테이너 내부에서 이루어집니다.

### 빌드 및 검증

- JDK 21, `backend/gradlew`의 Gradle 8.14.3 사용. Maven 설치 불필요.
- 로컬 검증: `backend/gradlew.bat -p backend --no-daemon test bootJar` (Docker 실행 필요).
- Railway는 저장소 루트 `Dockerfile` 및 `railway.toml`을 사용하고 `/actuator/health`로 배포 상태를 판정합니다.
- 기존 스키마와 Entity가 불일치하면 배포는 실패하며, DB 자동 수정 대신 Entity/SQL을 수정합니다.
- `railway ssh --service api --environment production` 등 내부 접근으로 health, 대학·입결·성적 비교·랭킹 GET 응답을 검증합니다. 서비스는 공개 도메인 없이 내부 API로 운영할 수 있습니다.

### 연결 검증 중 허용 범위

`railway-readonly`는 Hikari read-only 및 PostgreSQL 세션 기본 읽기 전용 모드, 15초 statement timeout, 최대 2개 연결을 적용합니다. Hibernate DDL은 `validate`, SQL 초기화는 `never`입니다. 대학·입결·랭킹 GET/HEAD 및 healthcheck만 허용합니다. 쓰기를 유발하는 VS `GET /next`를 포함한 나머지 요청은 HTTP 503 `READ_ONLY_DEPLOYMENT`로 차단됩니다.

이 모드는 기존 Postgres 관리자 자격 증명을 참조하므로 **DB 권한 자체를 제한한 전용 읽기 계정과 같지는 않습니다**. 프로필을 해제하거나 JDBC 연결 옵션을 바꾸면 보호 범위가 달라집니다. 공개 쓰기 전환은 회원 보안, VS 결과 동등성, Django와의 단일 쓰기 소유권 및 롤백 검증 후 별도로 수행합니다. 기존 `web`과 도메인은 그대로 유지합니다.

아래는 별도 복제 DB를 사용하던 초기 전환 가이드입니다. 현재 직접 연결 방식은 위 설정을 우선합니다.

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
