package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Narrow internal-branch coverage for invariants that are otherwise unreachable via public flows.
 */
class PostEntrySemanticsPolicyInternalBranchTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void privateSemanticsHelpers_reportTypedEventInvariantMessages() {
    MethodHandle assertVerbClass =
        privateStatic(
            "assertVerbClass",
            MethodType.methodType(void.class, BookkeepingEntryKind.class, ResolvedJournal.class));

    ContractFailureException assertVerbClassFailure =
        assertThrows(
            ContractFailureException.class,
            () ->
                invoke(
                    assertVerbClass,
                    BookkeepingEntryKind.SALE_SETTLED,
                    ResolvedJournalSupport.resolve(
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
                        generatedEvidence("credit-sale-journal", "invoice"),
                        classificationAccounts())));
    IllegalArgumentException expectedTypedEventFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PostEntryAdmissionSupport.expectedTypedEventClass(
                    BookkeepingEntryKind.DIRECT_JOURNAL));

    assertEquals(
        "Typed entry kind SALE_SETTLED resolved to CREDIT_SALE instead of SETTLED_SALE.",
        assertVerbClassFailure.getMessage());
    assertEquals("internal-defect", assertVerbClassFailure.failure().code());
    String hint = java.util.Objects.requireNonNull(assertVerbClassFailure.failure().hint());
    assertNotNull(hint);
    assertTrue(hint.contains("rerunning the same request"));
    assertEquals(
        EconomicEventClass.REVERSAL,
        PostEntryAdmissionSupport.expectedTypedEventClass(BookkeepingEntryKind.REVERSAL));
    assertEquals(
        "Direct journals do not assert one typed event class.",
        expectedTypedEventFailure.getMessage());
  }

  @Test
  void privateSemanticsHelpers_coverMatchingTypedEventShortcut() {
    MethodHandle assertVerbClass =
        privateStatic(
            "assertVerbClass",
            MethodType.methodType(void.class, BookkeepingEntryKind.class, ResolvedJournal.class));

    assertDoesNotThrow(
        () ->
            invoke(
                assertVerbClass,
                BookkeepingEntryKind.SALE_SETTLED,
                settledSaleResolvedJournal("settled-sale-match")));
  }

  @Test
  void privateSemanticsHelpers_coverResolvedJournalEligibilityBranches() {
    MethodHandle canResolveResolvedJournal =
        privateStatic(
            "canResolveResolvedJournal",
            MethodType.methodType(boolean.class, BookkeepingEntry.class));

    assertSaleSettledEligibilityBranches(canResolveResolvedJournal);
    assertSaleOnCreditEligibilityBranches(canResolveResolvedJournal);
    assertExpenseEligibilityBranches(canResolveResolvedJournal);
    assertReversalEligibilityBranches(canResolveResolvedJournal);
  }

  private static void assertSaleSettledEligibilityBranches(MethodHandle canResolveResolvedJournal) {
    assertFalse(
        invokeBoolean(
            canResolveResolvedJournal,
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
                    new dev.erst.fingrind.contract.tax.TaxCode("vat-standard-sale")),
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("2000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                null,
                null,
                null,
                null,
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
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
                    new dev.erst.fingrind.contract.tax.TaxCode("vat-standard-sale")),
                appliedSaleTax("vat-standard-sale", "2100"))));
    assertFalse(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("2000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                null,
                null,
                null,
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("2000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                new dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryCosting(
                    Money.parse("EUR", "5.00"),
                    dev.erst.fingrind.core.Quantity.ofScaledUnits(0, 1),
                    Money.parse("EUR", "5.00")),
                null,
                null,
                null)));
  }

  private static void assertSaleOnCreditEligibilityBranches(
      MethodHandle canResolveResolvedJournal) {
    assertFalse(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("2000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                null,
                null,
                null,
                new dev.erst.fingrind.contract.tax.TaxSelection(
                    new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
                    new dev.erst.fingrind.contract.tax.TaxCode("vat-standard-sale")),
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("2000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                null,
                null,
                null,
                null,
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("2000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                null,
                null,
                null,
                new dev.erst.fingrind.contract.tax.TaxSelection(
                    new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
                    new dev.erst.fingrind.contract.tax.TaxCode("vat-standard-sale")),
                appliedSaleTax("vat-standard-sale", "2100"))));
    assertFalse(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("2000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                null,
                null,
                null,
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("2000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                new dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryCosting(
                    Money.parse("EUR", "5.00"),
                    dev.erst.fingrind.core.Quantity.ofScaledUnits(0, 1),
                    Money.parse("EUR", "5.00")),
                null,
                null,
                null)));
  }

  private static void assertExpenseEligibilityBranches(MethodHandle canResolveResolvedJournal) {
    assertFalse(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("1000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                null,
                new dev.erst.fingrind.contract.tax.TaxSelection(
                    new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
                    new dev.erst.fingrind.contract.tax.TaxCode("vat-standard-expense")),
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("1000"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                null,
                null,
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("1000"),
                MonetaryAmount.of(Money.parse("EUR", "12.10")),
                null,
                new dev.erst.fingrind.contract.tax.TaxSelection(
                    new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
                    new dev.erst.fingrind.contract.tax.TaxCode("vat-standard-expense")),
                appliedExpenseTax("vat-standard-expense", "1300"))));
    assertFalse(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("2100"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                null,
                new dev.erst.fingrind.contract.tax.TaxSelection(
                    new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
                    new dev.erst.fingrind.contract.tax.TaxCode("vat-standard-expense")),
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("2100"),
                MonetaryAmount.of(Money.parse("EUR", "10.00")),
                null,
                null,
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("2100"),
                MonetaryAmount.of(Money.parse("EUR", "12.10")),
                null,
                new dev.erst.fingrind.contract.tax.TaxSelection(
                    new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
                    new dev.erst.fingrind.contract.tax.TaxCode("vat-standard-expense")),
                appliedExpenseTax("vat-standard-expense", "1300"))));
  }

  private static void assertReversalEligibilityBranches(MethodHandle canResolveResolvedJournal) {
    assertFalse(
        invokeBoolean(
            canResolveResolvedJournal,
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-07"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    PostingApplicationServiceTestSupport.reversalReference("posting-1")
                        .orElseThrow(),
                    new dev.erst.fingrind.core.ReversalReason("full reversal")),
                null,
                null)));
    assertTrue(
        invokeBoolean(
            canResolveResolvedJournal,
            PostingApplicationServiceTestSupport.resolvedReversalEntry(
                "posting-1",
                "full reversal",
                PostingApplicationServiceTestSupport.reversalJournalEntry())));
  }

  @Test
  void admissionSupport_helpers_coverDirectJournalAndTypedEventBranches() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> directJournalViolations =
        new ArrayList<>();

    PostEntryAdmissionSupport.validateAdmissionByVerbAndBasis(
        directJournalViolations,
        BookkeepingEntryKind.DIRECT_JOURNAL,
        AccountingBasis.ACCRUAL,
        "entryKind",
        "DIRECT_JOURNAL",
        openingResolvedJournal("opening-direct-journal"));
    assertEquals(1, directJournalViolations.size());
    assertEquals("raw-journal-shadows-typed-event", directJournalViolations.getFirst().code());

    assertDoesNotThrow(
        () ->
            PostEntryAdmissionSupport.assertVerbClass(
                BookkeepingEntryKind.SALE_SETTLED,
                settledSaleResolvedJournal("settled-sale-direct-helper")));

    ContractFailureException mismatchFailure =
        assertThrows(
            ContractFailureException.class,
            () ->
                PostEntryAdmissionSupport.assertVerbClass(
                    BookkeepingEntryKind.SALE_SETTLED,
                    creditSaleResolvedJournal("credit-sale-direct-helper")));
    assertEquals(
        "Typed entry kind SALE_SETTLED resolved to CREDIT_SALE instead of SETTLED_SALE.",
        mismatchFailure.getMessage());
  }

  @Test
  void privateSemanticsHelpers_coverRawAdmissionBranches() {
    MethodHandle rawAdmission =
        privateStatic(
            "rawAdmission",
            MethodType.methodType(
                void.class,
                List.class,
                AccountingBasis.class,
                String.class,
                String.class,
                ResolvedJournal.class));
    Map<AccountCode, RegisteredAccount> accounts = classificationAccounts();

    List<BookkeepingPostingRejection.EntrySemanticsViolation> adjustmentViolations =
        new ArrayList<>();
    List<BookkeepingPostingRejection.EntrySemanticsViolation> accrualAdjustmentViolations =
        new ArrayList<>();
    List<BookkeepingPostingRejection.EntrySemanticsViolation> openingViolations = new ArrayList<>();
    List<BookkeepingPostingRejection.EntrySemanticsViolation> reversalViolations =
        new ArrayList<>();

    invoke(
        rawAdmission,
        adjustmentViolations,
        AccountingBasis.CASH,
        "entryKind",
        "DIRECT_JOURNAL",
        ResolvedJournalSupport.resolve(
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2200"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")))),
                null),
            generatedEvidence("adjustment-cash", "operator-note"),
            accounts));
    invoke(
        rawAdmission,
        accrualAdjustmentViolations,
        AccountingBasis.ACCRUAL,
        "entryKind",
        "DIRECT_JOURNAL",
        ResolvedJournalSupport.resolve(
            new BookkeepingEntry.DirectJournal(
                new dev.erst.fingrind.core.JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("1000"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")),
                        new dev.erst.fingrind.core.JournalLine(
                            new AccountCode("2200"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")))),
                null),
            generatedEvidence("adjustment-accrual", "operator-note"),
            accounts));
    invoke(
        rawAdmission,
        openingViolations,
        AccountingBasis.ACCRUAL,
        "entryKind",
        "DIRECT_JOURNAL",
        ResolvedJournalSupport.resolve(
            new BookkeepingEntry.OpeningPosition(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                        MonetaryAmount.of(Money.parse("EUR", "10.00")),
                        null),
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("2200"),
                        dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                        MonetaryAmount.of(Money.parse("EUR", "10.00")),
                        null))),
            generatedEvidence("opening-raw-admission", "operator-note"),
            accounts));
    invoke(
        rawAdmission,
        reversalViolations,
        AccountingBasis.ACCRUAL,
        "entryKind",
        "DIRECT_JOURNAL",
        ResolvedJournalSupport.resolve(
            PostingApplicationServiceTestSupport.resolvedReversalEntry(
                "posting-1",
                "full reversal",
                PostingApplicationServiceTestSupport.reversalJournalEntry()),
            generatedEvidence("reversal-raw-admission", "operator-note"),
            accounts));

    assertTrue(adjustmentViolations.isEmpty());
    assertTrue(accrualAdjustmentViolations.isEmpty());
    assertEquals(1, openingViolations.size());
    assertEquals("raw-journal-shadows-typed-event", openingViolations.getFirst().code());
    assertEquals(1, reversalViolations.size());
    assertEquals("raw-journal-shadows-typed-event", reversalViolations.getFirst().code());
  }

  private static Map<AccountCode, RegisteredAccount> classificationAccounts() {
    return Map.of(
        new AccountCode("1000"),
        account("1000", AccountType.ASSET),
        new AccountCode("1100"),
        receivableAccount("1100"),
        new AccountCode("2000"),
        account("2000", AccountType.REVENUE),
        new AccountCode("2200"),
        payableAccount("2200"));
  }

  private static ResolvedJournal settledSaleResolvedJournal(String token) {
    return ResolvedJournalSupport.resolve(
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
        generatedEvidence(token, "cash-receipt"),
        classificationAccounts());
  }

  private static ResolvedJournal creditSaleResolvedJournal(String token) {
    return ResolvedJournalSupport.resolve(
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
        generatedEvidence(token, "invoice"),
        classificationAccounts());
  }

  private static ResolvedJournal openingResolvedJournal(String token) {
    return ResolvedJournalSupport.resolve(
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00")),
                    null),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("2200"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                    MonetaryAmount.of(Money.parse("EUR", "10.00")),
                    null))),
        generatedEvidence(token, "opening-balance"),
        classificationAccounts());
  }

  private static MethodHandle privateStatic(String name, MethodType type) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(PostEntrySemanticsPolicy.class, MethodHandles.lookup());
      return lookup.findStatic(PostEntrySemanticsPolicy.class, name, type);
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError(exception.getMessage(), exception);
    }
  }

  private static void invoke(MethodHandle handle, Object... arguments) {
    try {
      handle.invokeWithArguments(arguments);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(throwable.getMessage(), throwable);
    }
  }

  private static boolean invokeBoolean(MethodHandle handle, Object argument) {
    try {
      return (boolean) handle.invokeWithArguments(argument);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(throwable.getMessage(), throwable);
    }
  }

  private static dev.erst.fingrind.contract.tax.AppliedTax appliedSaleTax(
      String taxCode, String accountCode) {
    return new dev.erst.fingrind.contract.tax.AppliedTax(
        new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
        new dev.erst.fingrind.contract.tax.TaxCode(taxCode),
        new dev.erst.fingrind.contract.tax.TaxCodeName("VAT"),
        new dev.erst.fingrind.contract.tax.TaxRate(210_000),
        dev.erst.fingrind.contract.tax.TaxInclusionMode.EXCLUSIVE,
        dev.erst.fingrind.contract.tax.TaxApplicationKind.OUTPUT_SALE,
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "210"),
        new MonetaryAmount("EUR", "1210"),
        new AccountCode(accountCode));
  }

  private static dev.erst.fingrind.contract.tax.AppliedTax appliedExpenseTax(
      String taxCode, String accountCode) {
    return new dev.erst.fingrind.contract.tax.AppliedTax(
        new dev.erst.fingrind.contract.tax.TaxRegistrationId("vat-lv"),
        new dev.erst.fingrind.contract.tax.TaxCode(taxCode),
        new dev.erst.fingrind.contract.tax.TaxCodeName("VAT"),
        new dev.erst.fingrind.contract.tax.TaxRate(210_000),
        dev.erst.fingrind.contract.tax.TaxInclusionMode.INCLUSIVE,
        dev.erst.fingrind.contract.tax.TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "210"),
        new MonetaryAmount("EUR", "1210"),
        new AccountCode(accountCode));
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
}
