import type { Metadata } from "next";
import { ranking } from "@/lib/api";
import { Notice } from "@/components/Notice";

export const metadata: Metadata = { title: "대학 선호도 순위" };

export default async function RankingPage() {
  const data = await ranking("overall", 100);

  return (
    <>
      <section className="page-header">
        <h1>대학 선호도 순위</h1>
        <p>VS 투표 결과에서 산출된 Glicko-2 점수를 그대로 조회해.</p>
      </section>
      {data === null ? (
        <Notice>랭킹 API에 연결할 수 없어. Spring Boot 서버와 API_INTERNAL_URL을 확인해 줘.</Notice>
      ) : (
        <section className="panel">
          <h2>{data.board.name}</h2>
          <p className="muted">이 화면은 대학별 기존 점수를 표시하며 투표 기록을 변경하지 않아.</p>
          {data.ranking.length === 0 ? <Notice>아직 표시할 선호도 순위가 없어.</Notice> : (
            <ol className="ranking-list">
              {data.ranking.map(item => (
                <li key={item.university_id} className="list-item ranking-item">
                  <strong className="rank-number">{item.rank}위</strong>
                  <div>
                    <strong>{item.name}</strong>
                    <p className="muted">{item.match_count.toLocaleString("ko-KR")}전 · {item.win_count.toLocaleString("ko-KR")}승 · {item.loss_count.toLocaleString("ko-KR")}패</p>
                  </div>
                  <span className="rank-rating">{item.rating.toFixed(2)}</span>
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
    </>
  );
}
