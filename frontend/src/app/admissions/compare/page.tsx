import type { Metadata } from "next";
import { compareGrades } from "@/lib/api";
import { gradeIsValid, positivePage } from "@/lib/query.mjs";
import { Notice } from "@/components/Notice";
import { Pagination } from "@/components/Pagination";

export const metadata: Metadata = { title: "내 점수와 가까운 대학" };

type Search = {
  grade?: string; track?: string; special?: string; scope?: string;
  chart_min?: string; q?: string; region?: string; page?: string;
};

export default async function GradeComparisonPage({ searchParams }: {
  searchParams: Promise<Search>;
}) {
  const raw = await searchParams;
  const grade = typeof raw.grade === "string" ? raw.grade.trim() : "";
  const track = raw.track === "holistic" ? "holistic" : "student";
  const special = raw.special === "all" ? "all" : "exclude";
  const scope = raw.scope === "all" ? "all" : "near";
  const chartMin = ["1", "2", "3", "5"].includes(raw.chart_min ?? "") ? raw.chart_min! : "2";
  const q = typeof raw.q === "string" ? raw.q.slice(0, 120) : "";
  const region = typeof raw.region === "string" ? raw.region.slice(0, 30) : "";
  const page = positivePage(raw.page);
  const attempted = grade.length > 0;
  const valid = gradeIsValid(grade);
  const data = attempted && valid ? await compareGrades({
    grade, track, special, scope, chart_min: chartMin, q, region, page, size: 60,
  }) : null;

  return (
    <>
      <section className="page-header">
        <h1>내 점수와 가까운 대학</h1>
        <p>합격 확률이 아니라 공식 50%·70% 컷과 내 등급의 거리를 비교하는 기능이야.</p>
      </section>
      <form method="get" action="/admissions/compare/" className="filter-panel">
        <div className="form-field">
          <label htmlFor="grade">내신 평균등급</label>
          <input id="grade" name="grade" type="number" min="1" max="9" step="0.01"
                 defaultValue={grade} placeholder="예: 3.25" required />
        </div>
        <div className="form-field">
          <label htmlFor="track">전형 유형</label>
          <select id="track" name="track" defaultValue={track}>
            <option value="student">학생부교과</option>
            <option value="holistic">학생부종합</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="special">전형 범위</label>
          <select id="special" name="special" defaultValue={special}>
            <option value="exclude">일반 지원 중심</option>
            <option value="all">특별전형 포함</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="scope">비교 범위</label>
          <select id="scope" name="scope" defaultValue={scope}>
            <option value="near">내 등급 ±0.5</option>
            <option value="all">전체</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="chart_min">차트 최소 표본 수</label>
          <select id="chart_min" name="chart_min" defaultValue={chartMin}>
            {[1, 2, 3, 5].map(n => <option value={n} key={n}>{n}건 이상</option>)}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="q">학과 검색</label>
          <input id="q" name="q" defaultValue={q} placeholder="컴퓨터, 소프트웨어" />
        </div>
        <button className="button primary" type="submit">비교하기</button>
      </form>

      {!attempted && <Notice>내신 등급을 입력하고 비교하기를 눌러 줘.</Notice>}
      {attempted && !valid && <Notice>내신 등급은 1.0~9.0 사이의 숫자로 입력해 줘.</Notice>}
      {attempted && valid && data === null && (
        <Notice>입결 비교 API에 연결할 수 없어. Spring Boot 서버 상태와 API_INTERNAL_URL을 확인해 줘.</Notice>
      )}
      {data && (
        <>
          <section className="panel results">
            <h2>내 점수와 가까운 대학</h2>
            <p className="muted">차트 조건에 맞는 대학 {data.chartUniversityCount}개 · {data.chartMin}건 이상</p>
            {data.charts.length === 0 ? <p>차트에 표시할 대학이 없어. 최소 표본 수를 낮춰 봐.</p> : (
              <ol className="chart-list">
                {data.charts.map(row => (
                  <li key={row.universityId}>
                    <div className="chart-heading">
                      <strong>{row.universityName}</strong>
                      <span>{row.averageCut.toFixed(2)}등급 · {row.referenceBasis} {row.sampleCount}건</span>
                    </div>
                    <div className="cut-track">
                      <div className="cut-fill" style={{ width: `${Math.max(2, (9 - row.averageCut) / 8 * 100)}%` }} />
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </section>
          <section className="panel results">
            <h2>모집단위별 비교 결과</h2>
            <p className="muted">총 {data.totalCount.toLocaleString("ko-KR")}건 · {data.selectedYear ?? "학년도 없음"}학년도</p>
            {data.items.length === 0 ? <Notice>조건에 맞는 학과가 없어.</Notice> : (
              <ul className="item-list">
                {data.items.map(row => (
                  <li className="list-item" key={row.resultId}>
                    <div>
                      <strong>{row.universityName} — {row.recruitmentUnitName}</strong>
                      <p className="muted">{row.selectionCategory} / {row.selectionName} · {row.sourceName}</p>
                      <p>기준 {row.referenceBasis} {row.referenceCut.toFixed(2)}등급
                        <span className={`position ${row.positionTone}`}> · {row.positionLabel}</span>
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            <Pagination href="/admissions/compare/" page={page} hasNext={data.hasNext}
              params={{ grade, track, special, scope, chart_min: chartMin, q, region }} />
          </section>
        </>
      )}
    </>
  );
}
