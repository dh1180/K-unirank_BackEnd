# K-unirank BackEnd

K-unirank의 **Spring Boot REST API 전용 저장소**입니다. 이전 `K-unirank_java`에서 저장소 이름을 바꾸었으며, 프런트엔드와 AI를 독립 저장소로 분리했습니다.

| 구성 요소 | 저장소 | 기술 |
|---|---|---|
| BackEnd | [K-unirank_BackEnd](https://github.com/dh1180/K-unirank_BackEnd) | Java 21 · Spring Boot 3.5 · PostgreSQL |
| FrontEnd | [K-unirank_FrontEnd](https://github.com/dh1180/K-unirank_FrontEnd) | Next.js · React · TypeScript |
| AI | [K-unirank_AI](https://github.com/dh1180/K-unirank_AI) | FastAPI · 입결 조회 하네스 · RAG 예정 |
| 기존 운영 | [K-unirank_v2](https://github.com/dh1180/K-unirank_v2) | Django · Railway PostgreSQL |

> **전환 검증 단계입니다.** Gradle Wrapper로 빌드합니다. Railway의 기존 `Postgres`를 직접 사용하는 API는 `railway-readonly` 프로필로 연결하며, 스키마 변경 및 쓰기 요청을 차단합니다. 기존 Django 서비스의 도메인과 쓰기 소유권은 유지합니다.

## 현재 구현

- 대학 목록/상세, 대학별 모집단위 조회
- 입결 검색, 학과·대학별 입결 조회, 성적 비교/그래프
- 대학 선호도 랭킹 읽기 API
- 기존 학생부 50%/70% 컷 **둘 다 1.0인 비의치한약수 결과 제외** 정책
- JUnit 5 · PostgreSQL Testcontainers 통합 테스트

회원 인증·관심 등록·VS 투표는 개발판이 구현되어 있습니다. **공개 회원 기능의 보안 보완, 공식 SEO/광고 연결, 실제 Django 데이터와의 전체 결과 비교**는 남아 있습니다.

## Railway 기존 Postgres 직접 연결

새 API 서비스에 `SPRING_PROFILES_ACTIVE=railway-readonly`를 설정하고, `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`를 각각 `${{Postgres.PGHOST}}` 등의 Railway 참조 변수로 연결합니다. 별도 DB 생성이나 데이터 복사는 필요하지 않습니다.

이 프로필은 Hibernate `validate`, SQL 초기화 비활성화, PostgreSQL 세션 읽기 전용 설정을 적용합니다. 대학·입결·랭킹 조회와 healthcheck만 허용하며, 세션을 생성하는 VS `GET /next`와 모든 변경 요청을 컨트롤러 실행 전에 차단합니다. 일반 개발/통합 테스트는 기존 기본 프로필과 격리 DB를 사용합니다.

배포 명령, 내부 주소 및 검증 범위는 [Railway 배포 문서](docs/RAILWAY_DEPLOYMENT.md)를 참조하세요.

## 로컬 실행

JDK 21과 별도 개발용 PostgreSQL 복제본을 준비하세요. Gradle은 별도 설치하지 않고 프로젝트의 Gradle Wrapper(`gradlew` / Windows `gradlew.bat`)를 사용합니다.

```bash
export DB_JDBC_URL='jdbc:postgresql://localhost:5433/kunirank'
export DB_USER='kunirank_readonly'
export DB_PASSWORD='YOUR_LOCAL_PASSWORD'
cd backend
./gradlew bootRun
```

`spring.jpa.hibernate.ddl-auto=validate`로 **기존 DB 스키마를 수정하지 않고 일치 여부만 검증**합니다.

```bash
cd backend
./gradlew test bootJar
```

일반 통합 테스트는 독립 PostgreSQL Testcontainers 인스턴스에서 실행됩니다(Docker 필요). Windows PowerShell의 실제 staging 읽기 전용 검증은 `scripts/verify-staging.ps1`에서 `backend/gradlew.bat`을 사용하며 Maven 설치가 필요 없습니다.

## 주요 API

```http
GET /api/v1/universities?q=한국&page=0&size=20
GET /api/v1/universities/{id}
GET /api/v1/admissions/results?year=2026
GET /api/v1/admissions/units/{id}
GET /api/v1/admissions/universities/{id}
GET /api/v1/admissions/compare?grade=2.5
GET /api/v1/boards/overall/ranking
GET /actuator/health
```

## 전환 및 보안 원칙

- FrontEnd는 `API_INTERNAL_URL`로 Spring Boot API에 접근. Railway 내부 사설 주소는 Next.js **서버 측**에서만 사용.
- AI는 별도 Raspberry Pi 5의 FastAPI 서비스. Spring Boot→AI 통합은 별도 인증·타임아웃 PR에서 진행.
- 운영 데이터 수집·쓰기 소유권은 Django와 Spring이 동시에 충돌하지 않도록 보장.
- 개발용 테스트 DB는 운영 DB와 분리. 개인 데이터가 포함된다면 마스킹 후 사용.
- `/support/` 후원 서비스 비공개 정책 유지.

## PR·커밋 컨벤션

[Contextory_BackEnd PR #10](https://github.com/kau-likelion-14th-hackathon/Contextory_BackEnd/pull/10) 형식을 준수합니다. `feat:`/ `fix:` 커밋, `[Feat]`/ `[Fix]` PR 제목, PR 타입 → 반영 브랜치 → 변경 사항 → 테스트 → 참고 사항.

[전환 계획](docs/MIGRATION_PLAN.md) 참고.

## 비공개 Spring 투표 서비스

조회 서비스는 `railway-readonly`, 투표 전용 서비스는 `railway-voting` 프로필로 분리합니다. 투표 서비스에는 공개 도메인을 만들지 않으며 `GET /api/v1/boards/overall/next`, `POST /api/v1/boards/overall/vote`만 허용합니다.

- `VS_DB_USER`, `VS_DB_PASSWORD`: 기존 DB에 만든 투표 전용 역할. 대학·보드·투표·평점 SELECT, 투표 세션/기록/평점 INSERT, 세션 횟수 및 평점 관련 컬럼 UPDATE, ID 시퀀스 USAGE만 부여합니다. 대학 정보 UPDATE, DELETE, DDL은 허용하지 않습니다.
- `VS_FRONTEND_TOKEN`: Next BFF와 공유하는 32자 이상 비밀값입니다.
- `VS_SIGNING_SECRET`: 5분 대진 서명용 32자 이상 비밀값입니다.
- `VS_ALLOWED_ORIGIN`: 허용하는 프런트 도메인의 Origin을 쉼표로 구분합니다.
- `VS_COOKIE_SECURE=true`: HttpOnly, Secure, SameSite=Lax 쿠키를 사용합니다.
- 프런트에는 `VS_API_INTERNAL_URL`, 같은 `VS_FRONTEND_TOKEN`, `VS_FRONTEND_ENABLED=true`를 설정합니다. 브라우저에는 비밀값이나 사설 주소를 전달하지 않습니다.

기존 투표와 평점을 초기화하지 않습니다. 평점 갱신은 행 잠금과 교착 재시도로 기존 Django 투표 처리와 조정합니다. `VotingProfileIntegrationTest`는 실제 스키마 형태와 제한된 DB 역할로 투표 저장 및 금지된 쓰기 거부를 검증합니다.

대학 지역 필터는 `GET /api/v1/universities/regions`의 실제 활성 대학 지역 전체를 사용합니다.


### Legacy runtime retirement

Native registration uses `db/registration.sql` and `db/legacy-runtime-grants.sql`, applied by the existing database owner. The application role receives EXECUTE on a fixed security-definer registration function, not INSERT/UPDATE on auth_user. New accounts are always active non-staff/non-superuser accounts. Password hashing remains Django PBKDF2-SHA256 compatible. Common-password rejection uses Django's bundled public dictionary (BSD-3-Clause; see resources/DJANGO-LICENSE.txt).

Member history reads are scoped by the current authenticated member; association of an anonymous voting session cannot overwrite another member's association. Shared personal results retain existing UUID and JSON payloads, with new results built from at least five selected votes in the caller's opaque VS session. Public Glicko ratings are not modified by personal-result creation.

The former Django support feature is disabled in production and remains unavailable. Generic Django admin data editing is not exposed by the native admin statistics page; operational access to the old admin requires temporarily resuming web. Existing data are not removed when web is scaled to zero.
