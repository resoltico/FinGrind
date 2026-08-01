package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
                "fiscal-year-close", APRIL_2026, "3000", "3200", "3300", 1, null, List.of()));
  }

  @Test
  void closeProjections_requireTheirDeclaredAttestationOperationKinds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                "fiscal-year-close", APRIL_2026, "3200", 1, List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
                "interim-result-sweep", APRIL_2026, "3000", "3200", "3300", 1, null, List.of()));
  }

  @Test
  void closeProjections_canonicalizeEquivalentTokensForGeneratedSystemPostings() {
    AttestationOperationPreimages interim =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            "INTERIM_RESULT_SWEEP",
            APRIL_2026,
            "3200",
            1,
            List.of(),
            List.of(generatedPosting("INTERIM_RESULT_SWEEP", "INTERIM_RESULT_SWEEP", "SYSTEM")));

    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                decode(interim.request()),
                decode(interim.effect())));
  }

  @Test
  void fiscalYearClose_rejectsDerivedInterimSweepsOutsideItsPeriodAndResultHoldingAccount() {
    AttestationClosePostingSnapshot fiscalPosting =
        generatedPosting("fiscal-year-close", "fiscal-year-close", "SYSTEM");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            projectFiscalYearClose(
                derivedSweep(
                    new ReportingPeriod(
                        LocalDate.parse("2026-03-31"), APRIL_2026.effectiveDateTo()),
                    "3200",
                    List.of(
                        generatedPosting(
                            "interim-result-sweep", "interim-result-sweep", "SYSTEM"))),
                fiscalPosting));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            projectFiscalYearClose(
                derivedSweep(
                    new ReportingPeriod(
                        APRIL_2026.effectiveDateFrom(), LocalDate.parse("2026-04-29")),
                    "3200",
                    List.of(
                        generatedPosting(
                            "interim-result-sweep", "interim-result-sweep", "SYSTEM"))),
                fiscalPosting));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            projectFiscalYearClose(
                derivedSweep(
                    APRIL_2026,
                    "3300",
                    List.of(
                        generatedPosting(
                            "interim-result-sweep", "interim-result-sweep", "SYSTEM"))),
                fiscalPosting));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            projectFiscalYearClose(
                derivedSweep(
                    APRIL_2026,
                    "3200",
                    List.of(
                        generatedPosting("interim-result-sweep", "fiscal-year-close", "SYSTEM"))),
                fiscalPosting));
  }

  @Test
  void fiscalYearClose_rejectsOneGeneratedPostingRepresentedByBothDerivedAndCloseEffects() {
    UUID duplicatedPostingId = UUID.fromString("19f8a3ca-1ff0-4bcb-a064-e803a55d6589");
    AttestationClosePostingSnapshot interimPosting =
        generatedPosting(
            duplicatedPostingId, "interim-result-sweep", "interim-result-sweep", "SYSTEM");
    AttestationClosePostingSnapshot fiscalPosting =
        generatedPosting(duplicatedPostingId, "fiscal-year-close", "fiscal-year-close", "SYSTEM");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            projectFiscalYearClose(
                derivedSweep(APRIL_2026, "3200", List.of(interimPosting)), fiscalPosting));
  }

  private static AttestationOperationPreimages projectFiscalYearClose(
      AttestationInterimResultSweepEffect derivedInterimSweep,
      AttestationClosePostingSnapshot fiscalPosting) {
    return AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
        "fiscal-year-close",
        APRIL_2026,
        "3000",
        "3200",
        "3300",
        1,
        derivedInterimSweep,
        List.of(fiscalPosting));
  }

  private static AttestationInterimResultSweepEffect derivedSweep(
      ReportingPeriod reportingPeriod,
      String resultHoldingAccountCode,
      List<AttestationClosePostingSnapshot> postings) {
    return new AttestationInterimResultSweepEffect(
        reportingPeriod, resultHoldingAccountCode, 1, List.of(), postings);
  }

  private static AttestationClosePostingSnapshot generatedPosting(
      String postingKind, String postingOriginKind, String sourceChannel) {
    return generatedPosting(
        UUID.fromString("19f8a3ca-1ff0-4bcb-a064-e803a55d6589"),
        postingKind,
        postingOriginKind,
        sourceChannel);
  }

  private static AttestationClosePostingSnapshot generatedPosting(
      UUID postingId, String postingKind, String postingOriginKind, String sourceChannel) {
    return new AttestationClosePostingSnapshot(
        postingId,
        UUID.fromString("19f8a3ca-2000-4bcb-a064-e803a55d6589"),
        "close-idempotency",
        "close-causation",
        postingKind,
        postingOriginKind,
        APRIL_2026.effectiveDateTo(),
        Instant.parse("2026-04-30T12:30:45Z"),
        sourceChannel,
        List.of(
            new AttestationPostingLine("4000", "DEBIT", "EUR", 100),
            new AttestationPostingLine("3200", "CREDIT", "EUR", 100)));
  }
}
