import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { unitAdmissions } from "@/lib/api";
import { positivePage } from "@/lib/query.mjs";
import { Notice } from "@/components/Notice";
import { Pagination } from "@/components/Pagination";

export const metadata: Metadata = { title: "학과별 입시결과" };

export default async function AdmissionUnitPage({
  params, searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ page?: string }>;
}) {
  const { id } = await params;
  if (!/^\d+$/.test(id) || !Number.isSafeInteger(Number(id)) || Number(id) <= 0) notFound();
  const query = await searchParams;
  const page = positivePage(query.page);
  const data = await unitAdmissions(Number(id), page);
  return (
    <>
      <section className="page-header">
        <h1>모집단위 입시결과</h1>
        <p>기존 모집단위 ID {id} · 원본 출처와 공개 지표를 표시해.</p>
      </section>
      {data === null ? (
        <Notice>입결 API가 응답하지 않거나 모집단위를 찾지 못했어.</Notice>
      ) : (
        <section className="panel">
          <h2>{data.items[0]?.universityName ?? "모집단위"} {data.items[0]?.recruitmentUnitName ?? ""}</h2>
          <p className="muted">공개 입결 {data.totalCount.toLocaleString("ko-KR")}건</p>
          <ul className="item-list">
            {data.items.map(row => (
              <li className="list-item" key={row.id}>
                <div>
                  <strong>{row.admissionYear}학년도 {row.selectionCategory} · {row.selectionName}</strong>
                  <p className="muted">{row.sourceType} · {row.phase}</p>
                  {row.metrics.length > 0 && (
                    <dl className="metric-grid">
                      {row.metrics.map(metric => (
                        <div key={metric.code}>
                          <dt>{metric.code}</dt>
                          <dd>{metric.value} {metric.unit}</dd>
                        </div>
                      ))}
                    </dl>
                  )}
                </div>
              </li>
            ))}
          </ul>
          <Pagination href={`/admissions/unit/${id}/`} page={page} hasNext={data.hasNext} params={{}} />
        </section>
      )}
    </>
  );
}
