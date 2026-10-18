import Link from "next/link";
import { toQuery } from "@/lib/query.mjs";

type Props = {
  href: string;
  page: number;
  hasNext: boolean;
  params: Record<string, string | number | undefined>;
};

export function Pagination({ href, page, hasNext, params }: Props) {
  const prev = page > 0 ? `${href}?${toQuery({ ...params, page: page - 1 })}` : null;
  const next = hasNext ? `${href}?${toQuery({ ...params, page: page + 1 })}` : null;
  return (
    <nav aria-label="페이지 이동" className="pagination">
      {prev ? <Link href={prev}>이전</Link> : <span aria-disabled="true">이전</span>}
      <span>{page + 1} 페이지</span>
      {next ? <Link href={next}>다음</Link> : <span aria-disabled="true">다음</span>}
    </nav>
  );
}
