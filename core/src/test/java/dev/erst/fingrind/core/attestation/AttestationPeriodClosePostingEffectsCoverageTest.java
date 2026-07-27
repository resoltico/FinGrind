package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.JULY_2026;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.closePosting;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.replaceFirstField;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.Money;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies that canonical generated close effects retain their posting and linkage invariants. */
class AttestationPeriodClosePostingEffectsCoverageTest {
  private static final String INTERIM_RESULT_SWEEP = "interim-result-sweep";

  @Test
  void postingKindsRequireACompleteCanonicalPeriodCloseDescription() {
    AttestationPreimage effect = interimEffect(List.of());
    List<AttestationPreimage.Fact> postings = interimPostings(effect);

    assertEquals(1, postings.size());
    assertEquals(
        List.of(),
        AttestationPeriodClosePostingEffects.postingsForKind(effect, "fiscal-year-close"));
    assertDoesNotThrow(
        () ->
            AttestationPeriodClosePostingEffects.requireOnlyExpectedPostingKinds(
                effect, postings, List.of()));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPeriodClosePostingEffects.requireOnlyExpectedPostingKinds(
                effect, List.of(), List.of()));

    assertInvalidPostingKind(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.POSTING,
            4,
            AttestationPreimageProjectionFields.token("direct-journal")));
    assertInvalidPostingKind(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.POSTING,
            5,
            AttestationPreimageProjectionFields.token("fiscal-year-close")));
    assertInvalidPostingKind(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.POSTING,
            12,
            AttestationPreimageProjectionFields.token("cli")));
  }

  @Test
  void journalLinesRequireCreatedKnownContiguousPairs() {
    AttestationPreimage effect = interimEffect(List.of());

    assertDoesNotThrow(
        () ->
            AttestationPeriodClosePostingEffects.requireJournalLines(
                effect, interimPostings(effect)));
    assertInvalidJournalLines(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.POSTING,
            0,
            mutation(AttestationEffectMutation.AMEND)));
    assertInvalidJournalLines(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.JOURNAL_LINE,
            0,
            mutation(AttestationEffectMutation.AMEND)));
    assertInvalidJournalLines(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.JOURNAL_LINE,
            1,
            AttestationPreimageProjectionFields.uuid(
                UUID.fromString("11000000-0000-0000-0000-000000000001"))));
    assertInvalidJournalLines(
        withoutFirstRecord(effect, AttestationPeriodCloseProfileFacts.JOURNAL_LINE));
    assertInvalidJournalLines(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.JOURNAL_LINE,
            2,
            AttestationPreimageProjectionFields.unsigned32(2)));
  }

  @Test
  void derivedLinksRequireTheExactPostingSetOrderAndEffectiveDate() {
    AttestationPreimage effect = interimEffect(List.of());
    List<AttestationPreimage.Fact> postings = interimPostings(effect);

    assertDoesNotThrow(
        () ->
            AttestationPeriodClosePostingEffects.requireLinkedPostings(
                effect,
                AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING,
                BigInteger.ONE,
                postings,
                JULY_2026.effectiveDateTo()));
    assertInvalidLinks(
        withoutRecords(effect, AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING));
    assertInvalidLinks(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING,
            1,
            AttestationPreimageProjectionFields.unsigned64(2)));
    assertInvalidLinks(
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING,
            2,
            AttestationPreimageProjectionFields.uuid(
                UUID.fromString("11000000-0000-0000-0000-000000000002"))));
    AttestationPreimage wrongEffectiveDate =
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.POSTING,
            6,
            AttestationPreimageProjectionFields.date(LocalDate.parse("2026-07-30")));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPeriodClosePostingEffects.requireLinkedPostings(
                wrongEffectiveDate,
                AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING,
                BigInteger.ONE,
                interimPostings(wrongEffectiveDate),
                JULY_2026.effectiveDateTo()));
  }

  @Test
  void createdEffectsAndSweepTotalsRequireTheDeclaredSweepOrder() {
    List<CurrencyBalance> totals =
        List.of(CurrencyBalance.ofTotals(Money.parse("EUR", "23.00"), Money.parse("EUR", "5.00")));
    AttestationPreimage effect = interimEffect(totals);

    assertDoesNotThrow(() -> AttestationPeriodClosePostingEffects.requireCreatedEffects(effect));
    assertDoesNotThrow(
        () ->
            AttestationPeriodClosePostingEffects.requireUniqueSweepTotals(effect, BigInteger.ONE));
    AttestationPreimage amendedSweep =
        replaceFirstField(
            effect,
            AttestationPeriodCloseProfileFacts.INTERIM_SWEEP,
            0,
            mutation(AttestationEffectMutation.AMEND));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> AttestationPeriodClosePostingEffects.requireCreatedEffects(amendedSweep));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPeriodClosePostingEffects.requireUniqueSweepTotals(effect, BigInteger.TWO));
  }

  @Test
  void canonicalPreimagesRejectDuplicateCloseIdentitiesBeforeProfileValidation() {
    List<CurrencyBalance> totals =
        List.of(CurrencyBalance.ofTotals(Money.parse("EUR", "23.00"), Money.parse("EUR", "5.00")));
    AttestationPreimage effect = interimEffect(totals);

    assertDuplicateIdentityIsRejected(
        effect,
        AttestationPeriodCloseProfileFacts.POSTING,
        3,
        AttestationPreimageProjectionFields.token("fiscal-year-close"));
    assertDuplicateIdentityIsRejected(
        effect,
        AttestationPeriodCloseProfileFacts.JOURNAL_LINE,
        3,
        AttestationPreimageProjectionFields.text("4001"));
    assertDuplicateIdentityIsRejected(
        effect,
        AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING,
        0,
        mutation(AttestationEffectMutation.AMEND));
    assertDuplicateIdentityIsRejected(
        effect,
        AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_TOTAL,
        3,
        AttestationPreimageProjectionFields.signedMoney("EUR", 1));
  }

  private static void assertInvalidPostingKind(AttestationPreimage effect) {
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPeriodClosePostingEffects.requireOnlyExpectedPostingKinds(
                effect, interimPostings(effect), List.of()));
  }

  private static void assertInvalidJournalLines(AttestationPreimage effect) {
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPeriodClosePostingEffects.requireJournalLines(
                effect, interimPostings(effect)));
  }

  private static void assertInvalidLinks(AttestationPreimage effect) {
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationPeriodClosePostingEffects.requireLinkedPostings(
                effect,
                AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING,
                BigInteger.ONE,
                interimPostings(effect),
                JULY_2026.effectiveDateTo()));
  }

  private static void assertDuplicateIdentityIsRejected(
      AttestationPreimage effect, int recordTypeTag, int fieldIndex, AttestationField replacement) {
    List<AttestationPreimage.Fact> records = new ArrayList<>(effect.records());
    AttestationPreimage.Fact record =
        AttestationPreimageFields.records(effect, recordTypeTag).getFirst();
    records.add(withReplacement(record, fieldIndex, replacement));
    assertThrows(IllegalArgumentException.class, () -> AttestationPreimage.of(records));
  }

  private static AttestationPreimage interimEffect(List<CurrencyBalance> totals) {
    return decode(
        AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                INTERIM_RESULT_SWEEP,
                JULY_2026,
                "3200",
                1,
                totals,
                List.of(closePosting(INTERIM_RESULT_SWEEP)))
            .effect());
  }

  private static List<AttestationPreimage.Fact> interimPostings(AttestationPreimage effect) {
    return AttestationPeriodClosePostingEffects.postingsForKind(effect, INTERIM_RESULT_SWEEP);
  }

  private static AttestationField mutation(AttestationEffectMutation mutation) {
    return AttestationPreimageProjectionFields.present(
        AttestationNumericFieldValue.mutation(mutation.wireValue()));
  }

  private static AttestationPreimage withoutFirstRecord(
      AttestationPreimage effect, int recordTypeTag) {
    List<AttestationPreimage.Fact> records = new ArrayList<>();
    boolean omitted = false;
    for (AttestationPreimage.Fact record : effect.records()) {
      if (!omitted && record.recordTypeTag() == recordTypeTag) {
        omitted = true;
      } else {
        records.add(record);
      }
    }
    if (!omitted) {
      throw new IllegalArgumentException(
          "The requested record type was absent from the close effect.");
    }
    return AttestationPreimage.of(records);
  }

  private static AttestationPreimage withoutRecords(AttestationPreimage effect, int recordTypeTag) {
    return AttestationPreimage.of(
        effect.records().stream()
            .filter(record -> record.recordTypeTag() != recordTypeTag)
            .toList());
  }

  private static AttestationPreimage.Fact withReplacement(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationField replacement) {
    List<AttestationField> fields = new ArrayList<>(fact.fields());
    fields.set(fieldIndex, replacement);
    return new AttestationPreimage.Fact(fact.recordTypeTag(), fields);
  }
}
