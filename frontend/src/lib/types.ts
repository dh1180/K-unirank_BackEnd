export type University = {
  id: number;
  name: string;
  shortName: string | null;
  region: string | null;
  logoPath: string | null;
};

export type UniversityDetail = University & {
  address: string | null;
  homepageUrl: string | null;
  universityType: string | null;
  establishmentType: string | null;
  units: Array<{ id: number; name: string; collegeName: string }>;
};

export type UniversityPage = {
  items: University[];
  page: number;
  size: number;
  totalCount: number;
  totalPages: number;
  hasNext: boolean;
};

export type GradeComparisonRow = {
  resultId: number;
  universityId: number;
  universityName: string;
  recruitmentUnitId: number;
  recruitmentUnitName: string;
  selectionCategory: string;
  selectionName: string;
  cut50: number | null;
  cut70: number | null;
  referenceCut: number;
  referenceBasis: string;
  sourceType: string;
  sourceName: string;
  positionLabel: string;
  positionTone: "ahead" | "behind" | "near";
};

export type GradeComparisonChart = {
  universityId: number;
  universityName: string;
  averageCut: number;
  sampleCount: number;
  referenceBasis: string;
  positionLabel: string;
  positionTone: "ahead" | "behind" | "near";
};

export type GradeComparison = {
  grade: number;
  selectedYear: number | null;
  availableYears: number[];
  track: "student" | "holistic";
  special: "exclude" | "all";
  scope: "near" | "all";
  chartMin: number;
  totalCount: number;
  chartUniversityCount: number;
  page: number;
  size: number;
  hasNext: boolean;
  items: GradeComparisonRow[];
  charts: GradeComparisonChart[];
};

export type AdmissionMetric = {
  code: string;
  unit: string;
  value: number;
};

export type AdmissionResult = {
  id: number;
  admissionYear: number;
  universityId: number;
  universityName: string;
  recruitmentUnitId: number;
  recruitmentUnitName: string;
  phase: string;
  selectionCategory: string;
  selectionName: string;
  sourceType: string;
  sourceUrl: string;
  recruitmentCount: number | null;
  competitionRate: number | null;
  metrics: AdmissionMetric[];
};

export type AdmissionPage = {
  selectedYear: number | null;
  items: AdmissionResult[];
  page: number;
  size: number;
  totalCount: number;
  totalPages: number;
  hasNext: boolean;
};

export type RankingEntry = {
  rank: number;
  university_id: number;
  name: string;
  rating: number;
  match_count: number;
  win_count: number;
  loss_count: number;
};

export type RankingResult = {
  board: { slug: string; name: string };
  ranking: RankingEntry[];
};
