import "server-only";
import type { AdmissionPage, GradeComparison, UniversityPage } from "@/lib/types";
import { toQuery } from "@/lib/query.mjs";

/**
 * Next.js server fetches Spring Boot's API over the private network.
 * In an unconfigured preview, return null and show an explicit error.
 * We deliberately do NOT point an unconfigured preview at production Django.
 */
async function apiGet<T>(path: string): Promise<T | null> {
  const base = process.env.API_INTERNAL_URL;
  if (!base) return null;
  try {
    const url = new URL(path, base);
    const response = await fetch(url, {
      cache: "no-store",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(10_000),
    });
    return response.ok ? (await response.json()) as T : null;
  } catch {
    return null;
  }
}

export function listUniversities(q: string, page: number): Promise<UniversityPage | null> {
  return apiGet<UniversityPage>(`/api/v1/universities?${toQuery({ q, page, size: 20 })}`);
}

export function compareGrades(params: Record<string, string | number | undefined>): Promise<GradeComparison | null> {
  return apiGet<GradeComparison>(`/api/v1/admissions/compare?${toQuery(params)}`);
}

export function unitAdmissions(id: number, page: number): Promise<AdmissionPage | null> {
  return apiGet<AdmissionPage>(`/api/v1/admissions/units/${id}?${toQuery({ page, size: 60 })}`);
}
