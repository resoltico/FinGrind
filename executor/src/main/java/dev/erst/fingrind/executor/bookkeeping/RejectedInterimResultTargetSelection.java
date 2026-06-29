package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.List;
import java.util.Objects;

/** Deterministic close rejection caused by missing or ambiguous result-holding candidates. */
public final class RejectedInterimResultTargetSelection implements InterimResultTargetSelection {
  private final BookkeepingAdministrationRejection rejection;
  private final List<AccountCode> candidateAccountCodes;

  /** Creates one rejected selection for missing active result-holding candidates. */
  public RejectedInterimResultTargetSelection(
      BookkeepingAdministrationRejection.CloseTargetAccountCandidateMissing rejection) {
    this.rejection = Objects.requireNonNull(rejection, "rejection");
    this.candidateAccountCodes = rejection.inactiveCandidateAccountCodes();
  }

  /** Creates one rejected selection for ambiguous active result-holding candidates. */
  public RejectedInterimResultTargetSelection(CloseTargetAccountCandidateAmbiguous rejection) {
    this.rejection = Objects.requireNonNull(rejection, "rejection");
    this.candidateAccountCodes = rejection.candidateAccountCodes();
  }

  /** Returns the deterministic refusal that prevented close-account selection. */
  public BookkeepingAdministrationRejection rejection() {
    return rejection;
  }

  /** Returns the relevant account candidates named by the deterministic refusal. */
  public List<AccountCode> candidateAccountCodes() {
    return candidateAccountCodes;
  }
}
