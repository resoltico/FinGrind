package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.JULY_2026;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.absent;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.closePosting;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.replaceField;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.replaceFirstField;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.tags;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.token;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.unsigned32;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.Money;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies generated period-close postings and the profile that binds their derived effects. */
class AttestationPeriodCloseProjectionProfileTest {
  @Test
  void periodCloseProjection_commitsGeneratedPostingsAndAggregateCloseEffects() {
    List<AttestationClosePostingSnapshot> postings = List.of(closePosting("interim-result-sweep"));
    List<CurrencyBalance> totals =
        List.of(CurrencyBalance.ofTotals(Money.parse("EUR", "23.00"), Money.parse("EUR", "5.00")));

    AttestationOperationPreimages interim =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            "interim-result-sweep", JULY_2026, "3200", 1, totals, postings);
    AttestationOperationPreimages fiscal =
        AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
            "fiscal-year-close",
            JULY_2026,
            "3000",
            "3200",
            "3300",
            2,
            new AttestationInterimResultSweepEffect(JULY_2026, "3200", 1, totals, postings),
            List.of(closePosting("fiscal-year-close")));

    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.FISCAL_YEAR_CLOSE,
                decode(fiscal.request()),
                decode(fiscal.effect())));

    assertEquals(List.of(0x0100, 0x0120, 0x0140), tags(decode(interim.request())));
    assertEquals("interim-result-sweep", token(decode(interim.request()), 0x0120, 1));
    assertEquals("period-close", token(decode(interim.request()), 0x0120, 3));
    assertTrue(absent(decode(interim.request()), 0x0140, 3));
    assertEquals(
        BigInteger.valueOf(JULY_2026.effectiveDateTo().getYear()),
        unsigned32(decode(fiscal.request()), 0x0140, 3));
    assertEquals(
        List.of(0x0020, 0x0025, 0x0025, 0x0040, 0x0041, 0x0042), tags(decode(interim.effect())));
    assertEquals("interim-result-sweep", token(decode(interim.effect()), 0x0020, 3));
    assertEquals("period-close", token(decode(interim.effect()), 0x0020, 4));
    assertEquals(
        List.of(
            0x0020, 0x0020, 0x0025, 0x0025, 0x0025, 0x0025, 0x0040, 0x0041, 0x0042, 0x0043, 0x0044),
        tags(decode(fiscal.effect())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                "interim-result-sweep", JULY_2026, "3200", 0, totals, postings));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
                "fiscal-year-close", JULY_2026, "3000", "3200", "3300", 1, null, List.of()));
  }

  @Test
  void periodCloseProfile_requiresCompleteContiguousCreatedJournalLinesForEveryGeneratedPosting() {
    AttestationOperationPreimages interim =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            "interim-result-sweep",
            JULY_2026,
            "3200",
            1,
            List.of(),
            List.of(closePosting("interim-result-sweep")));
    AttestationPreimage request = decode(interim.request());
    AttestationPreimage effect = decode(interim.effect());

    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP, request, effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                request,
                replaceFirstField(
                    effect, 0x0025, 2, AttestationPreimageProjectionFields.unsigned32(2))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                request,
                replaceField(
                    effect,
                    0x0025,
                    1,
                    AttestationPreimageProjectionFields.uuid(UUID.randomUUID()))));
  }

  @Test
  void periodCloseProfile_requiresEveryGeneratedCloseFactToBeCreated() {
    List<AttestationClosePostingSnapshot> interimPostings =
        List.of(closePosting("interim-result-sweep"));
    List<CurrencyBalance> totals =
        List.of(CurrencyBalance.ofTotals(Money.parse("EUR", "23.00"), Money.parse("EUR", "5.00")));
    AttestationOperationPreimages fiscal =
        AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
            "fiscal-year-close",
            JULY_2026,
            "3000",
            "3200",
            "3300",
            2,
            new AttestationInterimResultSweepEffect(JULY_2026, "3200", 1, totals, interimPostings),
            List.of(closePosting("fiscal-year-close")));
    AttestationPreimage request = decode(fiscal.request());
    AttestationPreimage effect = decode(fiscal.effect());

    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.FISCAL_YEAR_CLOSE, request, effect));
    for (int recordTypeTag : List.of(0x0020, 0x0025, 0x0040, 0x0041, 0x0042, 0x0043, 0x0044)) {
      assertFailure(
          AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
          () ->
              AttestationOperationProfile.requireDirectProfile(
                  AttestationOperationKind.FISCAL_YEAR_CLOSE,
                  request,
                  replaceField(
                      effect,
                      recordTypeTag,
                      0,
                      AttestationPreimageProjectionFields.present(
                          AttestationNumericFieldValue.mutation(
                              AttestationEffectMutation.AMEND.wireValue())))));
    }
  }

  @Test
  void fiscalCloseProfile_requiresTheClosingCalendarYearAndInterimForbidsIt() {
    AttestationOperationPreimages fiscal =
        AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
            "fiscal-year-close",
            JULY_2026,
            "3000",
            "3200",
            "3300",
            1,
            null,
            List.of(closePosting("fiscal-year-close")));
    AttestationPreimage fiscalRequest = decode(fiscal.request());
    AttestationPreimage fiscalEffect = decode(fiscal.effect());

    assertDoesNotThrow(
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.FISCAL_YEAR_CLOSE, fiscalRequest, fiscalEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.FISCAL_YEAR_CLOSE,
                replaceField(
                    fiscalRequest, 0x0140, 3, AttestationPreimageProjectionFields.unsigned32(2025)),
                fiscalEffect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.FISCAL_YEAR_CLOSE,
                replaceField(fiscalRequest, 0x0140, 3, AttestationField.absent()),
                fiscalEffect));

    AttestationOperationPreimages interim =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            "interim-result-sweep",
            JULY_2026,
            "3200",
            1,
            List.of(),
            List.of(closePosting("interim-result-sweep")));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                replaceField(
                    decode(interim.request()),
                    0x0140,
                    3,
                    AttestationPreimageProjectionFields.unsigned32(2026)),
                decode(interim.effect())));
  }

  @Test
  void periodCloseProfile_rejectsAnyRequestFactThatContradictsTheCloseCommand() {
    AttestationOperationPreimages interim =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            "interim-result-sweep",
            JULY_2026,
            "3200",
            1,
            List.of(),
            List.of(closePosting("interim-result-sweep")));
    AttestationPreimage request = decode(interim.request());
    AttestationPreimage effect = decode(interim.effect());

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                replaceField(
                    request,
                    0x0100,
                    0,
                    AttestationPreimageProjectionFields.token("fiscal-year-close")),
                effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                replaceField(
                    request,
                    0x0120,
                    1,
                    AttestationPreimageProjectionFields.token("fiscal-year-close")),
                effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                replaceField(
                    request,
                    0x0120,
                    3,
                    AttestationPreimageProjectionFields.token("direct-journal")),
                effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                replaceField(
                    request,
                    0x0140,
                    0,
                    AttestationPreimageProjectionFields.token("fiscal-year-close")),
                effect));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.INTERIM_RESULT_SWEEP,
                replaceField(
                    request,
                    0x0120,
                    2,
                    AttestationPreimageProjectionFields.date(LocalDate.parse("2026-07-30"))),
                effect));
  }

  @Test
  void periodCloseRequestFacts_requireExactlyOneCommandPostingAndCloseDeclaration() {
    AttestationOperationPreimages interim =
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
            "interim-result-sweep",
            JULY_2026,
            "3200",
            1,
            List.of(),
            List.of(closePosting("interim-result-sweep")));
    AttestationPreimage request = decode(interim.request());

    assertEquals(
        0x0140,
        AttestationPeriodCloseProfileFacts.requirePeriodCloseRequest(
                AttestationOperationKind.INTERIM_RESULT_SWEEP, request)
            .recordTypeTag());
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPeriodCloseProfileFacts.requirePeriodCloseRequest(
                AttestationOperationKind.INTERIM_RESULT_SWEEP, withoutTag(request, 0x0100)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPeriodCloseProfileFacts.requirePeriodCloseRequest(
                AttestationOperationKind.INTERIM_RESULT_SWEEP, withAdditionalCommandFact(request)));
  }

  @Test
  void fiscalCloseProfile_rejectsEveryMismatchedCloseDeclarationField() {
    AttestationOperationPreimages fiscal = fiscalWithDerivedInterim();
    AttestationPreimage request = decode(fiscal.request());
    AttestationPreimage effect = decode(fiscal.effect());

    assertFiscalProfileFailure(
        request,
        replaceField(
            effect,
            0x0043,
            2,
            AttestationPreimageProjectionFields.date(JULY_2026.effectiveDateFrom().plusDays(1))));
    assertFiscalProfileFailure(
        request,
        replaceField(
            effect,
            0x0043,
            3,
            AttestationPreimageProjectionFields.date(JULY_2026.effectiveDateTo().minusDays(1))));
    assertFiscalProfileFailure(
        request, replaceField(effect, 0x0043, 4, AttestationPreimageProjectionFields.text("3001")));
    assertFiscalProfileFailure(
        request, replaceField(effect, 0x0043, 5, AttestationPreimageProjectionFields.text("3201")));
    assertFiscalProfileFailure(
        request, replaceField(effect, 0x0043, 6, AttestationPreimageProjectionFields.text("3301")));
  }

  @Test
  void fiscalCloseProfile_rejectsMissingFiscalPostingsAndEveryInvalidDerivedSweepField() {
    AttestationOperationPreimages fiscal = fiscalWithDerivedInterim();
    AttestationPreimage request = decode(fiscal.request());
    AttestationPreimage effect = decode(fiscal.effect());

    assertFiscalProfileFailure(
        request,
        replaceField(
            effect, 0x0020, 3, AttestationPreimageProjectionFields.token("interim-result-sweep")));
    assertFiscalProfileFailure(
        request,
        replaceField(
            effect,
            0x0040,
            2,
            AttestationPreimageProjectionFields.date(JULY_2026.effectiveDateFrom().minusDays(1))));
    assertFiscalProfileFailure(
        request,
        replaceField(
            effect,
            0x0040,
            3,
            AttestationPreimageProjectionFields.date(JULY_2026.effectiveDateTo().minusDays(1))));
    assertFiscalProfileFailure(
        request, replaceField(effect, 0x0040, 4, AttestationPreimageProjectionFields.text("3201")));
  }

  private static AttestationPreimage withoutTag(AttestationPreimage preimage, int recordTypeTag) {
    return AttestationPreimage.of(
        preimage.records().stream().filter(fact -> fact.recordTypeTag() != recordTypeTag).toList());
  }

  private static AttestationPreimage withAdditionalCommandFact(AttestationPreimage preimage) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>(preimage.records());
    AttestationPreimage.Fact command =
        facts.stream().filter(fact -> fact.recordTypeTag() == 0x0100).findFirst().orElseThrow();
    List<AttestationField> fields = new ArrayList<>(command.fields());
    fields.set(0, AttestationPreimageProjectionFields.token("other-close-command"));
    facts.add(new AttestationPreimage.Fact(command.recordTypeTag(), fields));
    return AttestationPreimage.of(facts);
  }

  private static AttestationOperationPreimages fiscalWithDerivedInterim() {
    return AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
        "fiscal-year-close",
        JULY_2026,
        "3000",
        "3200",
        "3300",
        2,
        new AttestationInterimResultSweepEffect(JULY_2026, "3200", 1, List.of(), List.of()),
        List.of(closePosting("fiscal-year-close")));
  }

  private static void assertFiscalProfileFailure(
      AttestationPreimage request, AttestationPreimage effect) {
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationOperationProfile.requireDirectProfile(
                AttestationOperationKind.FISCAL_YEAR_CLOSE, request, effect));
  }
}
