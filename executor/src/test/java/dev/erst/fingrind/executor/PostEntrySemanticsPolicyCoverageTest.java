package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.profitAndLossTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.inventoryAssetAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.tradingAccrualBookIdentity;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused coverage for accrual-only verbs, raw-admission rules, and role-account branches. */
class PostEntrySemanticsPolicyCoverageTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");

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
  void rejectionFor_cashDoctrineRejectsAccrualOnlyVerbsBeforeMissingAccountLookup() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble cashBookWithoutAccrualAccounts =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE),
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE)));

    assertSingleViolation(
        policy.rejectionFor(
            creditSale("credit-sale-no-ar", "invoice"), cashBookWithoutAccrualAccounts),
        "verb-requires-receivable-role");
    assertSingleViolation(
        policy.rejectionFor(
            receiptWithoutAdjunct("receipt-no-ar", "bank-deposit"), cashBookWithoutAccrualAccounts),
        "verb-requires-receivable-role");
    assertSingleViolation(
        policy.rejectionFor(
            creditExpense("credit-expense-no-ap", "bill"), cashBookWithoutAccrualAccounts),
        "verb-requires-payable-role");
    assertSingleViolation(
        policy.rejectionFor(
            paymentWithoutAdjunct("payment-no-ap", "bank-payment-confirmation"),
            cashBookWithoutAccrualAccounts),
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
  void rejectionFor_rejectsInventoryOpeningBalancesThatOmitQuantity() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble inventoryOpeningBook =
        new PostingValidationStoreDouble(
            tradingAccrualBookIdentity(),
            Map.of(
                new AccountCode("inventory"),
                inventoryAssetAccount("inventory"),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.EQUITY_CONTRIBUTION)));

    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                new BookkeepingEntry.OpeningPosition(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                            new AccountCode("inventory"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            MonetaryAmount.of(Money.parse("EUR", "10.00")),
                            null),
                        new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                            new AccountCode("3200"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            MonetaryAmount.of(Money.parse("EUR", "10.00")),
                            null))),
                generatedEvidence("opening-inventory", "opening-balance"),
                requestProvenance("opening-inventory"),
                SourceChannel.CLI),
            inventoryOpeningBook),
        "opening-inventory-requires-quantity");
  }

  @Test
  void rejectionFor_rejectsRawJournalInventoryMovementsBeforeAmountOnlyInventoryCanReachCommit() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble inventoryJournalBook =
        new PostingValidationStoreDouble(
            tradingAccrualBookIdentity(),
            Map.of(
                new AccountCode("inventory"),
                inventoryAssetAccount("inventory"),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.EQUITY_CONTRIBUTION)));

    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                new BookkeepingEntry.DirectJournal(
                    new dev.erst.fingrind.core.JournalEntry(
                        LocalDate.parse("2026-04-07"),
                        List.of(
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("inventory"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                Money.parse("EUR", "10.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("3200"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "10.00")))),
                    null),
                generatedEvidence("direct-journal-inventory", "working-note"),
                requestProvenance("direct-journal-inventory"),
                SourceChannel.CLI),
            inventoryJournalBook),
        "raw-journal-touches-inventory");
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
                new AccountCode("1100"),
                receivableAccount("1100"),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE),
                new AccountCode("2100"),
                payableAccount("2100"),
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE)));
    PostingValidationStoreDouble creditBook =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1100"),
                receivableAccount("1100"),
                new AccountCode("2000"),
                account("2000", AccountType.REVENUE),
                new AccountCode("2100"),
                payableAccount("2100"),
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE)));

    assertContainsViolationCode(
        policy.rejectionFor(cashRevenue("settled-invoice-conflict", "invoice"), settledBook),
        "evidence-class-conflict");
    assertContainsViolationCode(
        policy.rejectionFor(settledExpense("expense-invoice-conflict", "invoice"), settledBook),
        "evidence-class-conflict");
    assertContainsViolationCode(
        policy.rejectionFor(
            receiptWithoutAdjunct("receipt-invoice-conflict", "invoice"), settledBook),
        "evidence-class-conflict");
    assertContainsViolationCode(
        policy.rejectionFor(
            paymentWithoutAdjunct("payment-invoice-conflict", "invoice"), settledBook),
        "evidence-class-conflict");
    assertContainsViolationCode(
        policy.rejectionFor(creditSale("credit-cash-conflict", "cash-receipt"), creditBook),
        "evidence-class-conflict");
    assertContainsViolationCode(
        policy.rejectionFor(
            creditExpense("credit-expense-cash-conflict", "cash-receipt"), creditBook),
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
                account("2000", AccountType.REVENUE),
                new AccountCode("5900"),
                financeExpenseAccount("5900")));

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
                                Money.parse("EUR", "90.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("5900"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                Money.parse("EUR", "10.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("1100"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "100.00")))),
                    null),
                generatedEvidence("fx-loss-receipt-shadow", "bank-deposit"),
                requestProvenance("fx-loss-receipt-shadow"),
                SourceChannel.CLI),
            book),
        "raw-journal-shadows-typed-event");
  }

  @Test
  void rejectionFor_cashDoctrineOnlyAdmitsRawAdjustmentsWhenOneCashLineExists() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble cashBook =
        new PostingValidationStoreDouble(
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.RESERVE),
                new AccountCode("3000"),
                account("3000", AccountType.EXPENSE)));

    assertSingleViolation(
        policy.rejectionFor(rawAdjustmentWithoutCashLine("adjustment-no-cash"), cashBook),
        "raw-journal-requires-cash-line");
    assertTrue(
        policy.rejectionFor(rawAdjustmentWithCashLine("adjustment-with-cash"), cashBook).isEmpty());
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

  private static PostEntryCommand cashRevenue(String token, String sourceDocumentType) {
    return new PostEntryCommand(
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
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand creditSale(String token, String sourceDocumentType) {
    return new PostEntryCommand(
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
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand creditExpense(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.ExpenseOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3000"),
            new AccountCode("2100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand settledExpense(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.ExpenseSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("3000"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand receipt(
      String token, String sourceDocumentType, AccountCode settlementAdjunctAccountCode) {
    return new PostEntryCommand(
        new BookkeepingEntry.Receipt(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("1100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            new SettlementAdjunct(
                settlementAdjunctAccountCode, MonetaryAmount.of(Money.parse("EUR", "1.00")))),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand receiptWithoutAdjunct(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.Receipt(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("1100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand payment(
      String token, String sourceDocumentType, AccountCode settlementAdjunctAccountCode) {
    return new PostEntryCommand(
        new BookkeepingEntry.Payment(
            LocalDate.parse("2026-04-07"),
            new AccountCode("2100"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            new SettlementAdjunct(
                settlementAdjunctAccountCode, MonetaryAmount.of(Money.parse("EUR", "1.00")))),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand paymentWithoutAdjunct(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.Payment(
            LocalDate.parse("2026-04-07"),
            new AccountCode("2100"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand rawAdjustmentWithoutCashLine(String token) {
    return new PostEntryCommand(
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
        generatedEvidence(token, "other-support"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand rawAdjustmentWithCashLine(String token) {
    return new PostEntryCommand(
        new BookkeepingEntry.DirectJournal(
            new dev.erst.fingrind.core.JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("1000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new dev.erst.fingrind.core.JournalLine(
                        new AccountCode("3200"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            null),
        generatedEvidence(token, "other-support"),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static RegisteredAccount account(String code, AccountType accountType) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        accountType,
        accountTaxonomy(accountType),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount equityAccount(
      String code, FinancialPositionLineClassification lineClassification) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EQUITY,
        financialPositionTaxonomy(lineClassification),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount receivableAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_RECEIVABLE),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount payableAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_PAYABLE),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount settlementAdjunctAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EXPENSE,
        profitAndLossTaxonomy(ProfitAndLossLineClassification.SETTLEMENT_FEE),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount operatingExpenseAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EXPENSE,
        profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount financeExpenseAccount(String code) {
    return registeredAccount(
        new AccountCode(code),
        new AccountName("Account " + code),
        AccountType.EXPENSE,
        profitAndLossTaxonomy(ProfitAndLossLineClassification.FINANCE_EXPENSE),
        true,
        DECLARED_AT);
  }

  private static BookIdentity accrualBookIdentity() {
    BookIdentity baseline = ExecutorAccountingTestSupport.bookIdentity();
    return new BookIdentity(
        baseline.entityProfile(),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL,
        baseline.functionalCurrency(),
        baseline.fiscalYearStart(),
        java.time.LocalDate.parse("2026-01-01"));
  }

  /** In-memory validation store used to drive doctrine and role-account branches. */
  private static final class PostingValidationStoreDouble implements PostingValidationStore {
    private final BookIdentity bookIdentity;
    private final Map<AccountCode, RegisteredAccount> accounts;

    private PostingValidationStoreDouble(Map<AccountCode, RegisteredAccount> accounts) {
      this(ExecutorAccountingTestSupport.bookIdentity(), accounts);
    }

    private PostingValidationStoreDouble(
        BookIdentity bookIdentity, Map<AccountCode, RegisteredAccount> accounts) {
      this.bookIdentity = bookIdentity;
      this.accounts = accounts;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(1001, 1, 1, DECLARED_AT, bookIdentity);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return accounts.entrySet().stream()
          .filter(entry -> accountCodes.contains(entry.getKey()))
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }
}
