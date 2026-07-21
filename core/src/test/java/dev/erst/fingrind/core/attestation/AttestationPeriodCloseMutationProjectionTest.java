package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.ReportingPeriod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the attested close shapes that do and do not require generated postings. */
class AttestationPeriodCloseMutationProjectionTest {
  private static final ReportingPeriod APRIL_2026 =
      new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));

  @Test
  void interimResultSweep_allowsAZeroTotalSweepWithoutGeneratedPostings() {
    assertDoesNotThrow(
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                "interim-result-sweep", APRIL_2026, "3200", 1, List.of(), List.of()));
  }

  @Test
  void fiscalYearClose_requiresAtLeastOneGeneratedPosting() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
                "fiscal-year-close", APRIL_2026, "3000", "3200", "3300", 1, List.of()));
  }
}
