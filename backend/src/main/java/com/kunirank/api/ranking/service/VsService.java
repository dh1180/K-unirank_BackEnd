package com.kunirank.api.ranking.service;

import com.kunirank.api.ranking.dto.VsResponse;
import com.kunirank.api.ranking.entity.*;
import com.kunirank.api.ranking.repository.*;
import com.kunirank.api.university.entity.University;
import com.kunirank.api.university.repository.UniversityRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Session-scoped VS matches and votes. No browser-supplied SQL or unverified pair allowed. */
@Service
public class VsService {
    private final RankingBoardRepository boards;
    private final UniversityRepository universities;
    private final UniversityRatingRepository ratings;
    private final VoteSessionRepository sessions;
    private final ComparisonVoteRepository votes;
    private final String signingKey;
    private final String allowedOrigin;
    private final boolean secureCookie;
    private static final SecureRandom RANDOM = new SecureRandom();

    public VsService(RankingBoardRepository boards, UniversityRepository universities,
                     UniversityRatingRepository ratings, VoteSessionRepository sessions,
                     ComparisonVoteRepository votes,
                     @Value("${VS_SIGNING_SECRET:}") String signingKey,
                     @Value("${VS_ALLOWED_ORIGIN:}") String allowedOrigin,
                     @Value("${VS_COOKIE_SECURE:true}") boolean secureCookie) {
        this.boards = boards;
        this.universities = universities;
        this.ratings = ratings;
        this.sessions = sessions;
        this.votes = votes;
        this.signingKey = signingKey;
        this.allowedOrigin = allowedOrigin;
        this.secureCookie = secureCookie;
    }

    private void enabled() {
        if (signingKey.length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VS signing key missing");
        }
    }

