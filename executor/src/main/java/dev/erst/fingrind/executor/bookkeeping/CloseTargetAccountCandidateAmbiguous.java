package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import java.util.Objects;

/** Refusal for one close command when policy finds more than one active declared target. */
public record CloseTargetAccountCandidateAmbiguous(
    FinancialPositionLineClassification requiredFinancialPositionLineClassification,
    List<AccountCode> candidateAccountCodes)
    implements BookkeepingAdministrationRejection {
  public CloseTargetAccountCandidateAmbiguous {
    Objects.requireNonNull(
        requiredFinancialPositionLineClassification, "requiredFinancialPositionLineClassification");
    candidateAccountCodes = List.copyOf(candidateAccountCodes);
  }
}
