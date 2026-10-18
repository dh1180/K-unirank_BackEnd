# K-unirank Django → Spring Boot · React · FastAPI 전환

## 저장소 분리

- [BackEnd (Spring Boot)](https://github.com/dh1180/K-unirank_BackEnd) — 이 저장소의 `backend/` 디렉터리
- [FrontEnd (Next.js)](https://github.com/dh1180/K-unirank_FrontEnd) — 원래 `frontend/` 소스를 **별도 저장소 루트로 이동**, 백엔드에서 중복 제거
- [AI (FastAPI)](https://github.com/dh1180/K-unirank_AI) — Raspberry Pi 5에서 실행하는 읽기 전용 입결 도구·향후 RAG/LLM
- [원본 Django 운영 서비스](https://github.com/dh1180/K-unirank_v2) — 서비스 전환 전까지 그대로 운영

## 공통 원칙

1. 기존 Railway PostgreSQL과 Django 배포에 직접 변경을 적용하지 않는다.
2. 기존 대학·학과 ID, 입결·투표 데이터, 회원 관계를 그대로 재사용하며 **복제 DB에서 우선 검증**한다.
3. 운영 DB 스키마는 Django와 Flyway가 동시에 변경하지 않도록 전환 시점까지 관리 주체를 하나로 유지한다.
4. 학생부 **50%와 70% 컷이 모두 1.0**인 **비의치한약수** 입결만 제외한다. 한쪽이라도 1.0보다 큰 값은 보존한다.
5. 성적 비교에서 70% 우선, 특별전형 기본 제외, 표본 수 필터, 출처 우선 정책을 유지한다.
6. 기존 URL, sitemap, robots, canonical, AdSense/GA4, VS/Glicko-2를 단계별로 검증한 후 도메인을 전환한다.
7. 후원 공개 기능은 계속 비공개로 유지한다.

## 남은 개발

- Spring Boot: 인증/회원, 즐겨찾기, VS 매칭·Glicko-2 투표 쓰기 및 중복/동시성 테스트
- FrontEnd: 기존 Django UI 동등성, VS 화면/로그인, SEO·광고, 이미지 자산
- AI: Spring→FastAPI 인증된 HTTP 호출, LLM·모집요강 RAG, 평가 하네스, Pi 운영
- 통합: 별도 개발 DB에 실제 Django 백업 복원 후 API 검색 결과 수·계산값 비교
- 운영: Railway 신규 서비스 병행 검증, 성능·장애 복구·롤백 계획 작성

## 배포 아키텍처

Railway: Next.js Web + Spring Boot API + 기존 PostgreSQL. Raspberry Pi 5: FastAPI AI 서버. Spring Boot → AI는 인증된 HTTPS 터널을 통해 호출하고, Pi와 PostgreSQL을 직접 연결하지 않는다.

배포 전의 개발 미리보기는 `noindex` 유지. Spring과 Django의 **운영 DB 쓰기 중복 금지**.
