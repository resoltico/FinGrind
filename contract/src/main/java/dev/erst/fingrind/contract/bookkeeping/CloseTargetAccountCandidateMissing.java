package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import java.util.Objects;

/**
 * Rejection for one close command when policy finds no active target for the required
 * classification.
 */
public record CloseTargetAccountCandidateMissing(
    FinancialPositionLineClassification requiredFinancialPositionLineClassification,
    List<AccountCode> inactiveCandidateAccountCodes)
    implements BookAdministrationRejection {
  /** Validates the missing close-target selection facts. */
  public CloseTargetAccountCandidateMissing {
    Objects.requireNonNull(
        requiredFinancialPositionLineClassification, "requiredFinancialPositionLineClassification");
    inactiveCandidateAccountCodes = List.copyOf(inactiveCandidateAccountCodes);
  }
}
