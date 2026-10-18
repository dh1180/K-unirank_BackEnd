import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "K-unirank 개발 미리보기", template: "%s | K-unirank" },
  description: "K-unirank의 Spring Boot + React 전환 개발 미리보기입니다.",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>
        <header className="site-header">
          <div className="shell header-inner">
            <Link className="brand" href="/">K-unirank</Link>
            <nav aria-label="주요 메뉴">
              <Link href="/">입시</Link>
              <Link href="/admissions/compare/">내 점수</Link>
              <Link href="/universities/">대학</Link>
              <Link href="/ranking/">순위</Link>
            </nav>
          </div>
        </header>
        <main className="shell main-content">{children}</main>
        <footer className="footer shell">
          <p>Spring Boot + Next.js 전환 개발 미리보기 · 기존 Django 운영 서비스와 별개</p>
        </footer>
      </body>
    </html>
  );
}
