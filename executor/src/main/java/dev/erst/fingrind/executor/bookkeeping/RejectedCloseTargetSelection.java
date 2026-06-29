package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.List;
import java.util.Objects;

/** Deterministic close rejection caused by missing or ambiguous close-target candidates. */
public final class RejectedCloseTargetSelection implements CloseTargetSelection {
  private final BookkeepingAdministrationRejection rejection;
  private final List<AccountCode> candidateAccountCodes;

  /** Creates one rejected selection for missing active close-target candidates. */
  public RejectedCloseTargetSelection(
      BookkeepingAdministrationRejection.CloseTargetAccountCandidateMissing rejection) {
    this.rejection = Objects.requireNonNull(rejection, "rejection");
    this.candidateAccountCodes = rejection.inactiveCandidateAccountCodes();
  }

  /** Creates one rejected selection for ambiguous active close-target candidates. */
  public RejectedCloseTargetSelection(CloseTargetAccountCandidateAmbiguous rejection) {
    this.rejection = Objects.requireNonNull(rejection, "rejection");
    this.candidateAccountCodes = rejection.candidateAccountCodes();
  }

  /** Returns the deterministic refusal that prevented close-target selection. */
  public BookkeepingAdministrationRejection rejection() {
    return rejection;
  }

  /** Returns the relevant account candidates named by the deterministic refusal. */
  public List<AccountCode> candidateAccountCodes() {
    return candidateAccountCodes;
  }
}
