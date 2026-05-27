package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Optional;

/** Test-only accessors for split query and statement label helpers. */
final class CliQueryLabelFormatAccess {
  private CliQueryLabelFormatAccess() {}

  static String displayAccountTypeSectionLabel(AccountType accountType) {
    return CliAccountStatementLabels.displayAccountTypeSectionLabel(accountType);
  }

  static String displayLineTypeLabel(AccountType accountType) {
    return CliAccountStatementLabels.displayLineTypeLabel(accountType);
  }

  static String displayRowKind(StatementLineKind lineKind) {
    return CliAccountStatementLabels.displayRowKind(lineKind);
  }

  static String displayStatementLineCode(String lineCode, StatementLineKind lineKind) {
    return CliAccountStatementLabels.displayStatementLineCode(lineCode, lineKind);
  }

  static String displayLineRole(Optional<AccountRole> lineRole) {
    return CliAccountStatementLabels.displayLineRole(lineRole);
  }

  static String displayAccountRoleLabel(AccountRole accountRole) {
    return CliAccountStatementLabels.displayAccountRoleLabel(accountRole);
  }

  static String displayAccountNodeKindLabel(AccountNodeKind nodeKind) {
    return CliAccountStatementLabels.displayAccountNodeKindLabel(nodeKind);
  }

  static String displayFinancialPositionLineClassification(
      FinancialPositionLineClassification lineClassification) {
    return CliAccountStatementLabels.displayFinancialPositionLineClassification(lineClassification);
  }

  static String displayFinancialPositionLineClassification(
      Optional<FinancialPositionLineClassification> lineClassification) {
    return CliAccountStatementLabels.displayFinancialPositionLineClassification(lineClassification);
  }

  static String displayProfitAndLossLineClassification(
      ProfitAndLossLineClassification lineClassification) {
    return CliAccountStatementLabels.displayProfitAndLossLineClassification(lineClassification);
  }

  static String displayNormalBalanceLabel(NormalBalance normalBalance) {
    return CliAccountStatementLabels.displayNormalBalanceLabel(normalBalance);
  }

  static String displayPostingCoverage(PostingCoverage postingCoverage) {
    return CliPostingLabels.displayPostingCoverage(postingCoverage);
  }

  static String displayPostingKind(PostingKind postingKind) {
    return CliPostingLabels.displayPostingKind(postingKind);
  }

  static String displayPostingOriginKind(PostingOriginKind postingOriginKind) {
    return CliPostingLabels.displayPostingOriginKind(postingOriginKind);
  }

  static String displayPostingRoleText(PostingFact postingFact) {
    return CliPostingLabels.displayPostingRoleText(postingFact);
  }

  static String reversalStateWireValue(PostingFact postingFact) {
    return CliPostingLabels.reversalStateWireValue(postingFact);
  }

  static String reversalTargetText(PostingFact postingFact) {
    return CliPostingLabels.reversalTargetText(postingFact);
  }

  static String reversalTargetCsv(PostingFact postingFact) {
    return CliPostingLabels.reversalTargetCsv(postingFact);
  }
}
