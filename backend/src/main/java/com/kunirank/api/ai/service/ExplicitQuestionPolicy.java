package com.kunirank.api.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunirank.api.ai.dto.AiQuestion;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Explicit user constraints take precedence over model suggestions. Ported from the Python policy. */
@Component
public class ExplicitQuestionPolicy {
    private static final String NEGATION = "\\s*(?:학과|학부)?\\s*(?:은|는|을|를)?\\s*(?:말고|제외|빼고|아닌)";
    private static final String SPECIAL = "농어촌|기회균등|기회균형|지역인재|특성화고|특별전형";
    private final Map<String, String> regions;
    private final Map<String, String> majors;
    private final Map<String, List<String>> groups;
    private final Map<String, List<String>> aliases;
    private final List<String> regionOrder;
    private final Pattern universityPattern;

    public ExplicitQuestionPolicy(ObjectMapper json) {
        try (var input = getClass().getResourceAsStream("/ai/question-catalog.json")) {
            JsonNode catalog = json.readTree(Objects.requireNonNull(input));
            regions = json.convertValue(catalog.get("REGIONS"), new TypeReference<LinkedHashMap<String, String>>() {});
            majors = json.convertValue(catalog.get("MAJORS"), new TypeReference<LinkedHashMap<String, String>>() {});
            groups = json.convertValue(catalog.get("UNIVERSITY_GROUPS"), new TypeReference<LinkedHashMap<String, List<String>>>() {});
            aliases = json.convertValue(catalog.get("aliases"), new TypeReference<LinkedHashMap<String, List<String>>>() {});
            regionOrder = regions.values().stream().distinct().toList();
            universityPattern = literals(aliases.keySet());
        } catch (IOException ex) {
            throw new IllegalStateException("대학 별칭 카탈로그를 읽을 수 없습니다.", ex);
        }
    }

    public record Explicit(BigDecimal grade, Integer year, String track, String special, String scope,
                           String region, String major, String university, List<String> notes) {
        public boolean hasKnownSubject() { return major != null || university != null; }
    }

    public Explicit read(String question) { return read(question, true); }

