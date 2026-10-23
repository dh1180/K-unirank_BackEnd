# K-unirank BackEnd

K-unirank의 **Spring Boot REST API 전용 저장소**입니다. 이전 `K-unirank_java`에서 저장소 이름을 바꾸었으며, 프런트엔드와 AI를 독립 저장소로 분리했습니다.

| 구성 요소 | 저장소 | 기술 |
|---|---|---|
| BackEnd | [K-unirank_BackEnd](https://github.com/dh1180/K-unirank_BackEnd) | Java 21 · Spring Boot 3.5 · PostgreSQL |
| FrontEnd | [K-unirank_FrontEnd](https://github.com/dh1180/K-unirank_FrontEnd) | Next.js · React · TypeScript |
| AI | [K-unirank_AI](https://github.com/dh1180/K-unirank_AI) | FastAPI · 입결 조회 하네스 · RAG 예정 |
| 기존 운영 | [K-unirank_v2](https://github.com/dh1180/K-unirank_v2) | Django · Railway PostgreSQL |

> **아직 개발/이전 단계입니다.** 기존 운영 Django와 PostgreSQL은 변경하지 않았으며, 이 저장소는 새로운 Railway API 서비스로 별도 배포하기 전까지 개발용 DB 복제본만 사용합니다.

## 현재 구현

- 대학 목록/상세, 대학별 모집단위 조회
- 입결 검색, 학과·대학별 입결 조회, 성적 비교/그래프
- 대학 선호도 랭킹 읽기 API
- 기존 학생부 50%/70% 컷 **둘 다 1.0인 비의치한약수 결과 제외** 정책
- JUnit 5 · PostgreSQL Testcontainers 통합 테스트

아직 **회원 인증, VS 투표 쓰기, 공식 SEO/광고 연결, 실제 Django 데이터와의 전체 결과 비교**는 미완료입니다.

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
