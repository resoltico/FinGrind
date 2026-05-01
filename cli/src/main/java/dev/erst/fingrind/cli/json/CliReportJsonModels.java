package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireNonNegative;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Report-oriented JSON records emitted by the CLI transport layer. */
public interface CliReportJsonModels extends CliBookQueryJsonModels {

  record TrialBalancePayload(@Nullable String effectiveDateTo, List<TrialBalanceRowPayload> rows) {
    public TrialBalancePayload {
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      rows = copyList(rows);
    }
  }

  record TrialBalanceRowPayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt,
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      String balanceSide) {
    public TrialBalanceRowPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      currencyCode = requireText(currencyCode, "currencyCode");
      debitTotal = requireText(debitTotal, "debitTotal");
      creditTotal = requireText(creditTotal, "creditTotal");
      netAmount = requireText(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }

  record AccountLedgerPayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      List<BalanceBucketPayload> openingBalances,
      List<AccountLedgerEntryPayload> entries,
      List<BalanceBucketPayload> closingBalances) {
    public AccountLedgerPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      openingBalances = copyList(openingBalances);
      entries = copyList(entries);
      closingBalances = copyList(closingBalances);
    }
  }

  record AccountLedgerEntryPayload(
      String postingId,
      String effectiveDate,
      String recordedAt,
      String currencyCode,
      String debitAmount,
      String creditAmount,
      String runningBalance,
      String runningBalanceSide,
      List<String> counterpartAccounts) {
    public AccountLedgerEntryPayload {
      postingId = requireText(postingId, "postingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
      currencyCode = requireText(currencyCode, "currencyCode");
      debitAmount = requireText(debitAmount, "debitAmount");
      creditAmount = requireText(creditAmount, "creditAmount");
      runningBalance = requireText(runningBalance, "runningBalance");
      runningBalanceSide = requireText(runningBalanceSide, "runningBalanceSide");
      counterpartAccounts = copyList(counterpartAccounts);
    }
  }

  record PeriodSummaryPayload(
      String effectiveDateFrom,
      String effectiveDateTo,
      int postingCount,
      int postingLineCount,
      int accountsTouched,
      List<BalanceBucketPayload> currencyTotals,
      List<PeriodAccountActivityPayload> accountActivity) {
    public PeriodSummaryPayload {
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      requireNonNegative(postingCount, "postingCount");
      requireNonNegative(postingLineCount, "postingLineCount");
      requireNonNegative(accountsTouched, "accountsTouched");
      currencyTotals = copyList(currencyTotals);
      accountActivity = copyList(accountActivity);
    }
  }

  record PeriodAccountActivityPayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt,
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      String balanceSide) {
    public PeriodAccountActivityPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      currencyCode = requireText(currencyCode, "currencyCode");
      debitTotal = requireText(debitTotal, "debitTotal");
      creditTotal = requireText(creditTotal, "creditTotal");
      netAmount = requireText(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }
}