    /** Lookup mode has no invented student grade; the remaining explicit constraints are unchanged. */
    public Explicit read(String question, boolean gradeRequired) {
        require(!has("정시|평백|백분위|표준점수", question),
                "현재 AI 검색은 9등급제 내신의 수시 교과·종합 입결 비교를 지원합니다. 정시·백분위는 입결 찾기에서 확인해 주세요.");
        require(!has("5\\s*등급\\s*제|성취도|[ABCDEabcde]\\s*등급", question),
                "현재 자료는 9등급제 내신 기준입니다. 5등급제·성취도를 임의로 환산하지 않습니다.");
        require(!has("등급\\s*(?:초반|중반|후반|대(?:인데|로|에서|\\s|$))|(?<![\\d.])[1-9](?:\\.\\d+)?\\s*[~∼-]\\s*[1-9](?:\\.\\d+)?\\s*등급", question),
                "등급대 대신 정확한 평균등급을 알려주세요. 예: 내신 3.25등급");
        String normalized = question.replaceAll("([1-9])\\s*점\\s*(\\d{1,2})(?!\\d)", "$1.$2");
        var gradeTerms = new ArrayList<>(captures("(?:내신|평균등급|평균)\\s*(?:평균)?\\s*(?:등급)?\\s*(?:이|은|는|:)?\\s*([1-9](?:\\.\\d{1,2})?)(?![\\d.])", normalized));
        gradeTerms.addAll(captures("(?<![\\d.])([1-9](?:\\.\\d{1,2})?)\\s*등급(?!\\s*제)", normalized));
        Set<BigDecimal> grades = new TreeSet<>();
        gradeTerms.forEach(value -> grades.add(new BigDecimal(value)));
        require(!gradeRequired || !grades.isEmpty(), "내신 평균등급을 알려주세요. 예: 내신 3.25등급 서울 간호학과");
        require(grades.size() <= 1, "여러 내신 등급이 입력되었습니다. 비교할 본인의 평균등급 하나만 알려주세요.");
        BigDecimal grade = grades.isEmpty() ? null : grades.iterator().next();
        require(grade == null || grade.compareTo(BigDecimal.ONE) >= 0 && grade.compareTo(BigDecimal.valueOf(9)) <= 0,
                "내신 평균등급은 1.00~9.00 범위로 입력해 주세요.");
        var years = new LinkedHashSet<>(captures("(?<!\\d)(20\\d{2})(?!\\d)", question));
        require(years.size() <= 1, "비교할 입결 학년도를 하나만 지정해 주세요.");
        Integer year = years.isEmpty() ? null : Integer.valueOf(years.iterator().next());
        Set<String> tracks = new LinkedHashSet<>();
        boolean negativeTrack = false;
        for (var match : matches(literals(List.of("학생부교과", "학생부종합", "학종", "교과", "종합")), question)) {
            if (negated(question, match.end())) negativeTrack = true;
            else tracks.add(Set.of("학생부종합", "학종", "종합").contains(match.group()) ? "holistic" : "student");
        }
        require(!negativeTrack || !tracks.isEmpty(), "비교할 전형을 직접 지정해 주세요. 예: 학생부종합");
        require(tracks.size() <= 1, "학생부교과와 학생부종합 중 비교할 전형 하나를 선택해 주세요.");
        var schools = universities(question);
        String masked = schools.masked();
        require(!has("국립(?:대(?:학(?:교)?)?)?\\s*만", masked)
                        && !has("사립.*제외|전문대|수능최저|면접.*(?:없|제외)|여대.*제외|등록금|기숙사|취업률", question),
                "이 검색은 지역·대학·학과·내신·교과/종합 조건을 비교합니다. 대학 유형·최저·면접·등록금 조건은 대학 정보와 모집요강에서 확인해 주세요.");
        require(!has("(?:" + SPECIAL + ")\\s*(?:전형)?\\s*만", question),
                "특별전형만 지정하는 검색은 입결 찾기를 이용해 주세요. 일반 전형과 함께 보려면 '특별전형 포함'이라고 입력해 주세요.");
        String special = has("(?:" + SPECIAL + ").{0,8}포함", question) ? "all" : "exclude";
        var regionAliases = new LinkedHashMap<>(regions);
        regionAliases.put("수도권", "서울,경기,인천");
        regionAliases.put("인서울", "서울");
        Set<String> positive = new HashSet<>(), excluded = new HashSet<>();
        for (var match : matches(literals(regionAliases.keySet()), masked)) {
            (negated(masked, match.end()) ? excluded : positive).addAll(List.of(regionAliases.get(match.group()).split(",")));
        }
        if (masked.contains("전국")) positive.addAll(regionOrder);
        Set<String> selectedRegions = new HashSet<>(positive);
        if (!excluded.isEmpty() && positive.isEmpty()) selectedRegions.addAll(regionOrder);
        selectedRegions.removeAll(excluded);
        require(positive.isEmpty() && excluded.isEmpty() || !selectedRegions.isEmpty(),
                "지정한 지역과 제외 지역이 겹칩니다. 검색할 지역을 다시 지정해 주세요.");
        String regionNames = String.join("|", regionOrder);
        require(!has("(?:" + regionNames + ")[,·/](?:" + regionNames + ")(?:제외|말고|빼고)", masked),
                "제외할 지역을 각각 적어 주세요. 예: 서울 제외, 경기 제외");
        String region = selectedRegions.isEmpty() || selectedRegions.size() == regionOrder.size() ? null
                : String.join(",", regionOrder.stream().filter(selectedRegions::contains).toList());
        List<String> notes = new ArrayList<>();
        Set<String> notedGroups = new HashSet<>();
        for (var match : matches(universityPattern, compact(question))) {
            String alias = match.group();
            if (!groups.containsKey(alias) || !notedGroups.add(alias)) continue;
            String scope = groups.get(alias).equals(groups.get("지거국")) ? " (서울대 제외·기본 대학 항목 기준)" : "";
            notes.add((Set.of("sky", "knu10").contains(alias) ? alias.toUpperCase(Locale.ROOT) : alias)
                    + scope + "은 " + String.join("·", groups.get(alias)) + " 조건으로 검색했습니다.");
        }
        if (schools.names() != null && schools.names().contains("캠퍼스"))
            notes.add("캠퍼스는 결과의 대학 이름으로 구분했습니다. 특정 캠퍼스만 보려면 캠퍼스 이름까지 입력해 주세요.");
        Set<String> majorTerms = new LinkedHashSet<>();
        boolean negativeMajor = false;
        for (var match : matches(literals(majors.keySet()), masked)) {
            if (negated(masked, match.end())) { negativeMajor = true; continue; }
            majorTerms.addAll(List.of(majors.get(match.group()).split(",")));
            if (match.group().equals("컴공")) notes.add("컴공은 컴퓨터·소프트웨어·컴퓨팅 관련 모집단위로 함께 검색했습니다.");
        }
        require(!negativeMajor || !majorTerms.isEmpty(), "학과 제외 대신 관심 있는 학과 이름을 직접 지정해 주세요.");
        require(majorTerms.size() <= 5, "관련 학과 검색어는 한 번에 5개까지 지정해 주세요.");
        if (majorTerms.isEmpty()) {
            for (var match : matches(Pattern.compile("(?:^|\\s)([가-힣A-Za-z]{2,20}(?:학과|학부))"), question)) {
                String term = match.group(1);
                if (!Set.of("전체학과", "모든학과", "갈만한학과", "지원가능한학과", "추천학과").contains(term)
                        && !negated(question, question.indexOf(term) + term.length()))
                    majorTerms.add(term.endsWith("학과") ? term.substring(0, term.length() - 1) : term);
            }
        }
        return new Explicit(grade, year, tracks.stream().findFirst().orElse("student"), special,
                has("전체\\s*(?:등급|입결)|등급\\s*범위\\s*전체", question) ? "all" : "near", region,
                majorTerms.isEmpty() ? null : String.join(",", majorTerms), schools.names(), notes.stream().distinct().toList());
    }