    private RankingBoard board(String slug) {
        // Only the overall board is offered publicly; legacy historical boards are read-only.
        if (!"overall".equals(slug)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return boards.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static String newKey() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private Optional<VoteSession> session(String cookie) {
        return cookie == null || cookie.isBlank()
                ? Optional.empty() : sessions.findFirstBySessionKeyOrderByLastSeenAtDesc(cookie);
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private String challenge(VoteSession session, RankingBoard board, Long a, Long b) {
        String payload = session.getId() + ":" + board.getId() + ":" + session.getVoteCount()
                + ":" + a + ":" + b + ":" + Instant.now().getEpochSecond();
        return payload + "." + sign(payload);
    }

    private void validateChallenge(String token, VoteSession session, RankingBoard board,
                                   Long a, Long b) {
        if (token == null || token.length() > 512) throw bad("대진 인증이 누락되었습니다.");
        int sep = token.lastIndexOf('.');
        if (sep < 0) throw bad("대진 인증이 유효하지 않습니다.");
        String payload = token.substring(0, sep);
        String signature = token.substring(sep + 1);
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                                   signature.getBytes(StandardCharsets.US_ASCII))) {
            throw bad("대진 인증이 유효하지 않습니다.");
        }
        String[] parts = payload.split(":");
        try {
            if (parts.length != 6 || !UUID.fromString(parts[0]).equals(session.getId())
                    || Long.parseLong(parts[1]) != board.getId()
                    || Long.parseLong(parts[2]) != session.getVoteCount()
                    || Long.parseLong(parts[3]) != a || Long.parseLong(parts[4]) != b) {
                throw bad("대진 정보가 세션과 일치하지 않습니다.");
            }
            long age = Instant.now().getEpochSecond() - Long.parseLong(parts[5]);
            if (age < 0 || age > 300) throw bad("대진 유효 시간이 지났습니다.");
        } catch (IllegalArgumentException ex) {
            throw bad("대진 인증 형식이 잘못되었습니다.");
        }
    }

    private record Candidate(University university, double rating, double rd, long matches) {}

    @Transactional
    public VsResponse.Pair next(String slug, String cookie, HttpServletResponse response) {
        enabled();
        RankingBoard board = board(slug);
        VoteSession session = session(cookie).orElseGet(() -> {
            VoteSession created = sessions.save(new VoteSession(newKey()));
            ResponseCookie newCookie = ResponseCookie.from("kunirank_vs", created.getSessionKey())
                    .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/")
                    .maxAge(java.time.Duration.ofDays(30)).build();
            response.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());
            return created;
        });
        List<University> active = universities.findByActiveTrueOrderByNameAsc();
        if (active.size() < 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "대학이 부족합니다.");

        Map<Long, UniversityRating> existing = new HashMap<>();
        for (UniversityRating r : ratings.findForBoard(board.getId())) existing.put(r.getUniversity().getId(), r);
        List<Candidate> candidates = active.stream().map(u -> {
            UniversityRating r = existing.get(u.getId());
            return new Candidate(u, r == null ? 1500 : r.getRating(),
                    r == null ? 350 : r.getRatingDeviation(), r == null ? 0 : r.getMatchCount());
        }).toList();
        List<Candidate> ladder = new ArrayList<>(candidates);
        ladder.sort(Comparator.comparingDouble(Candidate::rating).reversed()
                .thenComparing(c -> c.university.getName()));
        Map<Long, Integer> rank = new HashMap<>();
        for (int i = 0; i < ladder.size(); i++) rank.put(ladder.get(i).university.getId(), i);

        List<ComparisonVote> history = votes.recent(session.getId(), board.getId(), PageRequest.of(0, 80));
        Map<Long, Integer> age = new HashMap<>();
        Set<String> pairs = new HashSet<>();
        for (int i = 0; i < history.size(); i++) {
            var v = history.get(i);
            long a = v.getUniversityA().getId(), b = v.getUniversityB().getId();
            age.putIfAbsent(a, i);
            age.putIfAbsent(b, i);
            if (i < 60) pairs.add(Math.min(a, b) + ":" + Math.max(a, b));
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(c -> {
            int last = age.getOrDefault(c.university.getId(), 100);
            double cooldown = last < 4 ? 10000 : last < 10 ? 900 : 0;
            return cooldown + Math.min(c.matches, 60) * 7 - Math.min(c.rd, 350) * 0.28
                    - (c.matches == 0 ? 180 : Math.max(0, 8 - c.matches) * 12)
                    + RANDOM.nextDouble() * 28;
        }));
        Candidate first = sorted.get(RANDOM.nextInt(Math.min(14, sorted.size())));
        double roll = RANDOM.nextDouble();
        List<Candidate> opponents = new ArrayList<>(candidates);
        opponents.removeIf(c -> c.university.getId().equals(first.university.getId()));
        opponents.sort(Comparator.comparingDouble(c -> {
            long a = first.university.getId(), b = c.university.getId();
            int last = age.getOrDefault(b, 100);
            double gap = Math.abs(first.rating - c.rating);
            int rankGap = Math.abs(rank.get(a) - rank.get(b));
            double repeat = pairs.contains(Math.min(a,b) + ":" + Math.max(a,b)) ? 50000 : 0;
            double cooldown = last < 3 ? 5000 : last < 7 ? 500 : 0;
            double mode = roll < 0.8 ? gap * .45 + rankGap * 8
                    + Math.max(0, gap - 110) * 4 + Math.max(0, rankGap - 7) * 90
                    : roll < .95 ? Math.abs(gap - 110) * .55 + rankGap * 3
                    + Math.max(0, rankGap - 15) * 65
                    : gap * .18 + rankGap * 1.5 - Math.min(c.rd,350) * .35
                    - Math.max(0, 12 - c.matches) * 10;
            return repeat + cooldown + mode + RANDOM.nextDouble() * 20;
        }));
        Candidate second = opponents.get(RANDOM.nextInt(Math.min(5, opponents.size())));
        List<University> pair = new ArrayList<>(List.of(first.university, second.university));
        Collections.shuffle(pair, RANDOM);
        List<VsResponse.School> schools = pair.stream().map(u ->
                new VsResponse.School(u.getId(), u.getName(), u.getLogoPath(), u.getRegion())
        ).toList();
        return new VsResponse.Pair(new VsResponse.Board(board.getSlug(), board.getName()), schools,
                challenge(session, board, pair.get(0).getId(), pair.get(1).getId()), session.getVoteCount());
    }

