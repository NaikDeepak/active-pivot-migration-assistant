package com.bank.migration.similarity;

/**
 * Confidence level of a replacement candidate.
 *
 * <p>The prompt builder uses this to decide how much weight to give a match and whether
 * to report "I need additional context" when no HIGH or MEDIUM candidates exist.
 */
public enum Confidence {

    /** Score ≥ 80: exact or near-exact name match — AI should use this. */
    HIGH,

    /** Score ≥ 50: partial name or structural match — AI should consider this. */
    MEDIUM,

    /** Score ≥ 20: loose match — AI should note it but ask for confirmation. */
    LOW,

    /** Score < 20: not a credible replacement — excluded from results. */
    INSUFFICIENT;

    public static Confidence from(double score) {
        if (score >= 80) return HIGH;
        if (score >= 50) return MEDIUM;
        if (score >= 20) return LOW;
        return INSUFFICIENT;
    }
}