    public AiQuestion.Filters resolve(Explicit explicit, JsonNode model, String question, int limit) {
        String major = explicit.major();
        if (major == null && model.path("major").isTextual() && !model.path("major").asText().isEmpty()) {
            Set<String> selected = explicit.university() == null ? Set.of() : Set.of(explicit.university().split(","));
            List<String> terms = Arrays.stream(model.path("major").asText().split(","))
                    .map(String::strip).filter(term -> !term.isEmpty())
                    .filter(term -> !selected.contains(term) && !(aliases.containsKey(compact(term))
                            && selected.containsAll(aliases.get(compact(term)))))
                    .filter(term -> !Set.of("학과", "갈만한 학과", "갈만한학과", "추천학과", "전체학과", "전체 학과").contains(term)).toList();
            require(terms.size() <= 5 && terms.stream().allMatch(question::contains),
                    "학과 이름을 명확히 적어 주세요. 입력하지 않은 학과로 검색하지 않습니다.");
            if (!terms.isEmpty()) major = String.join(",", terms);
        }
        return new AiQuestion.Filters(explicit.grade(), explicit.year(), explicit.track(), explicit.special(),
                explicit.scope(), explicit.region(), major, explicit.university(), explicit.notes(), limit);
    }

    private record Schools(String names, String masked) {}

