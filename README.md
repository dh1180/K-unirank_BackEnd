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