    @Transactional
    public VsResponse.VoteReceipt vote(String slug, String cookie, String origin, VsResponse.VoteRequest req) {
        enabled();
        if (origin != null && !origin.isBlank() && !origin.equals(allowedOrigin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "허용되지 않은 사이트입니다.");
        }
        // Serialize all rating updates on this board, including first-time rating creation.
        RankingBoard board = boards.lockActive(slug)
                .filter(b -> "overall".equals(b.getSlug()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        VoteSession session = session(cookie).orElseThrow(() -> bad("투표 세션이 없습니다."));
        validateChallenge(req.challenge(), session, board, req.universityA(), req.universityB());
        if (req.universityA().equals(req.universityB())) throw bad("동일 대학 투표는 허용되지 않습니다.");
        if (req.skipped() ? req.selectedUniversity() != null
                : !req.universityA().equals(req.selectedUniversity())
                    && !req.universityB().equals(req.selectedUniversity())) {
            throw bad("선택 대학이 대진과 일치하지 않습니다.");
        }
        University a = universities.findById(req.universityA()).filter(University::isActive)
                .orElseThrow(() -> bad("비활성 대학입니다."));
        University b = universities.findById(req.universityB()).filter(University::isActive)
                .orElseThrow(() -> bad("비활성 대학입니다."));
        University selected = req.skipped() ? null : req.selectedUniversity().equals(a.getId()) ? a : b;
        long previous = votes.previous(session.getId(), board.getId(), a.getId(), b.getId());
        ComparisonVote vote = votes.saveAndFlush(new ComparisonVote(board, session, a, b, selected, req.skipped()));
        session.increment();
        if (!req.skipped()) {
            UniversityRating ratingA = ratings.findByBoardIdAndUniversityId(board.getId(), a.getId())
                    .orElseGet(() -> ratings.saveAndFlush(new UniversityRating(board, a)));
            UniversityRating ratingB = ratings.findByBoardIdAndUniversityId(board.getId(), b.getId())
                    .orElseGet(() -> ratings.saveAndFlush(new UniversityRating(board, b)));
            double weight = previous == 0 ? 1 : previous == 1 ? .35 : 0;
            if (weight > 0) {
                Glicko2.State beforeA = Glicko2.effective(new Glicko2.State(ratingA.getRating(),
                        ratingA.getRatingDeviation(), ratingA.getVolatility()));
                Glicko2.State beforeB = Glicko2.effective(new Glicko2.State(ratingB.getRating(),
                        ratingB.getRatingDeviation(), ratingB.getVolatility()));
                boolean aWon = selected.getId().equals(a.getId());
                Glicko2.State afterA = Glicko2.update(beforeA, beforeB, aWon ? 1 : 0);
                Glicko2.State afterB = Glicko2.update(beforeB, beforeA, aWon ? 0 : 1);
                ratingA.record(Glicko2.cappedRating(ratingA.getRating(), afterA.rating(),
                        ratingA.getMatchCount(), weight), afterA.rd(), afterA.volatility(), aWon);
                ratingB.record(Glicko2.cappedRating(ratingB.getRating(), afterB.rating(),
                        ratingB.getMatchCount(), weight), afterB.rd(), afterB.volatility(), !aWon);
            } else {
                boolean aWon = selected.getId().equals(a.getId());
                ratingA.record(ratingA.getRating(), ratingA.getRatingDeviation(), ratingA.getVolatility(), aWon);
                ratingB.record(ratingB.getRating(), ratingB.getRatingDeviation(), ratingB.getVolatility(), !aWon);
            }
        }
        return new VsResponse.VoteReceipt(vote.getId(), session.getVoteCount());
    }
}
