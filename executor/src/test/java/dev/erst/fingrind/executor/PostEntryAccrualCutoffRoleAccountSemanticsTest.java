package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Proves the accrual cut-off role contract for unresolved and executor-resolved entries. */
class PostEntryAccrualCutoffRoleAccountSemanticsTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  private static final AccrualCutoffId CUTOFF_ID = new AccrualCutoffId("cutoff-2026-04");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode PREPAID_EXPENSE = new AccountCode("1410");
  private static final AccountCode EXPENSE = new AccountCode("5000");
  private static final AccountCode DEFERRED_REVENUE = new AccountCode("2200");
  private static final AccountCode REVENUE = new AccountCode("4000");
  private static final AccountCode ACCRUED_EXPENSE = new AccountCode("2100");

  @Test
  void validate_admitsEveryAccrualCutoffAccountShape() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    List<AccrualCutoffBookkeepingEntryVariants> entries =
        List.of(
            new AccrualCutoffBookkeepingEntryVariants.Prepayment(
                EFFECTIVE_DATE,
                CUTOFF_ID,
                PREPAID_EXPENSE,
                EXPENSE,
                CASH,
                amount("100.00"),
                interval()),
            new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
                EFFECTIVE_DATE,
                CUTOFF_ID,
                CASH,
                DEFERRED_REVENUE,
                REVENUE,
                amount("100.00"),
                interval()),
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
                EFFECTIVE_DATE, CUTOFF_ID, EXPENSE, ACCRUED_EXPENSE, amount("100.00")),
            new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                EFFECTIVE_DATE, CUTOFF_ID, amount("25.00"), null),
            recognition(AccrualCutoffKind.PREPAYMENT, EXPENSE, PREPAID_EXPENSE),
            recognition(AccrualCutoffKind.DEFERRED_REVENUE, DEFERRED_REVENUE, REVENUE),
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                EFFECTIVE_DATE, CUTOFF_ID, CASH, amount("25.00"), null),
            settlement());

    for (AccrualCutoffBookkeepingEntryVariants entry : entries) {
      PostEntryRoleAccountSemantics.validate(
          violations, accounts(), entry, "entryKind", entry.entryKind().wireValue());
    }

    assertEquals(List.of(), violations);
  }

  @Test
  void validate_rejectsResolvedCutoffKindsOutsideTheirOwningVerb() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();

    assertThrows(
        IllegalStateException.class,
        () ->
            PostEntryAccrualCutoffRoleAccountSemantics.validate(
                violations,
                accounts(),
                recognition(AccrualCutoffKind.ACCRUED_EXPENSE, EXPENSE, PREPAID_EXPENSE),
                "entryKind",
                "ACCRUAL_CUTOFF_RECOGNITION"));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostEntryAccrualCutoffRoleAccountSemantics.validate(
                violations,
                accounts(),
                new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                    EFFECTIVE_DATE,
                    CUTOFF_ID,
                    CASH,
                    amount("25.00"),
                    new ResolvedAccrualCutoffApplication(
                        AccrualCutoffKind.PREPAYMENT,
                        AccrualCutoffApplicationKind.SETTLEMENT,
                        EXPENSE,
                        PREPAID_EXPENSE)),
                "entryKind",
                "ACCRUED_EXPENSE_SETTLEMENT"));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition(
      AccrualCutoffKind kind, AccountCode debitAccountCode, AccountCode creditAccountCode) {
    return new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
        EFFECTIVE_DATE,
        CUTOFF_ID,
        amount("25.00"),
        new ResolvedAccrualCutoffApplication(
            kind, AccrualCutoffApplicationKind.RECOGNITION, debitAccountCode, creditAccountCode));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement() {
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
        EFFECTIVE_DATE,
        CUTOFF_ID,
        CASH,
        amount("25.00"),
        new ResolvedAccrualCutoffApplication(
            AccrualCutoffKind.ACCRUED_EXPENSE,
            AccrualCutoffApplicationKind.SETTLEMENT,
            ACCRUED_EXPENSE,
            CASH));
  }

  private static Map<AccountCode, RegisteredAccount> accounts() {
    return Map.of(
        CASH,
        account(CASH, "Cash", AccountType.ASSET, accountTaxonomy(AccountType.ASSET)),
        PREPAID_EXPENSE,
        account(
            PREPAID_EXPENSE,
            "Prepaid expense",
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.PREPAID_EXPENSE)),
        EXPENSE,
        account(EXPENSE, "Expense", AccountType.EXPENSE, accountTaxonomy(AccountType.EXPENSE)),
        DEFERRED_REVENUE,
        account(
            DEFERRED_REVENUE,
            "Deferred revenue",
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.DEFERRED_REVENUE)),
        REVENUE,
        account(REVENUE, "Revenue", AccountType.REVENUE, accountTaxonomy(AccountType.REVENUE)),
        ACCRUED_EXPENSE,
        account(
            ACCRUED_EXPENSE,
            "Accrued expense",
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.ACCRUED_EXPENSE)));
  }

  private static RegisteredAccount account(
      AccountCode accountCode,
      String accountName,
      AccountType accountType,
      dev.erst.fingrind.core.AccountTaxonomy accountTaxonomy) {
    return registeredAccount(
        accountCode, new AccountName(accountName), accountType, accountTaxonomy, true, DECLARED_AT);
  }

  private static AccrualCutoffRecognitionInterval interval() {
    return new AccrualCutoffRecognitionInterval(EFFECTIVE_DATE, LocalDate.parse("2026-05-31"));
  }

  private static MonetaryAmount amount(String decimalAmount) {
    return MonetaryAmount.of(Money.parse("EUR", decimalAmount));
  }
}
