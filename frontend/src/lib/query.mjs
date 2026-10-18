/**
 * Keep all user-supplied values in URLSearchParams; never interpolate them
 * into a raw URL/SQL fragment.
 */
export function toQuery(params) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && String(value) !== "") {
      query.set(key, String(value));
    }
  }
  return query.toString();
}

export function positivePage(input) {
  const page = Number(input);
  return Number.isSafeInteger(page) && page >= 0 ? page : 0;
}

export function gradeIsValid(input) {
  if (typeof input !== "string" || input.trim() === "") return false;
  const grade = Number(input);
  return Number.isFinite(grade) && grade >= 1 && grade <= 9;
}
