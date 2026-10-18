import test from "node:test";
import assert from "node:assert/strict";
import { toQuery, positivePage, gradeIsValid } from "../src/lib/query.mjs";

test("URL query is encoded, not string concatenated", () => {
  assert.equal(toQuery({ q: "컴퓨터&소프트웨어", page: 0 }),
    "q=%EC%BB%B4%ED%93%A8%ED%84%B0%26%EC%86%8C%ED%94%84%ED%8A%B8%EC%9B%A8%EC%96%B4&page=0");
  assert.equal(toQuery({ q: "", page: null, grade: 3.2 }), "grade=3.2");
});

test("page and grade validation reject invalid inputs", () => {
  for (const invalid of ["abc", "-1", "1.2", "Infinity", "NaN"]) {
    assert.equal(positivePage(invalid), 0);
  }
  assert.equal(positivePage("3"), 3);
  for (const invalid of [undefined, "", "NaN", "0.9", "9.1"]) {
    assert.equal(gradeIsValid(invalid), false);
  }
  assert.equal(gradeIsValid("3.45"), true);
});
