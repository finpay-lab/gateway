package com.finpay.gateway.infrastructure;

import com.finpay.gateway.domain.GuardDecision;
import com.finpay.gateway.domain.RequestGuard;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Heuristic, dependency-free implementation of {@link RequestGuard} (FP-64).
 *
 * Scores an inbound request for prompt-injection / anomalous LLM-gateway
 * patterns. No external LLM is required for the default behaviour; an optional
 * BYOK-backed scorer can be supplied via {@link #withScorer(GuardScorer)}.
 *
 * Non-blocking by default: it never returns {@link GuardDecision#block()} unless
 * block mode is enabled AND the score crosses the configured threshold.
 */
public final class HeuristicRequestGuard implements RequestGuard {

    /** Block-mode flag. When false the guard only flags (logs + header). */
    private volatile boolean blockMode = false;
    /** Risk score above which a request is rejected when block mode is on. */
    private volatile double blockThreshold = 0.85;
    /** Optional external scorer (e.g. BYOK LLM). Null = heuristic only. */
    private volatile GuardScorer scorer = null;

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // classic "ignore previous instructions"
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above|system)\\s+instructions", Pattern.CASE_INSENSITIVE),
            // "you are now / act as" role hijack
            Pattern.compile("(you\\s+are\\s+now|act\\s+as|pretend\\s+to\\s+be)\\b", Pattern.CASE_INSENSITIVE),
            // delimiter smuggling
            Pattern.compile("(\\bsystem\\s*:|###|<<<|>>>|\\n\\s*user\\s*:)", Pattern.CASE_INSENSITIVE),
            // prompt extraction
            Pattern.compile("(reveal|print|output|dump)\\s+(your\\s+)?(system\\s+)?(prompt|instructions|rules)", Pattern.CASE_INSENSITIVE),
            // jailbreak tokens
 Pattern.compile("(jailbreak|dan\\s*mode|developer\\s+mode|do\\s+anything\\s+now)", Pattern.CASE_INSENSITIVE)
    );

    public HeuristicRequestGuard blockMode(boolean on) {
        this.blockMode = on;
        return this;
    }

    public HeuristicRequestGuard blockThreshold(double t) {
        this.blockThreshold = t;
        return this;
    }

    /** Plug in a BYOK-backed external scorer (optional). */
    public HeuristicRequestGuard withScorer(GuardScorer scorer) {
        this.scorer = scorer;
        return this;
    }

    @Override
    public GuardDecision evaluate(String method, String path, Map<String, String> headers, String body) {
        double score = 0.0;
        StringBuilder reasons = new StringBuilder();

        String text = (body == null ? "" : body) + " " + (path == null ? "" : path);
        String lower = text.toLowerCase(Locale.ROOT);

        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(lower).find()) {
                score += 0.35;
                reasons.append("injection-pattern;");
            }
        }
        // Repeated instruction-like keywords inflate suspicion.
        long instrHits = countOccurrences(lower, "instruction");
        if (instrHits >= 3) {
            score += 0.15;
            reasons.append("repetitive-instruction;");
        }
        // Overly long payloads are anomalous for an edge guardrail.
        if (body != null && body.length() > 20_000) {
            score += 0.10;
            reasons.append("oversized-payload;");
        }

        // Optional external (BYOK) scorer augments the heuristic.
        if (scorer != null) {
            try {
                double ext = scorer.score(method, path, headers, body);
                score = Math.min(1.0, score + ext * 0.5);
            } catch (RuntimeException ex) {
                // Never let the external scorer break the request path.
                reasons.append("scorer-error;");
            }
        }

        score = Math.max(0.0, Math.min(1.0, score));

        if (blockMode && score >= blockThreshold) {
            return GuardDecision.reject(score, reasons.toString());
        }
        if (score > 0.0) {
            return GuardDecision.flag(score, reasons.toString());
        }
        return GuardDecision.BENIGN;
    }

    private static long countOccurrences(String text, String word) {
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            count++;
            idx += word.length();
        }
        return count;
    }

    /** Optional external scorer hook (BYOK LLM, heuristic fallback, etc.). */
    public interface GuardScorer {
        /** Returns a 0..1 risk contribution. */
        double score(String method, String path, Map<String, String> headers, String body);
    }
}
