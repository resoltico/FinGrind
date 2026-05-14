package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireNonNegative;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Report-oriented JSON records emitted by the CLI transport layer. */
public interface CliReportJsonModels extends CliBookQueryJsonModels {

  record StatementContextPayload(
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      String postingCoverage,
      @Nullable String comparativeReferenceEffectiveDateFrom,
      @Nullable String comparativeReferenceEffectiveDateTo) {
    public StatementContextPayload {
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      postingCoverage = requireText(postingCoverage, "postingCoverage");
      comparativeReferenceEffectiveDateFrom =
          requireOptionalText(
              comparativeReferenceEffectiveDateFrom, "comparativeReferenceEffectiveDateFrom");
      comparativeReferenceEffectiveDateTo =
          requireOptionalText(
              comparativeReferenceEffectiveDateTo, "comparativeReferenceEffectiveDateTo");
    }
  }

  record TrialBalancePayload(
      @Nullable String effectiveDateTo,
      StatementContextPayload context,
      List<TrialBalanceRowPayload> rows)
      implements CliSuccessPayload {
    public TrialBalancePayload {
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      Objects.requireNonNull(context, "context");
      rows = copyList(rows, "rows");
    }
  }

  record TrialBalanceRowPayload(
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      String normalBalance,
      boolean active,
      String declaredAt,
      MonetaryAmount debitTotal,
      MonetaryAmount creditTotal,
      MonetaryAmount netAmount,
      String balanceSide) {
    public TrialBalanceRowPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      accountRole = requireText(accountRole, "accountRole");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      Objects.requireNonNull(debitTotal, "debitTotal");
      Objects.requireNonNull(creditTotal, "creditTotal");
      Objects.requireNonNull(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }

  record AccountLedgerPayload(
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      String normalBalance,
      boolean active,
      String declaredAt,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      List<BalanceBucketPayload> openingBalances,
      List<AccountLedgerEntryPayload> entries,
      List<BalanceBucketPayload> closingBalances)
      implements CliSuccessPayload {
    public AccountLedgerPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      accountRole = requireText(accountRole, "accountRole");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      openingBalances = copyList(openingBalances, "openingBalances");
      entries = copyList(entries, "entries");
      closingBalances = copyList(closingBalances, "closingBalances");
    }
  }

