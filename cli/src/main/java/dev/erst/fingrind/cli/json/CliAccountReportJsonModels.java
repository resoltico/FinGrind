package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Semantic machine payloads for account-centric report families. */
public interface CliAccountReportJsonModels {
  /** Sealed family for account-centric reports with a shared CSV projection owner. */
  sealed interface AccountReportPayload extends CliReportJsonModels.ReportPayload
      permits AccountBalancePayload,
          TrialBalancePayload,
          AccountLedgerPayload,
          PeriodSummaryPayload {}

  record AccountBalancePayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.AccountBalanceResolvedQuery resolvedQuery,
      String generatedAt,
      CliReportValueJsonModels.AccountPayload account,
      List<CliReportValueJsonModels.BalancePayload> balances)
      implements AccountReportPayload {
    public AccountBalancePayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      Objects.requireNonNull(account, "account");
      balances = copyList(balances, "balances");
    }
  }

  record TrialBalancePayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.TrialBalanceResolvedQuery resolvedQuery,
      String generatedAt,
      String balanceState,
      List<AccountBalanceRowPayload> rows,
      List<CliReportValueJsonModels.BalancePayload> totals,
      @Nullable TrialBalanceComparativePayload comparative)
      implements AccountReportPayload {
    public TrialBalancePayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      balanceState = requireText(balanceState, "balanceState");
      rows = copyList(rows, "rows");
      totals = copyList(totals, "totals");
    }
  }

  record TrialBalanceComparativePayload(
      String balanceState,
      List<AccountBalanceRowPayload> rows,
      List<CliReportValueJsonModels.BalancePayload> totals) {
    public TrialBalanceComparativePayload {
      balanceState = requireText(balanceState, "balanceState");
      rows = copyList(rows, "rows");
      totals = copyList(totals, "totals");
    }
  }

  /** One account and its exact balance facts, flattened for table-like report rows. */
  record AccountBalanceRowPayload(
      String accountCode,
      String accountName,
      String accountType,
      String normalBalance,
      boolean active,
      String currencyCode,
      CliReportValueJsonModels.MoneyPayload debitTotal,
      CliReportValueJsonModels.MoneyPayload creditTotal,
      CliReportValueJsonModels.MoneyPayload netAmount,
      String balanceSide) {
    public AccountBalanceRowPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      normalBalance = requireText(normalBalance, "normalBalance");
      currencyCode = requireText(currencyCode, "currencyCode");
      Objects.requireNonNull(debitTotal, "debitTotal");
      Objects.requireNonNull(creditTotal, "creditTotal");
      Objects.requireNonNull(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }

  record AccountLedgerPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.AccountLedgerResolvedQuery resolvedQuery,
      String generatedAt,
      CliReportValueJsonModels.AccountPayload account,
      List<CliReportValueJsonModels.BalancePayload> openingBalances,
      List<AccountLedgerRowPayload> rows,
      List<CliReportValueJsonModels.BalancePayload> closingBalances,
      @Nullable String nextCursor)
      implements AccountReportPayload {
    public AccountLedgerPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      Objects.requireNonNull(account, "account");
      openingBalances = copyList(openingBalances, "openingBalances");
      rows = copyList(rows, "rows");
      closingBalances = copyList(closingBalances, "closingBalances");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
    }
  }

  record AccountLedgerRowPayload(
      String postingId,
      String effectiveDate,
      CliReportValueJsonModels.BalancePayload movement,
      CliReportValueJsonModels.MoneyPayload runningNetAmount,
      String runningBalanceSide,
      @JsonInclude(JsonInclude.Include.ALWAYS)
          @Nullable AttestationCommitPayload attestationCommit) {
    public AccountLedgerRowPayload {
      postingId = requireText(postingId, "postingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      Objects.requireNonNull(movement, "movement");
      Objects.requireNonNull(runningNetAmount, "runningNetAmount");
      runningBalanceSide = requireText(runningBalanceSide, "runningBalanceSide");
    }
  }

  record PeriodSummaryPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.PeriodResolvedQuery resolvedQuery,
      String generatedAt,
      int postingCount,
      int postingLineCount,
      int accountsTouched,
      List<CliReportValueJsonModels.BalancePayload> currencyTotals,
      List<AccountBalanceRowPayload> accountActivity)
      implements AccountReportPayload {
    public PeriodSummaryPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      currencyTotals = copyList(currencyTotals, "currencyTotals");
      accountActivity = copyList(accountActivity, "accountActivity");
    }
  }
}
