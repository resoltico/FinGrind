package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import java.util.Objects;

/**
 * Refusal for one close command when policy finds no active declared target for the required
 * financial-position classification.
 */
public record CloseTargetAccountCandidateMissing(
    FinancialPositionLineClassification requiredFinancialPositionLineClassification,
    List<AccountCode> inactiveCandidateAccountCodes)
    implements BookkeepingAdministrationRejection {
  public CloseTargetAccountCandidateMissing {
    Objects.requireNonNull(
        requiredFinancialPositionLineClassification, "requiredFinancialPositionLineClassification");
    inactiveCandidateAccountCodes = List.copyOf(inactiveCandidateAccountCodes);
  }
}
