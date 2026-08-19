package com.finpay.gateway.infrastructure.guard;

import com.finpay.gateway.domain.guard.GuardDecision;
import com.finpay.gateway.domain.guard.RequestGuard;
import com.finpay.gateway.domain.guard.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic, offline {@link RequestGuard} (AI-7). Scores payloads for
 * known prompt-injection and anomalous patterns; runs first and always, so the
 * gateway keeps a baseline defense even when the LLM scorer is not configured.
 *
 * <p>Scoring model: {@code score = sum(severity weights)} — CRITICAL=5,
 * STRONG=2, ANOMALY=2. A score of 4+ is {@code HIGH} (blocked), 2-3 is
 * {@code MEDIUM}, otherwise {@code LOW}. Pure logic, no framework imports.
 */
public final class HeuristicPromptInjectionGuard implements RequestGuard {

    private static final int HIGH_THRESHOLD = 4;
    private static final int MEDIUM_THRESHOLD = 2;

    private static final long ANOMALY_LENGTH_THRESHOLD = 50_000;
    private static final int ANOMALY_BASE64_MIN_LENGTH = 40;
    private static final int BASE64_TAIL_TOLERANCE = 2;

    private enum Severity {
        CRITICAL(5),
        STRONG(2),
        ANOMALY(2);

        private final int weight;

        Severity(int weight) {
            this.weight = weight;
        }
    }

    private record Rule(Pattern pattern, Severity severity, String reason) {
    }

    // Prompt-injection patterns (case-insensitive). Keep deliberately
    // conservative to avoid blocking benign traffic; the LLM scorer covers the
    // long tail.
    private static final List<Rule> RULES = List.of(
            // CRITICAL: explicit instruction-override / jailbreak.
            rule("\\bignore\\s+(?:all\\s+)?(?:previous|prior)\\s+(?:instructions|prompt|prompts|system\\s+prompt|messages)\\b",
                    Severity.CRITICAL, "instruction_override"),
            rule("\\bdisregard\\s+(?:all\\s+)?(?:previous|prior)\\s+(?:instructions|prompt|prompts)\\b",
                    Severity.CRITICAL, "instruction_override"),
            rule("\\bforget\\s+(?:all\\s+)?(?:previous|prior|everything)\\s+(?:instructions|prompt|prompts|rules)?\\b",
                    Severity.CRITICAL, "instruction_override"),
            rule("\\byou\\s+are\\s+now\\s+", Severity.CRITICAL, "role_override"),
            rule("\\bnew\\s+system\\s+prompt\\b", Severity.CRITICAL, "role_override"),
            rule("\\bjailbreak\\b", Severity.CRITICAL, "jailbreak"),
            rule("\\bdo\\s+anything\\s+now\\b", Severity.CRITICAL, "jailbreak"),
            rule("\\breveal\\s+(?:the\\s+)?(?:system\\s+)?(?:prompt|instructions?)\\b", Severity.CRITICAL, "secret_exfiltration"),
            rule("\\bprint\\s+(?:out\\s+)?(?:your\\s+)?(?:system\\s+)?(?:prompt|instructions?)\\b", Severity.CRITICAL, "secret_exfiltration"),

            // STRONG: softer override / exfiltration / injection payloads.
            rule("\\bignore\\s+(?:the\\s+)?above\\b", Severity.STRONG, "context_override"),
            rule("\\bdisregard\\s+(?:the\\s+)?above\\b", Severity.STRONG, "context_override"),
            rule("\\boverwrite\\s+(?:your|the)\\s+(?:instructions|prompt|rules)\\b", Severity.STRONG, "instruction_override"),
            rule("\\bignore\\s+(?:your|the)\\s+(?:rules|restrictions|guidelines|guardrails)\\b", Severity.STRONG, "instruction_override"),
            rule("\\bact\\s+as\\s+if\\s+you\\s+have\\s+no\\s+(?:rules|restrictions|guardrails|safety)\\b", Severity.STRONG, "jailbreak"),
            rule("\\b(?:extract|output|repeat|reveal)\\s+(?:all|every|the)\\s+(?:\\w+\\s+){0,3}(?:data|information|prompts?|instructions?)\\b",
                    Severity.STRONG, "data_exfiltration"),
            rule("\\bunion\\s+select\\b", Severity.STRONG, "sql_injection"),
            rule("\\bor\\s+1\\s*=\\s*1\\b", Severity.STRONG, "sql_injection"),
            rule("\\bdrop\\s+table\\b", Severity.STRONG, "sql_injection"),
            rule("\\b(?:eval|exec|system|shell_exec)\\s*\\(", Severity.STRONG, "code_injection"),
            rule("\\brm\\s+-rf\\b", Severity.STRONG, "code_injection"),
            rule("<\\s*(?:script|iframe)\\b", Severity.STRONG, "xss"),

            // ANOMALY: statistical outliers that cheap LLM-classifiers still miss.
            rule("(?s)[A-Za-z0-9+/]{" + ANOMALY_BASE64_MIN_LENGTH + ",}={0," + BASE64_TAIL_TOLERANCE + "}",
                    Severity.ANOMALY, "base64_blob")
    );

    private static Rule rule(String regex, Severity severity, String reason) {
        return new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), severity, reason);
    }

    @Override
    public GuardDecision evaluate(String payload) {
        if (payload == null || payload.isBlank()) {
            return GuardDecision.low();
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();

        for (Rule rule : RULES) {
            if (rule.pattern().matcher(payload).find()) {
                score += rule.severity().weight;
                if (!reasons.contains(rule.reason())) {
                    reasons.add(rule.reason());
                }
            }
        }

        if (payload.length() > ANOMALY_LENGTH_THRESHOLD) {
            score += Severity.ANOMALY.weight;
            reasons.add("excessive_length");
        }
        if (containsSuspiciousControlChars(payload)) {
            score += Severity.ANOMALY.weight;
            reasons.add("control_characters");
        }

        RiskLevel risk;
        if (score >= HIGH_THRESHOLD) {
            risk = RiskLevel.HIGH;
        } else if (score >= MEDIUM_THRESHOLD) {
            risk = RiskLevel.MEDIUM;
        } else {
            risk = RiskLevel.LOW;
        }
        return new GuardDecision(risk, reasons);
    }

    private boolean containsSuspiciousControlChars(String payload) {
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        return payload.indexOf('\u0000') >= 0 || payload.toLowerCase(Locale.ROOT).contains("\\u00");
    }
}