    private Schools universities(String question) {
        String text = compact(question);
        StringBuilder masked = new StringBuilder(text);
        Set<String> found = new LinkedHashSet<>();
        for (var match : matches(universityPattern, text)) {
            String tail = text.substring(match.end()).replaceFirst("^(?:라인|대학군|그룹)", "");
            require(!negated(tail, 0), "대학 제외 조건은 아직 지원하지 않습니다. 확인할 대학 이름을 직접 지정해 주세요.");
            require(!groups.containsKey(match.group()) || !hasAtStart("(?:라인)?(?:이상|이하|보다|급|정도)", tail),
                    "대학군의 위아래를 임의의 순위로 해석하지 않습니다. 검색할 대학군이나 대학을 직접 지정해 주세요.");
            found.addAll(aliases.get(match.group()));
            masked.replace(match.start(), match.end(), " ".repeat(match.end() - match.start()));
        }
        for (var match : matches(Pattern.compile("(?:^|\\s)([가-힣A-Za-z]{2,20}(?:대학교|대학|대))(?=\\s|[,·/]|에서|의|를|으로|$)"), question)) {
            String term = match.group(1);
            if (recognized(question, masked, match.start(1), match.end(1)) || aliases.containsKey(term.toLowerCase(Locale.ROOT))
                    || majors.containsKey(term) || Set.of("전체대학", "모든대학", "어느대학", "수도권대학", "서울권대학", "국립대", "사립대", "전문대").contains(term)) continue;
            StringBuilder residue = new StringBuilder(term.toLowerCase(Locale.ROOT));
            var hits = matches(universityPattern, residue.toString());
            for (int i = hits.size() - 1; i >= 0; i--) residue.replace(hits.get(i).start(), hits.get(i).end(), " ".repeat(hits.get(i).group().length()));
            require(residue.toString().replaceAll("이랑|하고|랑|와|과|및|또는|그리고|\\s", "").isEmpty(),
                    "대학 이름을 확인해 주세요. 등록된 정확한 대학명으로 검색할 수 있습니다.");
        }
        for (var match : matches(Pattern.compile("[가-힣A-Za-z]{2,20}"), question.toLowerCase(Locale.ROOT))) {
            if (recognized(question, masked, match.start(), match.end())) continue;
            String candidate = match.group();
            String token = candidate.replaceFirst("(?:중에서는|중에서|에서는|에서|으로|까지|하고|이랑|랑|와|과|만|도|를|을|은|는|의)$", "");
            if (token.length() > 8 || aliases.containsKey(token) || regions.containsKey(token) || majors.containsKey(token)
                    || Set.of("수도권", "인서울", "대학", "학과", "내신", "전체", "전국").contains(token)) continue;
            String near = groups.keySet().stream().filter(group -> oneDifferent(group, token)).findFirst().orElse(null);
            if (near == null) {
                double best = 0.8;
                for (String alias : aliases.keySet()) {
                    double ratio = 2.0 * matchingLength(token, 0, token.length(), alias, 0, alias.length()) / (token.length() + alias.length());
                    if (ratio > best || ratio == best && (near == null || alias.compareTo(near) > 0)) { best = ratio; near = alias; }
                }
            }
            require(near == null, "'" + candidate + "'의 대학 조건을 확인해 주세요. '" + near + "'을 뜻했다면 해당 이름으로 다시 입력해 주세요.");
        }
        String university = String.join(",", found);
        require(found.size() <= 10 && university.length() <= 160, "한 번에 대학 10곳까지 지정해 주세요.");
        return new Schools(found.isEmpty() ? null : university, masked.toString());
    }

    private static boolean oneDifferent(String a, String b) {
        if (a.length() != b.length()) return false;
        int differences = 0;
        for (int i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) differences++;
        return differences == 1;
    }

    // Same longest contiguous matching blocks as Python difflib for these short aliases.
    private static int matchingLength(String a, int startA, int endA, String b, int startB, int endB) {
        int length = 0, first = startA, second = startB;
        for (int i = startA; i < endA; i++) for (int j = startB; j < endB; j++) {
            int run = 0;
            while (i + run < endA && j + run < endB && a.charAt(i + run) == b.charAt(j + run)) run++;
            if (run > length) { length = run; first = i; second = j; }
        }
        if (length == 0) return 0;
        return length + matchingLength(a, startA, first, b, startB, second)
                + matchingLength(a, first + length, endA, b, second + length, endB);
    }

    private static boolean recognized(String question, StringBuilder masked, int start, int end) {
        int left = compact(question.substring(0, start)).length();
        int right = compact(question.substring(0, end)).length();
        return masked.substring(left, right).isBlank();
    }
    private static String compact(String value) { return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private static Pattern literals(Collection<String> terms) {
        return Pattern.compile(String.join("|", terms.stream().sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).toList()), Pattern.CASE_INSENSITIVE);
    }
    private static List<MatchResult> matches(Pattern pattern, String value) { return pattern.matcher(value).results().toList(); }
    private static List<String> captures(String pattern, String value) { return matches(Pattern.compile(pattern), value).stream().map(match -> match.group(1)).toList(); }
    private static boolean has(String pattern, String value) { return Pattern.compile(pattern).matcher(value).find(); }
    private static boolean hasAtStart(String pattern, String value) { return Pattern.compile(pattern).matcher(value).lookingAt(); }
    private static boolean negated(String value, int end) { return hasAtStart(NEGATION, value.substring(end)); }
    private static void require(boolean valid, String detail) {
        if (!valid) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
