package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.account;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.accrualBookIdentity;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.cashExpense;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.cashRevenue;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.costOfSalesAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.creditExpense;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.creditSale;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.duplicateCashExpense;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.duplicateCashRevenue;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.duplicateEquityContribution;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.duplicateEquityWithdrawal;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.equityAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.equityContribution;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.equityWithdrawal;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.inventoryAssetAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.operatingExpenseAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.payableAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.payment;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.paymentWithoutAdjunct;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.receipt;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.receiptWithoutAdjunct;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.receivableAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.settlementAdjunctAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.tradingAccrualBookIdentity;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.tradingCashBookIdentity;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.command;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for typed-entry semantics validation at the application boundary. */
class PostEntrySemanticsPolicyTest {
  @Test
  void rejectionFor_acceptsAllSupportedTypedKernelEntriesWhenAccountsAndEvidenceMatch() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE),
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                new AccountCode("3210"),
                equityAccount("3210", FinancialPositionLineClassification.EQUITY_WITHDRAWAL)));

    assertTrue(policy.rejectionFor(cashRevenue("cash-ok", "cash-receipt"), book).isEmpty());
    assertTrue(policy.rejectionFor(cashExpense("expense-ok", "expense-receipt"), book).isEmpty());
    assertTrue(
        policy
            .rejectionFor(equityContribution("equity-contribution-ok", "owner-contribution"), book)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(equityWithdrawal("equity-withdrawal-ok", "owner-withdrawal"), book)
            .isEmpty());
  }

  @Test
  void rejectionFor_reportsTypedEntryDoctrineViolationsAcrossAllSupportedTypedEntries() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.LIABILITY),
                new AccountCode("2000"),
                account("2000", AccountType.ASSET),
                new AccountCode("3000"),
                account("3000", AccountType.REVENUE),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.OTHER_EQUITY),
                new AccountCode("3210"),
                equityAccount("3210", FinancialPositionLineClassification.RESULT_HOLDING)));

    assertViolationCodes(
        policy.rejectionFor(cashRevenue("cash-bad", "invoice"), book),
        "account-type-mismatch",
        "cash-flow-asset-classification-mismatch",
        "account-type-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(cashExpense("expense-bad", "invoice"), book),
        "account-type-mismatch",
        "account-type-mismatch",
        "cash-flow-asset-classification-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(equityContribution("equity-contribution-bad", "invoice"), book),
        "account-type-mismatch",
        "cash-flow-asset-classification-mismatch",
        "financial-position-classification-mismatch",
        "source-document-type-not-accepted");
    assertViolationCodes(
        policy.rejectionFor(equityWithdrawal("equity-withdrawal-bad", "invoice"), book),
        "financial-position-classification-mismatch",
        "account-type-mismatch",
        "cash-flow-asset-classification-mismatch",
        "source-document-type-not-accepted");
  }

  @Test
  void rejectionFor_enforcesTradingSaleInventoryReliefByBookTemplate() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    InventoryRelief inventoryRelief =
        new InventoryRelief(
            new AccountCode("1400"),
            new AccountCode("5000"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"));
    BookkeepingEntry.SaleSettled saleWithInventoryRelief =
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            inventoryRelief,
            null,
            null,
            null,
            null);
    BookkeepingEntry.SaleSettled saleWithoutInventoryRelief =
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null);
    BookkeepingEntry.SaleOnCredit creditSaleWithInventoryRelief =
        new BookkeepingEntry.SaleOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1100"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            inventoryRelief,
            null,
            null,
            null,
            null);
    BookkeepingEntry.SaleOnCredit creditSaleWithoutInventoryRelief =
        new BookkeepingEntry.SaleOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1100"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null);
    Map<AccountCode, RegisteredAccount> tradingAccounts =
        Map.of(
            new AccountCode("1000"), account("1000", AccountType.ASSET),
            new AccountCode("1100"), receivableAccount("1100"),
            new AccountCode("1400"), inventoryAssetAccount("1400"),
            new AccountCode("2000"), account("2000", AccountType.REVENUE),
            new AccountCode("5000"), costOfSalesAccount("5000"));

    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                saleWithInventoryRelief,
                generatedEvidence("service-sale-with-relief", "cash-receipt"),
                requestProvenance("service-sale-with-relief"),
                SourceChannel.CLI),
            new PostingValidationStoreDouble(tradingAccounts)),
        "inventory-relief-requires-trading-book");
    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                saleWithoutInventoryRelief,
                generatedEvidence("trading-sale-without-relief", "cash-receipt"),
                requestProvenance("trading-sale-without-relief"),
                SourceChannel.CLI),
            new PostingValidationStoreDouble(tradingCashBookIdentity(), tradingAccounts)),
        "trading-sale-requires-inventory-relief");
    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    saleWithInventoryRelief,
                    generatedEvidence("trading-sale-with-relief", "cash-receipt"),
                    requestProvenance("trading-sale-with-relief"),
                    SourceChannel.CLI),
                new PostingValidationStoreDouble(tradingCashBookIdentity(), tradingAccounts))
            .isEmpty());
    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                creditSaleWithInventoryRelief,
                generatedEvidence("service-credit-sale-with-relief", "invoice"),
                requestProvenance("service-credit-sale-with-relief"),
                SourceChannel.CLI),
            new PostingValidationStoreDouble(accrualBookIdentity(), tradingAccounts)),
        "inventory-relief-requires-trading-book");
    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                creditSaleWithoutInventoryRelief,
                generatedEvidence("trading-credit-sale-without-relief", "invoice"),
                requestProvenance("trading-credit-sale-without-relief"),
                SourceChannel.CLI),
            new PostingValidationStoreDouble(tradingAccrualBookIdentity(), tradingAccounts)),
        "trading-sale-requires-inventory-relief");
    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    creditSaleWithInventoryRelief,
                    generatedEvidence("trading-credit-sale-with-relief", "invoice"),
                    requestProvenance("trading-credit-sale-with-relief"),
                    SourceChannel.CLI),
                new PostingValidationStoreDouble(tradingAccrualBookIdentity(), tradingAccounts))
            .isEmpty());
  }

  @Test
  void rejectionFor_skipsSemanticsChecksForAdministrativeEntriesOutsideTypedKernelEvents() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());
    PostingValidationStoreDouble reversalBook =
        new PostingValidationStoreDouble(
            ExecutorAccountingTestSupport.bookIdentity(),
            Map.of(),
            Map.of(
                new dev.erst.fingrind.core.PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                PostingApplicationServiceTestSupport.existingPosting("posting-1", "prior")));

    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.OpeningPosition(
                        LocalDate.parse("2026-04-07"),
                        List.of(
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                new AccountCode("1000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                                null),
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                new AccountCode("2000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                                null))),
                    generatedEvidence("opening-balance", "opening-balance"),
                    requestProvenance("opening-balance"),
                    SourceChannel.CLI),
                emptyBook)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(
                command(
                    "reversal-adjustment",
                    PostingApplicationServiceTestSupport.reversalReference("posting-1"),
                    Optional.of(new dev.erst.fingrind.core.ReversalReason("full reversal")),
                    PostingApplicationServiceTestSupport.reversalJournalEntry()),
                reversalBook)
            .isEmpty());
  }

  @Test
  void rejectionFor_leavesUndeclaredAccountsToAccountStateValidation() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();

    assertTrue(
        policy
            .rejectionFor(
                equityContribution("undeclared-accounts", "owner-contribution"),
                new PostingValidationStoreDouble(Map.of()))
            .isEmpty());
  }

  @Test
  void rejectionFor_acceptsCallerAuthoredSourceDocumentTypesForPatternOnlyEntries() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());
    PostingValidationStoreDouble reversalBook =
        new PostingValidationStoreDouble(
            ExecutorAccountingTestSupport.bookIdentity(),
            Map.of(),
            Map.of(
                new dev.erst.fingrind.core.PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                PostingApplicationServiceTestSupport.existingPosting("posting-1", "prior")));

    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.OpeningPosition(
                        LocalDate.parse("2026-04-07"),
                        List.of(
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                new AccountCode("1000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                                null),
                            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                                new AccountCode("2000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                                null))),
                    generatedEvidence("open-pattern", "field-note"),
                    requestProvenance("open-pattern"),
                    SourceChannel.CLI),
                emptyBook)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.Reversal(
                        LocalDate.parse("2026-04-07"),
                        new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                            PostingApplicationServiceTestSupport.reversalReference("posting-1")
                                .orElseThrow(),
                            new dev.erst.fingrind.core.ReversalReason("full reversal")),
                        null,
                        null),
                    generatedEvidence("reversal-pattern", "operator-annotation"),
                    requestProvenance("reversal-pattern"),
                    SourceChannel.CLI),
                reversalBook)
            .isEmpty());
  }

  @Test
  void roleSemanticsValidate_treatsOpeningAndReversalEntriesAsNoOps() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();

    PostEntryRoleAccountSemantics.validate(
        violations,
        Map.of(),
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00")),
                    null),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("2000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00")),
                    null))),
        "entryKind",
        "OPENING_POSITION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        Map.of(),
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                PostingApplicationServiceTestSupport.reversalReference("posting-1").orElseThrow(),
                new dev.erst.fingrind.core.ReversalReason("full reversal")),
            null,
            null),
        "entryKind",
        "REVERSAL");

    assertTrue(violations.isEmpty());
  }

  @Test
  void rejectionFor_rejectsTypedEntriesThatReuseOneAccountAcrossBothBusinessRoles() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    assertSingleViolation(
        policy.rejectionFor(duplicateCashRevenue("duplicate-cash-revenue"), emptyBook),
        "distinct-role-accounts-required");
    assertSingleViolation(
        policy.rejectionFor(duplicateCashExpense("duplicate-cash-expense"), emptyBook),
        "distinct-role-accounts-required");
    assertSingleViolation(
        policy.rejectionFor(
            duplicateEquityContribution("duplicate-equity-contribution"), emptyBook),
        "distinct-role-accounts-required");
    assertSingleViolation(
        policy.rejectionFor(duplicateEquityWithdrawal("duplicate-equity-withdrawal"), emptyBook),
        "distinct-role-accounts-required");
  }

  @Test
  void rejectionFor_acceptsDirectJournalsWithCallerAuthoredEvidenceTypesWhenTheyMoveBalances() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2100"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "4.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2200"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "6.00")))),
                null),
            generatedEvidence("direct-journal", "operator-note"),
            requestProvenance("direct-journal"),
            SourceChannel.CLI);

    assertTrue(policy.rejectionFor(command, emptyBook).isEmpty());
  }

  @Test
  void rejectionFor_rejectsEconomicallyNullDirectJournalsAfterPerAccountNetting() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble emptyBook = new PostingValidationStoreDouble(Map.of());

    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")))),
                null),
            generatedEvidence("economic-null-journal", "operator-note"),
            requestProvenance("economic-null-journal"),
            SourceChannel.CLI);

    assertSingleViolation(policy.rejectionFor(command, emptyBook), "economic-null-journal");
  }

  @Test
  void rejectionFor_rejectsDirectJournalsThatNeverTouchDeclaredCashAccounts() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.OTHER_EQUITY)));

    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("3000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("3200"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")))),
                null),
            generatedEvidence("non-cash-direct-journal", "operator-note"),
            requestProvenance("non-cash-direct-journal"),
            SourceChannel.CLI);

    assertViolationCodes(policy.rejectionFor(command, book), "raw-journal-requires-cash-line");
  }

  @Test
  void rejectionFor_reportsEvidenceClassConflictsForSettledAndCreditEventFamilies() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble settledBook =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE)));
    PostingValidationStoreDouble creditBook =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1100"),
                receivableAccount("1100"),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE)));

    assertContainsViolationCode(
        policy.rejectionFor(cashRevenue("settled-invoice-conflict", "invoice"), settledBook),
        "evidence-class-conflict");
    assertContainsViolationCode(
        policy.rejectionFor(creditSale("credit-cash-conflict", "cash-receipt"), creditBook),
        "evidence-class-conflict");
  }

  @Test
  void rejectionFor_preservesTaxSemanticsFailuresWithoutResolvingTheJournal() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE)));

    assertContainsViolationCode(
        policy.rejectionFor(
            new PostEntryCommand(
                new BookkeepingEntry.SaleSettled(
                    LocalDate.parse("2026-04-07"),
                    new AccountCode("1000"),
                    new AccountCode("2000"),
                    MonetaryAmount.of(Money.parse("EUR", "10.00")),
                    null,
                    null,
                    null,
                    new dev.erst.fingrind.contract.tax.TaxSelection(
                        new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
                        new dev.erst.fingrind.contract.tax.TaxCode("missing-code")),
                    null),
                generatedEvidence("tax-semantic-failure", "cash-receipt"),
                requestProvenance("tax-semantic-failure"),
                SourceChannel.CLI),
            book),
        "unknown-tax-registration");
  }

  @Test
  void rejectionFor_acceptsCreditAndSettlementEntriesWhenAccrualDoctrineAndRolesMatch() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("1100"),
                receivableAccount("1100"),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE),
                new AccountCode("2100"),
                payableAccount("2100"),
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE),
                new AccountCode("5600"),
                settlementAdjunctAccount("5600")));

    assertTrue(policy.rejectionFor(creditSale("credit-sale-ok", "invoice"), book).isEmpty());
    assertTrue(policy.rejectionFor(creditExpense("credit-expense-ok", "bill"), book).isEmpty());
    assertTrue(
        policy
            .rejectionFor(receipt("receipt-ok", "bank-deposit", new AccountCode("5600")), book)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(
                payment("payment-ok", "bank-payment-confirmation", new AccountCode("5600")), book)
            .isEmpty());
  }

  @Test
  void rejectionFor_cashDoctrineRejectsAccrualOnlyCreditAndSettlementVerbs() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("1100"),
                receivableAccount("1100"),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE),
                new AccountCode("2100"),
                payableAccount("2100"),
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE)));

    assertSingleViolation(
        policy.rejectionFor(creditSale("credit-sale-cash-basis", "invoice"), book),
        "verb-requires-receivable-role");
    assertSingleViolation(
        policy.rejectionFor(receiptWithoutAdjunct("receipt-cash-basis", "bank-deposit"), book),
        "verb-requires-receivable-role");
    assertSingleViolation(
        policy.rejectionFor(creditExpense("credit-expense-cash-basis", "bill"), book),
        "verb-requires-payable-role");
    assertSingleViolation(
        policy.rejectionFor(
            paymentWithoutAdjunct("payment-cash-basis", "bank-payment-confirmation"), book),
        "verb-requires-payable-role");
  }

  @Test
  void rejectionFor_rejectsOpeningWindowBlockedAccountsAndSettlementAdjunctRoleMismatches() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble openingWindowBook =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE)));
    PostingValidationStoreDouble wrongSettlementRoleBook =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("1100"),
                receivableAccount("1100"),
                new AccountCode("5601"),
                operatingExpenseAccount("5601")));

    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                new BookkeepingEntry.OpeningPosition(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            MonetaryAmount.of(Money.parse("EUR", "10.00")),
                            null),
                        new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                            new AccountCode("2000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            MonetaryAmount.of(Money.parse("EUR", "10.00")),
                            null))),
                generatedEvidence("opening-window-blocked", "opening-balance"),
                requestProvenance("opening-window-blocked"),
                SourceChannel.CLI),
            openingWindowBook),
        "opening-window-account-not-permitted");
    assertSingleViolation(
        policy.rejectionFor(
            receipt("receipt-adjunct-mismatch", "bank-deposit", new AccountCode("5601")),
            wrongSettlementRoleBook),
        "account-role-mismatch");
  }

  @Test
  void rejectionFor_rejectsRawJournalsThatShadowTypedEventsAndBundleOperationalEvents() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble book =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("1100"),
                receivableAccount("1100"),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE)));

    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                new BookkeepingEntry.DirectJournal(
                    new dev.erst.fingrind.core.JournalEntry(
                        LocalDate.parse("2026-04-07"),
                        List.of(
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("1000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                Money.parse("EUR", "10.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("2000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "10.00")))),
                    null),
                generatedEvidence("shadow-typed-event", "cash-receipt"),
                requestProvenance("shadow-typed-event"),
                SourceChannel.CLI),
            book),
        "raw-journal-shadows-typed-event");
    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                new BookkeepingEntry.DirectJournal(
                    new dev.erst.fingrind.core.JournalEntry(
                        LocalDate.parse("2026-04-07"),
                        List.of(
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("1000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                Money.parse("EUR", "150.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("1100"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "100.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("2000"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "50.00")))),
                    null),
                generatedEvidence("compound-operational", "cash-receipt"),
                requestProvenance("compound-operational"),
                SourceChannel.CLI),
            book),
        "raw-journal-bundles-operational-events");
  }

  @Test
  void roleSemanticsValidate_coversAllBranchesAndIgnoresMissingSettlementAdjunctAccounts() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    Map<AccountCode, RegisteredAccount> accounts =
        Map.of(
            new AccountCode("1000"),
            account("1000", AccountType.ASSET),
            new AccountCode("1100"),
            receivableAccount("1100"),
            new AccountCode("2000"),
            account("2000", AccountType.REVENUE),
            new AccountCode("2100"),
            payableAccount("2100"),
            new AccountCode("3000"),
            account("3000", AccountType.EXPENSE),
            new AccountCode("3200"),
            equityAccount("3200", FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
            new AccountCode("3210"),
            equityAccount("3210", FinancialPositionLineClassification.EQUITY_WITHDRAWAL));

    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.DirectJournal(
            new dev.erst.fingrind.core.JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("1000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("2000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            null),
        "entryKind",
        "DIRECT_JOURNAL");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null),
        "entryKind",
        "SALE_SETTLED");

    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.SaleOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1100"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null),
        "entryKind",
        "SALE_ON_CREDIT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.ExpenseOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3000"),
            new AccountCode("2100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null),
        "entryKind",
        "EXPENSE_ON_CREDIT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.OwnerContribution(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("3200"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        "entryKind",
        "OWNER_CONTRIBUTION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.OwnerWithdrawal(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3210"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        "entryKind",
        "OWNER_WITHDRAWAL");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.Receipt(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("1100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            new SettlementAdjunct(
                new AccountCode("9999"), MonetaryAmount.of(Money.parse("EUR", "1.00")))),
        "entryKind",
        "RECEIPT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.Payment(
            LocalDate.parse("2026-04-07"),
            new AccountCode("2100"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        "entryKind",
        "PAYMENT");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00")),
                    null))),
        "entryKind",
        "OPENING_POSITION");
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                PostingApplicationServiceTestSupport.reversalReference("posting-1").orElseThrow(),
                new dev.erst.fingrind.core.ReversalReason("full reversal")),
            null,
            null),
        "entryKind",
        "REVERSAL");

    assertTrue(violations.isEmpty());
  }

  @Test
  void resolvedJournalSupport_rejectsMissingDeclaredAccounts() {
    NullPointerException failure =
        assertThrows(
            NullPointerException.class,
            () ->
                ResolvedJournalSupport.resolve(
                    new BookkeepingEntry.SaleSettled(
                        LocalDate.parse("2026-04-07"),
                        new AccountCode("1000"),
                        new AccountCode("2000"),
                        MonetaryAmount.of(Money.parse("EUR", "10.00")),
                        null,
                        null,
                        null,
                        null,
                        null),
                    generatedEvidence("missing-account", "cash-receipt"),
                    Map.of()));

    assertEquals("Missing declared account for 1000", failure.getMessage());
  }

  private static void assertViolationCodes(
      Optional<BookkeepingPostingRejection> rejection, String... expectedCodes) {
    BookkeepingPostingRejection.EntrySemanticsViolations violations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class, rejection.orElseThrow());
    assertEquals(expectedCodes.length, violations.violations().size());
    for (int index = 0; index < expectedCodes.length; index++) {
      assertEquals(expectedCodes[index], violations.violations().get(index).code());
    }
  }

  private static void assertSingleViolation(
      Optional<BookkeepingPostingRejection> rejection, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations violations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class, rejection.orElseThrow());
    assertEquals(1, violations.violations().size());
    assertEquals(expectedCode, violations.violations().getFirst().code());
  }

  private static void assertContainsViolationCode(
      Optional<BookkeepingPostingRejection> rejection, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations violations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class, rejection.orElseThrow());
    assertTrue(
        violations.violations().stream()
            .anyMatch(violation -> expectedCode.equals(violation.code())),
        expectedCode);
  }
}
