import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { universityAdmissions } from "@/lib/api";
import { positivePage } from "@/lib/query.mjs";
import { Notice } from "@/components/Notice";
import { Pagination } from "@/components/Pagination";

export const metadata: Metadata = { title: "대학별 입시결과" };

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ page?: string; year?: string }>;
};

export default async function UniversityAdmissionsPage({ params, searchParams }: Props) {
  const { id } = await params;
  if (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) notFound();
  const query = await searchParams;
  const page = positivePage(query.page);
  const year = query.year && /^\d{4}$/.test(query.year) ? Number(query.year) : undefined;
  const data = await universityAdmissions(Number(id), page, year);

  return (
    <>
      <section className="page-header">
        <p><Link href={`/universities/${id}/`}>대학 정보</Link></p>
        <h1>대학별 입시결과</h1>
        <p>공식 입결 지표가 공개된 모집단위 결과를 기준으로 표시해.</p>
      </section>
      {!data ? (
        <Notice>입결 API가 응답하지 않거나 존재하지 않는 대학이야.</Notice>
      ) : (
        <section className="panel">
          <h2>{data.items[0]?.universityName ?? "입결 결과"}</h2>
          <p className="muted">입시결과 {data.totalCount.toLocaleString("ko-KR")}건</p>
          {data.items.length === 0 ? (
            <Notice>해당 조건의 공개 입결이 없어.</Notice>
          ) : (
            <ul className="item-list">
              {data.items.map(item => (
                <li className="list-item" key={item.id}>
                  <div>
                    <strong><Link href={`/admissions/unit/${item.recruitmentUnitId}/`}>{item.recruitmentUnitName}</Link></strong>
                    <p className="muted">{item.admissionYear}학년도 · {item.selectionCategory} · {item.selectionName} · {item.phase}</p>
                    <dl className="metric-grid">
                      {item.metrics.map(metric => (
                        <div key={metric.code}>
                          <dt>{metric.code}</dt>
                          <dd>{metric.value} {metric.unit}</dd>
                        </div>
                      ))}
                    </dl>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <Pagination href={`/admissions/university/${id}/`} page={page} hasNext={data.hasNext} params={{ year: year?.toString() ?? "" }} />
        </section>
      )}
    </>
  );
}
