# K-unirank Java

기존 [K-unirank Django 서비스](https://github.com/dh1180/K-unirank_v2)를 **Spring Boot + Next.js(React·TypeScript)** 로 점진적으로 전환하기 위한 신규 저장소입니다.

> **상태:** 이전 준비 단계. 기존 Django 사이트와 운영 PostgreSQL은 그대로 운영하며, 이 저장소의 변경사항이 자동으로 운영 DB에 적용되지 않습니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA |
| 프런트엔드(예정) | Next.js, React, TypeScript |
| DB | 기존 PostgreSQL — 운영 DB가 아닌 **복제본으로 먼저 검증** |
| 테스트 | JUnit 5, Testcontainers + PostgreSQL |
| 배포(예정) | Railway: 프런트 + API + 기존 PostgreSQL |

## 디렉터리

- `backend/`: Spring Boot REST API. 첫 단계는 대학 조회 기능만 구현
- `.github/workflows/`: Java 빌드·PostgreSQL 회귀 테스트
- `docs/`: 전환 절차, 호환성 및 배포 계획
- `frontend/`: Next.js 단계에서 추가 예정

## 로컬 실행 준비

Java 21, Maven 3.9+, PostgreSQL 17 (또는 기존 운영 DB와 호환되는 버전)이 필요합니다.

1. 기존 PostgreSQL의 **별도 백업 복제본**을 로컬에 준비합니다. 운영 DB 연결정보는 사용하지 않습니다.
2. `backend/.env.example` 값을 참고해 개발 환경 변수를 설정합니다.

```bash
export DB_JDBC_URL='jdbc:postgresql://localhost:5433/kunirank'
export DB_USER='kunirank_readonly'
export DB_PASSWORD='YOUR_LOCAL_PASSWORD'

cd backend
mvn spring-boot:run
```

> `application.yml`의 `spring.jpa.hibernate.ddl-auto: validate`는 기존 스키마를 검증만 하며 생성·업데이트하지 않습니다. 기존 DB 구조를 유지하고, 개발용 DB에는 가능하면 읽기 전용 계정으로 연결합니다. 현재 코드는 마이그레이션을 실행하지 않습니다.

### API

```http
GET /api/v1/universities?page=0&size=20&q=서울&region=서울
GET /actuator/health
```

- 대학 목록은 `universities`의 `is_active=true` 데이터만 검색하며 대학명 가나다 정렬과 페이지네이션을 제공합니다.
- `q`: 대학명 부분 일치 검색 (선택)
- `region`: 지역 정확 일치 검색 (선택)
- `page`: 0부터 시작, `size`: 1~100

예시 응답 구조 (`totalCount`와 항목은 DB 상태에 따라 다름):

```json
{
  "items": [{"id": 1, "name": "예시대학교", "shortName": null, "region": "서울", "logoPath": null}],
  "page": 0,
  "size": 20,
  "totalCount": 1,
  "totalPages": 1,
  "hasNext": false
}
```

## 테스트

```bash
cd backend
mvn verify
```

통합 테스트는 Testcontainers로 **독립적인 임시 PostgreSQL**을 띄워 실행합니다. Docker 실행 환경이 필요하며, 운영 DB 자격증명은 사용하지 않습니다.

## Git / PR 컨벤션

참조: [Contextory_BackEnd PR #10](https://github.com/kau-likelion-14th-hackathon/Contextory_BackEnd/pull/10)

- 브랜치: `feature/{topic}`, `fix/{topic}`, `refactor/{topic}`, `chore/{topic}`
- PR 제목: `[Feat] 대학 목록 조회 API 구현`, `[Fix] ...`, `[Refactor] ...`
- 커밋: `feat: 대학 목록 조회 API 구현` 형식. 커밋 본문에는 `- 변경 내용`을 항목별로 명시
- PR 본문: **PR 타입 → 반영 브랜치 → 변경 사항 → 주요 API → 테스트 결과 → 참고 사항** 구조
- 기본 대상 브랜치: `main`
- 운영 DB 직접 변경, 전면 API 전환, 배포는 각각 별도 PR 및 검증 후 진행합니다.

자세한 단계는 [전환 계획](docs/MIGRATION_PLAN.md)을 참고하세요.
