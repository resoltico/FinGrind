package dev.erst.fingrind.core.attestation;

import java.util.List;

/** Validates the generated effect bundle for one standalone interim-result sweep. */
final class AttestationInterimResultSweepEffectProfile {
  private AttestationInterimResultSweepEffectProfile() {}

  static void requireValid(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    AttestationPreimage.Fact requestClose =
        AttestationPeriodCloseProfileFacts.requirePeriodCloseRequest(
            AttestationOperationKind.INTERIM_RESULT_SWEEP, requestPreimage);
    AttestationPeriodClosePostingEffects.requireCreatedEffects(effectPreimage);
    AttestationPreimage.Fact sweep =
        AttestationPeriodCloseProfileFacts.exactlyOne(
            effectPreimage, AttestationPeriodCloseProfileFacts.INTERIM_SWEEP);
    AttestationPeriodCloseProfileFacts.require(
        AttestationPeriodCloseProfileFacts.date(requestClose, 1)
                .equals(AttestationPeriodCloseProfileFacts.date(sweep, 2))
            && AttestationPeriodCloseProfileFacts.date(requestClose, 2)
                .equals(AttestationPeriodCloseProfileFacts.date(sweep, 3))
            && AttestationPeriodCloseProfileFacts.text(requestClose, 4)
                .equals(AttestationPeriodCloseProfileFacts.text(sweep, 4))
            && AttestationPeriodCloseProfileFacts.absent(requestClose, 5)
            && AttestationPeriodCloseProfileFacts.absent(requestClose, 6));
    List<AttestationPreimage.Fact> postings =
        AttestationPeriodClosePostingEffects.postingsForKind(
            effectPreimage, AttestationOperationKind.INTERIM_RESULT_SWEEP.wireToken());
    List<AttestationPreimage.Fact> allPostings =
        AttestationPeriodClosePostingEffects.requireOnlyExpectedPostingKinds(
            effectPreimage, postings, List.of());
    AttestationPeriodClosePostingEffects.requireJournalLines(effectPreimage, allPostings);
    AttestationPeriodClosePostingEffects.requireLinkedPostings(
        effectPreimage,
        AttestationPeriodCloseProfileFacts.INTERIM_SWEEP_POSTING,
        AttestationPeriodCloseProfileFacts.unsigned64(sweep, 1),
        postings,
        AttestationPeriodCloseProfileFacts.date(sweep, 3));
    AttestationPeriodClosePostingEffects.requireUniqueSweepTotals(
        effectPreimage, AttestationPeriodCloseProfileFacts.unsigned64(sweep, 1));
  }
}