  record AccountLedgerEntryPayload(
      String postingId,
      String effectiveDate,
      String recordedAt,
      MonetaryAmount debitAmount,
      MonetaryAmount creditAmount,
      MonetaryAmount runningBalance,
      String runningBalanceSide,
      List<String> counterpartAccounts) {
    public AccountLedgerEntryPayload {
      postingId = requireText(postingId, "postingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
      Objects.requireNonNull(debitAmount, "debitAmount");
      Objects.requireNonNull(creditAmount, "creditAmount");
      Objects.requireNonNull(runningBalance, "runningBalance");
      runningBalanceSide = requireText(runningBalanceSide, "runningBalanceSide");
      counterpartAccounts = copyList(counterpartAccounts, "counterpartAccounts");
    }
  }

  record PeriodSummaryPayload(
      String effectiveDateFrom,
      String effectiveDateTo,
      int postingCount,
      int postingLineCount,
      int accountsTouched,
      List<BalanceBucketPayload> currencyTotals,
      List<PeriodAccountActivityPayload> accountActivity)
      implements CliSuccessPayload {
    public PeriodSummaryPayload {
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      requireNonNegative(postingCount, "postingCount");
      requireNonNegative(postingLineCount, "postingLineCount");
      requireNonNegative(accountsTouched, "accountsTouched");
      currencyTotals = copyList(currencyTotals, "currencyTotals");
      accountActivity = copyList(accountActivity, "accountActivity");
    }
  }

  record PeriodAccountActivityPayload(
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      String normalBalance,
      boolean active,
      String declaredAt,
      MonetaryAmount debitTotal,
      MonetaryAmount creditTotal,
      MonetaryAmount netAmount,
      String balanceSide) {
    public PeriodAccountActivityPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      accountRole = requireText(accountRole, "accountRole");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      Objects.requireNonNull(debitTotal, "debitTotal");
      Objects.requireNonNull(creditTotal, "creditTotal");
      Objects.requireNonNull(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }

  record FinancialPositionPayload(
      @Nullable String effectiveDateTo,
      StatementContextPayload context,
      List<FinancialPositionSectionPayload> sections)
      implements CliSuccessPayload {
    public FinancialPositionPayload {
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      Objects.requireNonNull(context, "context");
      sections = copyList(sections, "sections");
    }
  }

  record FinancialPositionSectionPayload(
      String accountType,
      List<FinancialPositionRowPayload> rows,
      List<BalanceBucketPayload> totals) {
    public FinancialPositionSectionPayload {
      accountType = requireText(accountType, "accountType");
      rows = copyList(rows, "rows");
      totals = copyList(totals, "totals");
    }
  }

  record FinancialPositionRowPayload(
      String lineCode,
      String lineName,
      String lineType,
      @Nullable String lineRole,
      boolean synthetic,
      BalanceBucketPayload balance) {
    public FinancialPositionRowPayload {
      lineCode = requireText(lineCode, "lineCode");
      lineName = requireText(lineName, "lineName");
      lineType = requireText(lineType, "lineType");
      lineRole = requireOptionalText(lineRole, "lineRole");
      Objects.requireNonNull(balance, "balance");
    }
  }

  record IncomeStatementPayload(
      String effectiveDateFrom,
      String effectiveDateTo,
      StatementContextPayload context,
      List<IncomeStatementSectionPayload> sections,
      List<BalanceBucketPayload> netIncomeTotals)
      implements CliSuccessPayload {
    public IncomeStatementPayload {
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      Objects.requireNonNull(context, "context");
      sections = copyList(sections, "sections");
      netIncomeTotals = copyList(netIncomeTotals, "netIncomeTotals");
    }
  }

  record IncomeStatementSectionPayload(
      String accountType, List<IncomeStatementRowPayload> rows, List<BalanceBucketPayload> totals) {
    public IncomeStatementSectionPayload {
      accountType = requireText(accountType, "accountType");
      rows = copyList(rows, "rows");
      totals = copyList(totals, "totals");
    }
  }

  record IncomeStatementRowPayload(
      String lineCode,
      String lineName,
      String lineType,
      @Nullable String lineRole,
      boolean synthetic,
      BalanceBucketPayload movement) {
    public IncomeStatementRowPayload {
      lineCode = requireText(lineCode, "lineCode");
      lineName = requireText(lineName, "lineName");
      lineType = requireText(lineType, "lineType");
      lineRole = requireOptionalText(lineRole, "lineRole");
      Objects.requireNonNull(movement, "movement");
    }
  }

  record ChangesInEquityPayload(
      String effectiveDateFrom,
      String effectiveDateTo,
      StatementContextPayload context,
      List<ChangesInEquityRowPayload> rows,
      List<BalanceBucketPayload> openingTotals,
      List<BalanceBucketPayload> movementTotals,
      List<BalanceBucketPayload> closingTotals)
      implements CliSuccessPayload {
    public ChangesInEquityPayload {
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      Objects.requireNonNull(context, "context");
      rows = copyList(rows, "rows");
      openingTotals = copyList(openingTotals, "openingTotals");
      movementTotals = copyList(movementTotals, "movementTotals");
      closingTotals = copyList(closingTotals, "closingTotals");
    }
  }

  record ChangesInEquityRowPayload(
      String lineCode,
      String lineName,
      @Nullable String lineType,
      @Nullable String lineRole,
      boolean synthetic,
      BalanceBucketPayload openingBalance,
      BalanceBucketPayload movement,
      BalanceBucketPayload closingBalance) {
    public ChangesInEquityRowPayload {
      lineCode = requireText(lineCode, "lineCode");
      lineName = requireText(lineName, "lineName");
      lineType = requireOptionalText(lineType, "lineType");
      lineRole = requireOptionalText(lineRole, "lineRole");
      Objects.requireNonNull(openingBalance, "openingBalance");
      Objects.requireNonNull(movement, "movement");
      Objects.requireNonNull(closingBalance, "closingBalance");
    }
  }
}
