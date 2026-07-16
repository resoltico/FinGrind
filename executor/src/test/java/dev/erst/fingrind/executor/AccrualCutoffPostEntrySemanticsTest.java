package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises accrual cut-off commands through the application-boundary semantic policy. */
class AccrualCutoffPostEntrySemanticsTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode PREPAID_EXPENSE = new AccountCode("1410");
  private static final AccountCode EXPENSE = new AccountCode("5100");
  private static final AccountCode DEFERRED_REVENUE = new AccountCode("2300");
  private static final AccountCode REVENUE = new AccountCode("4100");
  private static final AccountCode ACCRUED_EXPENSE = new AccountCode("2400");

  @Test
  void accrualBook_admitsEveryTypedCutoffCommandAndResolvesItsApplicationAccounts() {
    AccrualCutoffId prepaymentId = new AccrualCutoffId("insurance-2026");
    AccrualCutoffId deferredRevenueId = new AccrualCutoffId("support-contract-2026");
    AccrualCutoffId accruedExpenseId = new AccrualCutoffId("contractor-2026");
    Map<AccountCode, RegisteredAccount> accounts = accounts();

    assertAccepted(
        command(prepayment(prepaymentId), "prepayment", "prepayment-invoice"),
        new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
            PostEntrySemanticsPolicyTestSupport.accrualBookIdentity(), accounts));
    assertAccepted(
        command(deferredRevenue(deferredRevenueId), "deferred-revenue", "customer-contract"),
        new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
            PostEntrySemanticsPolicyTestSupport.accrualBookIdentity(), accounts));
    assertAccepted(
        command(accruedExpense(accruedExpenseId), "accrued-expense", "accrual-schedule"),
        new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
            PostEntrySemanticsPolicyTestSupport.accrualBookIdentity(), accounts));

    assertAccepted(
        command(recognition(prepaymentId), "prepayment-recognition", "prepayment-schedule"),
        validationBook(accounts, Map.of(prepaymentId, prepaymentRecord(prepaymentId))));
    assertAccepted(
        command(
            recognition(deferredRevenueId),
            "deferred-revenue-recognition",
            "revenue-recognition-schedule"),
        validationBook(
            accounts, Map.of(deferredRevenueId, deferredRevenueRecord(deferredRevenueId))));
    assertAccepted(
        command(settlement(accruedExpenseId), "accrued-expense-settlement", "cash-disbursement"),
        validationBook(accounts, Map.of(accruedExpenseId, accruedExpenseRecord(accruedExpenseId))));
  }

  @Test
  void cashBasisBook_rejectsAccrualCutoffCommandsBeforeJournalResolution() {
    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        (BookkeepingPostingRejection.EntrySemanticsViolations)
            PostEntrySemanticsPolicy.currentKernel()
                .rejectionFor(
                    command(
                        prepayment(new AccrualCutoffId("cash-basis-prepayment")),
                        "cash-basis-prepayment",
                        "prepayment-invoice"),
                    new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
                        accounts()))
                .orElseThrow();

    assertEquals("accrual-cutoff-requires-accrual-basis", rejection.violations().getFirst().code());
  }

  private static void assertAccepted(
      PostEntryCommand command,
      PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble book) {
    assertTrue(PostEntrySemanticsPolicy.currentKernel().rejectionFor(command, book).isEmpty());
  }

  private static PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble validationBook(
      Map<AccountCode, RegisteredAccount> accounts,
      Map<AccrualCutoffId, AccrualCutoffRecord> cutoffsById) {
    return new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
        PostEntrySemanticsPolicyTestSupport.accrualBookIdentity(), accounts, Map.of(), cutoffsById);
  }

  private static PostEntryCommand command(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry,
      String idempotencyKey,
      String sourceDocumentType) {
    return new PostEntryCommand(
        entry,
        generatedEvidence(idempotencyKey, sourceDocumentType),
        requestProvenance(idempotencyKey),
        SourceChannel.CLI);
  }

  private static AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.Prepayment(
        LocalDate.parse("2026-04-07"),
        accrualCutoffId,
        PREPAID_EXPENSE,
        EXPENSE,
        CASH,
        amount("100.00"),
        interval());
  }

  private static AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
        LocalDate.parse("2026-04-07"),
        accrualCutoffId,
        CASH,
        DEFERRED_REVENUE,
        REVENUE,
        amount("100.00"),
        interval());
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
        LocalDate.parse("2026-04-07"), accrualCutoffId, EXPENSE, ACCRUED_EXPENSE, amount("100.00"));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
        LocalDate.parse("2026-04-15"), accrualCutoffId, amount("25.00"), null);
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
        LocalDate.parse("2026-04-15"), accrualCutoffId, CASH, amount("25.00"), null);
  }

  private static AccrualCutoffRecord.Prepayment prepaymentRecord(AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffRecord.Prepayment(
        accrualCutoffId,
        LocalDate.parse("2026-04-07"),
        PREPAID_EXPENSE,
        EXPENSE,
        Money.parse("EUR", "100.00"),
        interval(),
        Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
        Optional.empty());
  }

  private static AccrualCutoffRecord.DeferredRevenue deferredRevenueRecord(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffRecord.DeferredRevenue(
        accrualCutoffId,
        LocalDate.parse("2026-04-07"),
        DEFERRED_REVENUE,
        REVENUE,
        Money.parse("EUR", "100.00"),
        interval(),
        Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
        Optional.empty());
  }

  private static AccrualCutoffRecord.AccruedExpense accruedExpenseRecord(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffRecord.AccruedExpense(
        accrualCutoffId,
        LocalDate.parse("2026-04-07"),
        ACCRUED_EXPENSE,
        EXPENSE,
        Money.parse("EUR", "100.00"),
        Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
        Optional.empty());
  }

  private static AccrualCutoffRecognitionInterval interval() {
    return new AccrualCutoffRecognitionInterval(
        LocalDate.parse("2026-04-07"), LocalDate.parse("2026-12-31"));
  }

  private static MonetaryAmount amount(String decimalAmount) {
    return MonetaryAmount.of(Money.parse("EUR", decimalAmount));
  }

  private static Map<AccountCode, RegisteredAccount> accounts() {
    return Map.of(
        CASH,
        account(CASH, "Cash", AccountType.ASSET, accountTaxonomy(AccountType.ASSET)),
        PREPAID_EXPENSE,
        account(
            PREPAID_EXPENSE,
            "Prepaid insurance",
            AccountType.ASSET,
            financialPositionTaxonomy(FinancialPositionLineClassification.PREPAID_EXPENSE)),
        EXPENSE,
        account(
            EXPENSE,
            "Insurance expense",
            AccountType.EXPENSE,
            accountTaxonomy(AccountType.EXPENSE)),
        DEFERRED_REVENUE,
        account(
            DEFERRED_REVENUE,
            "Deferred support revenue",
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.DEFERRED_REVENUE)),
        REVENUE,
        account(
            REVENUE, "Support revenue", AccountType.REVENUE, accountTaxonomy(AccountType.REVENUE)),
        ACCRUED_EXPENSE,
        account(
            ACCRUED_EXPENSE,
            "Accrued contractor expense",
            AccountType.LIABILITY,
            financialPositionTaxonomy(FinancialPositionLineClassification.ACCRUED_EXPENSE)));
  }

  private static RegisteredAccount account(
      AccountCode accountCode,
      String name,
      AccountType accountType,
      dev.erst.fingrind.core.AccountTaxonomy taxonomy) {
    return registeredAccount(
        accountCode, new AccountName(name), accountType, taxonomy, true, DECLARED_AT);
  }
}
