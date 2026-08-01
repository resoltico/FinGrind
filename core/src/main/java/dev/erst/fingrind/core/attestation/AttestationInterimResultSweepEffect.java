package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import java.util.List;
import java.util.Objects;

/**
 * The executor-derived interim sweep that a fiscal-year-close commits as part of its one atomic
 * attestation operation.
 */
public record AttestationInterimResultSweepEffect(
    ReportingPeriod reportingPeriod,
    String resultHoldingAccountCode,
    int sweepOrder,
    List<CurrencyBalance> sweptTotals,
    List<AttestationClosePostingSnapshot> postings) {
  /** Defensively owns the complete immutable interim-sweep effect. */
  public AttestationInterimResultSweepEffect {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    if (Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode").isBlank()) {
      throw new IllegalArgumentException("resultHoldingAccountCode must not be blank.");
    }
    if (sweepOrder < 1) {
      throw new IllegalArgumentException("sweepOrder must be at least one.");
    }
    sweptTotals = List.copyOf(Objects.requireNonNull(sweptTotals, "sweptTotals"));
    postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
  }
}
