package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntrySemanticsViolationFactory;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;

/** Event-role account semantics for the published business-event posting surface. */
final class PostEntryRoleAccountSemantics {
  private PostEntryRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry entry,
      String selectorField,
      String selectorValue) {
    if (entry instanceof BookkeepingEntry.OpeningPosition
        || entry instanceof BookkeepingEntry.Reversal) {
      return;
    }
    if (entry instanceof BookkeepingEntry.DirectJournal journal) {
      DirectJournalEntrySemantics.validate(
          violations, accounts, selectorField, selectorValue, journal.lines());
      return;
    }
    if (entry instanceof BookkeepingEntry.Sale event) {
      validateSale(violations, accounts, event, selectorField, selectorValue);
      return;
    }
    if (entry instanceof BookkeepingEntry.Expense event) {
      validateExpense(violations, accounts, event, selectorField, selectorValue);
      return;
    }
    if (entry instanceof BookkeepingEntry.OwnerContribution event) {
      validateOwnerContribution(violations, accounts, event, selectorField, selectorValue);
      return;
    }
    BookkeepingEntry.OwnerWithdrawal event = (BookkeepingEntry.OwnerWithdrawal) entry;
    validateOwnerWithdrawal(violations, accounts, event, selectorField, selectorValue);
  }

  private static void validateSale(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.Sale event,
      String selectorField,
      String selectorValue) {
    requireDistinctRoleAccounts(
        violations,
        selectorField,
        selectorValue,
        event.cashAccountCode(),
        "cashAccountCode",
        event.revenueAccountCode(),
        "revenueAccountCode");
    requireAccountType(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.cashAccountCode(),
        "cashAccountCode",
        AccountType.ASSET);
    requireCashAndCashEquivalent(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.cashAccountCode(),
        "cashAccountCode");
    requireAccountType(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.revenueAccountCode(),
        "revenueAccountCode",
        AccountType.REVENUE);
  }

  private static void validateExpense(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.Expense event,
      String selectorField,
      String selectorValue) {
    requireDistinctRoleAccounts(
        violations,
        selectorField,
        selectorValue,
        event.expenseAccountCode(),
        "expenseAccountCode",
        event.cashAccountCode(),
        "cashAccountCode");
    requireAccountType(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.expenseAccountCode(),
        "expenseAccountCode",
        AccountType.EXPENSE);
    requireAccountType(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.cashAccountCode(),
        "cashAccountCode",
        AccountType.ASSET);
    requireCashAndCashEquivalent(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.cashAccountCode(),
        "cashAccountCode");
  }

  private static void validateOwnerContribution(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.OwnerContribution event,
      String selectorField,
      String selectorValue) {
    requireDistinctRoleAccounts(
        violations,
        selectorField,
        selectorValue,
        event.cashAccountCode(),
        "cashAccountCode",
        event.equityAccountCode(),
        "equityAccountCode");
    requireAccountType(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.cashAccountCode(),
        "cashAccountCode",
        AccountType.ASSET);
    requireCashAndCashEquivalent(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.cashAccountCode(),
        "cashAccountCode");
    requireAccountType(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.equityAccountCode(),
        "equityAccountCode",
        AccountType.EQUITY);
    requireFinancialPositionClassification(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.equityAccountCode(),
        "equityAccountCode",
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
  }

  private static void validateOwnerWithdrawal(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry.OwnerWithdrawal event,
      String selectorField,
      String selectorValue) {
    requireDistinctRoleAccounts(
        violations,
        selectorField,
        selectorValue,
        event.equityAccountCode(),
        "equityAccountCode",
        event.cashAccountCode(),
        "cashAccountCode");
    requireAccountType(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.equityAccountCode(),
        "equityAccountCode",
        AccountType.EQUITY);
    requireFinancialPositionClassification(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.equityAccountCode(),
        "equityAccountCode",
        FinancialPositionLineClassification.EQUITY_WITHDRAWAL);
    requireAccountType(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.cashAccountCode(),
        "cashAccountCode",
        AccountType.ASSET);
    requireCashAndCashEquivalent(
        violations,
        selectorField,
        selectorValue,
        accounts,
        event.cashAccountCode(),
        "cashAccountCode");
  }

  private static void requireDistinctRoleAccounts(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      AccountCode firstAccountCode,
      String firstField,
      AccountCode secondAccountCode,
      String secondField) {
    if (!firstAccountCode.equals(secondAccountCode)) {
      return;
    }
    violations.add(
        BookkeepingEntrySemanticsViolationFactory.distinctRoleAccountsRequired(
            selectorField, selectorValue, firstField, secondField, firstAccountCode));
  }

  private static void requireAccountType(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field,
      AccountType expectedAccountType) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null || account.accountType() == expectedAccountType) {
      return;
    }
    violations.add(
        BookkeepingEntrySemanticsViolationFactory.accountTypeMismatch(
            selectorField,
            selectorValue,
            field,
            accountCode,
            expectedAccountType,
            account.accountType()));
  }

  private static void requireFinancialPositionClassification(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field,
      FinancialPositionLineClassification expectedClassification) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null) {
      return;
    }
    FinancialPositionLineClassification actualClassification =
        account.accountTaxonomy().financialPositionLineClassification().orElse(null);
    if (actualClassification == expectedClassification) {
      return;
    }
    violations.add(
        BookkeepingEntrySemanticsViolationFactory.financialPositionClassificationMismatch(
            selectorField,
            selectorValue,
            field,
            accountCode,
            expectedClassification,
            actualClassification));
  }

  private static void requireCashAndCashEquivalent(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null || account.cashAndCashEquivalent()) {
      return;
    }
    violations.add(
        BookkeepingEntrySemanticsViolationFactory.cashFlowAssetClassificationMismatch(
            selectorField,
            selectorValue,
            field,
            accountCode,
            CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT,
            account.accountTaxonomy().cashFlowAssetClassification().orElse(null)));
  }
}
