package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.replaceField;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.ReportingPeriod;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises the accepted-chain state that makes system reporting-period closes deterministic. */
class AttestationPeriodCloseHistoryTest {
  private static final LocalDate FIRST_FISCAL_YEAR_START = LocalDate.of(2026, 1, 1);
  private static final LocalDate FIRST_FISCAL_YEAR_END = LocalDate.of(2026, 12, 31);

  @Test
  void acceptsAContiguousSystemTimelineIncludingTheInterimEffectEmbeddedInFiscalClose() {
    AttestationPeriodCloseHistory history = history();
    CloseOperation interim =
        interim(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, LocalDate.of(2026, 12, 30)),
            1,
            Instant.parse("2026-12-31T03:00:00Z"));
    CloseOperation fiscal =
        fiscal(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, FIRST_FISCAL_YEAR_END),
            1,
            new AttestationInterimResultSweepEffect(
                new ReportingPeriod(LocalDate.of(2026, 12, 31), FIRST_FISCAL_YEAR_END),
                "3000",
                2,
                List.of(),
                List.of()),
            Instant.parse("2027-02-01T03:00:00Z"));
    CloseOperation followingInterim =
        interim(
            new ReportingPeriod(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 30)),
            3,
            Instant.parse("2027-12-31T03:00:00Z"));

    history = accept(history, interim);
    history = accept(history, fiscal);
    AttestationPeriodCloseHistory accepted = history;

    assertDoesNotThrow(() -> accept(accepted, followingInterim));
    assertEquals(LocalDate.of(2027, 1, 1), history.expectedNextSweepEffectiveFrom());
    assertEquals(LocalDate.of(2027, 1, 1), history.expectedNextFiscalYearEffectiveFrom());
  }

  @Test
  void rejectsDuplicateSweepOrdersAndOverlappingInterimIntervals() {
    AttestationPeriodCloseHistory accepted =
        accept(
            history(),
            interim(
                new ReportingPeriod(FIRST_FISCAL_YEAR_START, LocalDate.of(2026, 12, 30)),
                1,
                Instant.parse("2026-12-31T03:00:00Z")));
    CloseOperation duplicateOrder =
        interim(
            new ReportingPeriod(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)),
            1,
            Instant.parse("2027-01-02T03:00:00Z"));
    CloseOperation overlappingInterval =
        interim(
            new ReportingPeriod(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 1)),
            2,
            Instant.parse("2027-01-02T03:00:00Z"));

    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () -> accept(accepted, duplicateOrder));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () -> accept(accepted, overlappingInterval));
  }

  @Test
  void rejectsDuplicateFiscalOrdersAndARepeatedFiscalYearInsteadOfTheOldestUnclosedYear() {
    AttestationPeriodCloseHistory accepted =
        accept(
            history(),
            fiscal(
                new ReportingPeriod(FIRST_FISCAL_YEAR_START, FIRST_FISCAL_YEAR_END),
                1,
                null,
                Instant.parse("2027-02-01T03:00:00Z")));
    CloseOperation duplicateOrder =
        fiscal(
            new ReportingPeriod(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)),
            1,
            null,
            Instant.parse("2028-02-01T03:00:00Z"));
    CloseOperation repeatedFiscalYear =
        fiscal(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, FIRST_FISCAL_YEAR_END),
            2,
            null,
            Instant.parse("2028-02-01T03:00:00Z"));

    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () -> accept(accepted, duplicateOrder));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () -> accept(accepted, repeatedFiscalYear));
  }

  @Test
  void rejectsAnEmbeddedInterimSweepThatOverlapsTheAcceptedSweepHorizon() {
    AttestationPeriodCloseHistory accepted =
        accept(
            history(),
            interim(
                new ReportingPeriod(FIRST_FISCAL_YEAR_START, LocalDate.of(2026, 12, 30)),
                1,
                Instant.parse("2026-12-31T03:00:00Z")));
    CloseOperation overlappingFiscal =
        fiscal(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, FIRST_FISCAL_YEAR_END),
            1,
            new AttestationInterimResultSweepEffect(
                new ReportingPeriod(LocalDate.of(2026, 12, 30), FIRST_FISCAL_YEAR_END),
                "3000",
                2,
                List.of(),
                List.of()),
            Instant.parse("2027-01-01T03:00:00Z"));

    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () -> accept(accepted, overlappingFiscal));
  }

  @Test
  void directAcceptanceRetainsNonCloseOperationsAndAdvancesOnlyTheirOwnCloseHorizons() {
    AttestationPeriodCloseHistory initial = history();
    CloseOperation interim =
        interim(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, LocalDate.of(2026, 12, 30)),
            1,
            Instant.parse("2026-12-31T03:00:00Z"));
    CloseOperation fiscal =
        fiscal(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, FIRST_FISCAL_YEAR_END),
            1,
            null,
            Instant.parse("2027-02-01T03:00:00Z"));

    assertEquals(FIRST_FISCAL_YEAR_START, initial.expectedNextSweepEffectiveFrom());
    assertEquals(FIRST_FISCAL_YEAR_START, initial.expectedNextFiscalYearEffectiveFrom());
    AttestationPeriodCloseHistory afterNonClose =
        initial.accept(AttestationOperationKind.POST_ENTRY, AttestationPreimage.of(List.of()));
    assertEquals(FIRST_FISCAL_YEAR_START, afterNonClose.expectedNextSweepEffectiveFrom());
    assertEquals(FIRST_FISCAL_YEAR_START, afterNonClose.expectedNextFiscalYearEffectiveFrom());
    AttestationPeriodCloseHistory afterInterim =
        afterNonClose.accept(
            AttestationOperationKind.INTERIM_RESULT_SWEEP, interim.effectPreimage());
    AttestationPeriodCloseHistory afterFiscal =
        afterInterim.accept(AttestationOperationKind.FISCAL_YEAR_CLOSE, fiscal.effectPreimage());

    assertEquals(LocalDate.of(2026, 12, 31), afterInterim.expectedNextSweepEffectiveFrom());
    assertEquals(LocalDate.of(2027, 1, 1), afterFiscal.expectedNextSweepEffectiveFrom());
    assertEquals(LocalDate.of(2027, 1, 1), afterFiscal.expectedNextFiscalYearEffectiveFrom());
  }

  @Test
  void systemAcceptanceRejectsCloseDatesThatDoNotMatchItsRecordedDayOrFiscalHorizon() {
    CloseOperation interimWithLateRecordedDate =
        interim(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, LocalDate.of(2026, 12, 30)),
            1,
            Instant.parse("2027-01-01T03:00:00Z"));
    CloseOperation fiscalBeforeItsEffectiveEnd =
        fiscal(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, FIRST_FISCAL_YEAR_END),
            1,
            null,
            Instant.parse("2026-12-30T03:00:00Z"));

    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () -> accept(history(), interimWithLateRecordedDate));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () -> accept(history(), fiscalBeforeItsEffectiveEnd));
  }

  @Test
  void genesisPreservesTheInvalidFiscalCalendarValueAsItsFailureCause() {
    AttestationPreimage malformedGenesis =
        AttestationGenesisTestSupport.replaceFirstRecord(
            AttestationGenesisTestSupport.genesisEffectPreimage(
                AttestationAuthorizationTestSupport.credential()),
            0x0001,
            identity ->
                AttestationGenesisTestSupport.withField(
                    identity,
                    10,
                    AttestationField.present(AttestationNumericFieldValue.unsigned8(13))));

    AttestationAuthorizationException failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            AttestationAuthorizationException.class,
            () -> AttestationPeriodCloseHistory.genesis(malformedGenesis));
    assertEquals(AttestationAuthorizationFailure.GENESIS_INVALID, failure.failure());
    org.junit.jupiter.api.Assertions.assertInstanceOf(
        java.time.DateTimeException.class, failure.getCause());
  }

  @Test
  void rejectsMissingGenesisIdentityAndCloseFacts() {
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () -> AttestationPeriodCloseHistory.genesis(AttestationPreimage.of(List.of())));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            history()
                .accept(
                    AttestationOperationKind.INTERIM_RESULT_SWEEP,
                    AttestationPreimage.of(List.of())));
  }

  @Test
  void rejectsCloseIntervalsEndingBeforeTheirRequiredCloseHorizons() {
    CloseOperation interim =
        interim(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, LocalDate.of(2026, 12, 30)),
            1,
            Instant.parse("2026-12-31T03:00:00Z"));
    CloseOperation fiscal =
        fiscal(
            new ReportingPeriod(FIRST_FISCAL_YEAR_START, FIRST_FISCAL_YEAR_END),
            1,
            null,
            Instant.parse("2027-02-01T03:00:00Z"));

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            history()
                .accept(
                    AttestationOperationKind.INTERIM_RESULT_SWEEP,
                    replaceField(
                        interim.effectPreimage(),
                        0x0040,
                        3,
                        AttestationPreimageProjectionFields.date(LocalDate.of(2025, 12, 31)))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            history()
                .accept(
                    AttestationOperationKind.FISCAL_YEAR_CLOSE,
                    replaceField(
                        fiscal.effectPreimage(),
                        0x0043,
                        3,
                        AttestationPreimageProjectionFields.date(LocalDate.of(2026, 12, 30)))));
  }

  private static AttestationPeriodCloseHistory history() {
    return AttestationPeriodCloseHistory.genesis(
        AttestationGenesisTestSupport.genesisEffectPreimage(
            AttestationAuthorizationTestSupport.credential()));
  }

  private static AttestationPeriodCloseHistory accept(
      AttestationPeriodCloseHistory history, CloseOperation operation) {
    return history.acceptSystem(
        operation.operationKind(), operation.payload(), operation.effectPreimage());
  }

  private static CloseOperation interim(
      ReportingPeriod reportingPeriod, int sweepOrder, Instant recordedAt) {
    AttestationOperationPreimages preimages =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            AttestationOperationKind.INTERIM_RESULT_SWEEP.wireToken(),
            reportingPeriod,
            "3000",
            sweepOrder,
            List.of(),
            List.of());
    return closeOperation(AttestationOperationKind.INTERIM_RESULT_SWEEP, preimages, recordedAt);
  }

  private static CloseOperation fiscal(
      ReportingPeriod reportingPeriod,
      int closeOrder,
      @org.jspecify.annotations.Nullable AttestationInterimResultSweepEffect derivedInterimSweep,
      Instant recordedAt) {
    AttestationOperationPreimages preimages =
        AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
            AttestationOperationKind.FISCAL_YEAR_CLOSE.wireToken(),
            reportingPeriod,
            "3100",
            "3000",
            "3200",
            closeOrder,
            derivedInterimSweep,
            List.of(fiscalPosting(reportingPeriod.effectiveDateTo(), recordedAt)));
    return closeOperation(AttestationOperationKind.FISCAL_YEAR_CLOSE, preimages, recordedAt);
  }

  private static CloseOperation closeOperation(
      AttestationOperationKind operationKind,
      AttestationOperationPreimages preimages,
      Instant recordedAt) {
    AttestationPreimage request =
        AttestationPreimage.decode(
            preimages.request(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationPreimage effect =
        AttestationPreimage.decode(
            preimages.effect(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    assertDoesNotThrow(
        () -> AttestationOperationProfile.requireDirectProfile(operationKind, request, effect));
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            operationKind.wireToken(),
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            recordedAt,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    return new CloseOperation(operationKind, payload, effect);
  }

  private static AttestationClosePostingSnapshot fiscalPosting(
      LocalDate effectiveDate, Instant recordedAt) {
    return new AttestationClosePostingSnapshot(
        UUID.fromString("00112233-4455-6677-8899-aabbccddee01"),
        UUID.fromString("00112233-4455-6677-8899-aabbccddee02"),
        "close-idempotency",
        "close-causation",
        AttestationOperationKind.FISCAL_YEAR_CLOSE.wireToken(),
        AttestationOperationKind.FISCAL_YEAR_CLOSE.wireToken(),
        effectiveDate,
        recordedAt,
        "system",
        List.of(
            new AttestationPostingLine("3000", "DEBIT", "EUR", 100),
            new AttestationPostingLine("3200", "CREDIT", "EUR", 100)));
  }

  private record CloseOperation(
      AttestationOperationKind operationKind,
      AttestationOperationPayload payload,
      AttestationPreimage effectPreimage) {}
}
