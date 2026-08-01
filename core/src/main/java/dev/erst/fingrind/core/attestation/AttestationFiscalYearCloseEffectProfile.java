package dev.erst.fingrind.core.attestation;

import java.util.List;

/**
 * Validates the generated effect bundle for one fiscal-year close and its optional interim sweep.
 */
final class AttestationFiscalYearCloseEffectProfile {
  private AttestationFiscalYearCloseEffectProfile() {}

  static void requireValid(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    AttestationPreimage.Fact requestClose =
        AttestationPeriodCloseProfileFacts.requirePeriodCloseRequest(
            AttestationOperationKind.FISCAL_YEAR_CLOSE, requestPreimage);
    AttestationPeriodClosePostingEffects.requireCreatedEffects(effectPreimage);
    AttestationPreimage.Fact close =
        AttestationPeriodCloseProfileFacts.exactlyOne(
            effectPreimage, AttestationPeriodCloseProfileFacts.FISCAL_CLOSE);
    AttestationPeriodCloseProfileFacts.require(
        AttestationPeriodCloseProfileFacts.date(requestClose, 1)
                .equals(AttestationPeriodCloseProfileFacts.date(close, 2))
            && AttestationPeriodCloseProfileFacts.date(requestClose, 2)
                .equals(AttestationPeriodCloseProfileFacts.date(close, 3))
            && AttestationPeriodCloseProfileFacts.text(requestClose, 5)
                .equals(AttestationPeriodCloseProfileFacts.text(close, 4))
            && AttestationPeriodCloseProfileFacts.text(requestClose, 4)
                .equals(AttestationPeriodCloseProfileFacts.text(close, 5))
            && AttestationPeriodCloseProfileFacts.text(requestClose, 6)
                .equals(AttestationPeriodCloseProfileFacts.text(close, 6)));
    List<AttestationPreimage.Fact> fiscalPostings =
        AttestationPeriodClosePostingEffects.postingsForKind(
            effectPreimage, AttestationOperationKind.FISCAL_YEAR_CLOSE.wireToken());
    List<AttestationPreimage.Fact> interimPostings =
        AttestationPeriodClosePostingEffects.postingsForKind(
            effectPreimage, AttestationOperationKind.INTERIM_RESULT_SWEEP.wireToken());
    AttestationPeriodCloseProfileFacts.require(!fiscalPostings.isEmpty());
    List<AttestationPreimage.Fact> allPostings =
        AttestationPeriodClosePostingEffects.requireOnlyExpectedPostingKinds(
            effectPreimage, fiscalPostings, interimPostings);
    AttestationPeriodClosePostingEffects.requireJournalLines(effectPreimage, allPostings);
    AttestationPeriodClosePostingEffects.requireLinkedPostings(
        effectPreimage,
        AttestationPeriodCloseProfileFacts.FISCAL_CLOSE_POSTING,
        AttestationPeriodCloseProfileFacts.unsigned64(close, 1),
        fiscalPostings,
        AttestationPeriodCloseProfileFacts.date(close, 3));

    List<AttestationPreimage.Fact> sweeps =
        AttestationPreimageFields.records(
            effectPreimage, AttestationPeriodCloseProfileFacts.INTERIM_SWEEP);
    if (sweeps.isEmpty()) {
      AttestationPeriodCloseProfileFacts.require(interimPostings.isEmpty());
      AttestationPeriodCloseProfileFacts.require(
          AttestationPreimageFields.records(
                  effectPreimage, AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_TOTAL)
              .isEmpty());
      AttestationPeriodCloseProfileFacts.require(
          AttestationPreimageFields.records(
                  effectPreimage, AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING)
              .isEmpty());
      return;
    }
    AttestationPreimage.Fact sweep =
        AttestationPeriodCloseProfileFacts.exactlyOne(
            effectPreimage, AttestationPeriodCloseProfileFacts.INTERIM_SWEEP);
    AttestationPeriodCloseProfileFacts.require(
        !AttestationPeriodCloseProfileFacts.date(sweep, 2)
                .isBefore(AttestationPeriodCloseProfileFacts.date(requestClose, 1))
            && AttestationPeriodCloseProfileFacts.date(sweep, 3)
                .equals(AttestationPeriodCloseProfileFacts.date(requestClose, 2))
            && AttestationPeriodCloseProfileFacts.text(sweep, 4)
                .equals(AttestationPeriodCloseProfileFacts.text(requestClose, 4)));
    AttestationPeriodClosePostingEffects.requireLinkedPostings(
        effectPreimage,
        AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING,
        AttestationPeriodCloseProfileFacts.unsigned64(sweep, 1),
        interimPostings,
        AttestationPeriodCloseProfileFacts.date(sweep, 3));
    AttestationPeriodClosePostingEffects.requireUniqueSweepTotals(
        effectPreimage, AttestationPeriodCloseProfileFacts.unsigned64(sweep, 1));
  }
}
