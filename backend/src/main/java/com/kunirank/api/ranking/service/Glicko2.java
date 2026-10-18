package com.kunirank.api.ranking.service;

/**
 * Numerical port of K-unirank_v2/rankings/services.py, with identical
 * Glicko-2 tau, epsilon, effective RD and per-vote caps.
 */
public final class Glicko2 {
    private Glicko2() {}
    private static final double SCALE = 173.7178;
    private static final double TAU = 0.5;
    private static final double EPS = 0.000001;

    public record State(double rating, double rd, double volatility) {}

    private static double g(double phi) {
        return 1 / Math.sqrt(1 + 3 * phi * phi / (Math.PI * Math.PI));
    }

    private static double expectation(double mu, double otherMu, double otherPhi) {
        return 1 / (1 + Math.exp(-g(otherPhi) * (mu - otherMu)));
    }

    private static double f(double x, double phi, double delta, double v, double a) {
        double ex = Math.exp(x);
        return ex * (delta * delta - phi * phi - v - ex)
                / (2 * Math.pow(phi * phi + v + ex, 2))
                - (x - a) / (TAU * TAU);
    }

    private static double newVolatility(double phi, double sigma, double delta, double v) {
        double a = Math.log(sigma * sigma);
        double left = a;
        double right;
        if (delta * delta > phi * phi + v) {
            right = Math.log(delta * delta - phi * phi - v);
        } else {
            int k = 1;
            while (f(a - k * TAU, phi, delta, v, a) < 0) {
                k++;
                if (k > 10000) throw new IllegalStateException("Glicko volatility did not bracket");
            }
            right = a - k * TAU;
        }
        double fLeft = f(left, phi, delta, v, a);
        double fRight = f(right, phi, delta, v, a);
        for (int i = 0; Math.abs(right - left) > EPS; i++) {
            if (i > 10000) throw new IllegalStateException("Glicko volatility did not converge");
            double candidate = left + (left - right) * fLeft / (fRight - fLeft);
            double fCandidate = f(candidate, phi, delta, v, a);
            if (fCandidate * fRight < 0) {
                left = right;
                fLeft = fRight;
            } else {
                fLeft /= 2;
            }
            right = candidate;
            fRight = fCandidate;
        }
        return Math.exp(left / 2);
    }

    public static State update(State self, State opponent, double score) {
        double mu = (self.rating - 1500) / SCALE;
        double phi = self.rd / SCALE;
        double otherMu = (opponent.rating - 1500) / SCALE;
        double otherPhi = opponent.rd / SCALE;
        double g = g(otherPhi);
        double expected = expectation(mu, otherMu, otherPhi);
        double v = 1 / (g * g * expected * (1 - expected));
        double delta = v * g * (score - expected);
        double sigma = newVolatility(phi, self.volatility, delta, v);
        double phiStar = Math.sqrt(phi * phi + sigma * sigma);
        double newPhi = 1 / Math.sqrt(1 / (phiStar * phiStar) + 1 / v);
        double newMu = mu + newPhi * newPhi * g * (score - expected);
        return new State(
                1500 + SCALE * newMu,
                Math.max(30, Math.min(350, SCALE * newPhi)),
                sigma
        );
    }

    public static State effective(State s) {
        return new State(s.rating, Math.min(150, Math.max(30, Math.min(350, s.rd))), s.volatility);
    }

    public static double cappedRating(double before, double proposed, long matches, double weight) {
        if (weight <= 0) return before;
        double cap = matches < 20 ? 22.0 : 16.0;
        double delta = Math.max(-cap * weight, Math.min(cap * weight, (proposed - before) * weight));
        return before + delta;
    }
}
