import Link from "next/link";

export default function HomePage() {
  return (
    <>
      <section className="hero">
        <p className="eyebrow">K-unirank Next.js 개발 미리보기</p>
        <h1>대학 정보를 더 편리하게 비교할 수 있도록 준비 중이야</h1>
        <p>
          기존 서비스의 대학·입결 데이터를 새로운 Spring Boot API와 React 화면으로
          옮기고 있어. 운영 중인 Django 사이트와는 별개인 개발 환경이야.
        </p>
        <div className="actions">
          <Link className="button primary" href="/universities/">대학 찾아보기</Link>
          <Link className="button" href="/admissions/compare/">내 점수로 학과 찾기</Link>
        </div>
      </section>
      <section className="grid-two">
        <article className="panel">
          <h2>대학 정보</h2>
          <p>기존 PostgreSQL의 대학 데이터를 Spring Boot API로 조회해.</p>
          <Link href="/universities/">대학 목록 보기 →</Link>
        </article>
        <article className="panel">
          <h2>내 점수 학과 찾기</h2>
          <p>학생부 70% 컷 우선·50% 컷 대체 원칙과 특별전형 필터를 반영해.</p>
          <Link href="/admissions/compare/">성적 비교하기 →</Link>
        </article>
      </section>
    </>
  );
}
