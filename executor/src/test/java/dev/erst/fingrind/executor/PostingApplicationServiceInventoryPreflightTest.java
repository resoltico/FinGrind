package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.applicationService;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareInventoryVatRegistration;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareTaxAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** End-to-end preflight and commit coverage for inventory business-event resolution. */
class PostingApplicationServiceInventoryPreflightTest {
  @Test
  void inventoryWriteCommandsResolveTaxFxAndExactCostingAcrossOneDurableSequence() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      initializeTradingBook(bookSession);
      declareTaxAccounts(bookSession);
      declareInventoryVatRegistration(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      recordInitialInventoryCountIncrease(bookSession, applicationService);

      PostEntryResult.Committed capitalization =
          committed(
              applicationService,
              new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("1000"),
                  new MonetaryAmount("EUR", "100"),
                  foreignExchange("100"),
                  null,
                  null),
              "capitalization-settled",
              "landed-cost-invoice");
      assertEquals(
          EconomicEventClass.INVENTORY_CAPITALIZATION,
          capitalization.resolvedJournal().classification().eventClass());
      assertInventoryState(bookSession, "3", "10.00");

      PostEntryResult.Committed shrinkage =
          committed(
              applicationService,
              new InventoryBookkeepingEntryVariants.InventoryShrinkage(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("5200"),
                  new dev.erst.fingrind.contract.bookkeeping.QuantityText("2"),
                  null),
              "shrinkage",
              "inventory-count-sheet");
      assertEquals(
          EconomicEventClass.INVENTORY_SHRINKAGE,
          shrinkage.resolvedJournal().classification().eventClass());
      assertEquals(
          Money.parse("EUR", "6.67"),
          shrinkage.resolvedJournal().expandedLines().lines().get(0).amount().money());
      assertInventoryState(bookSession, "1", "3.33");

      PostEntryResult.Committed settledPurchase =
          committed(
              applicationService,
              new BookkeepingEntry.PurchaseSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("1000"),
                  new dev.erst.fingrind.contract.bookkeeping.QuantityText("2"),
                  new MonetaryAmount("EUR", "5000"),
                  null,
                  foreignExchange("10000"),
                  new TaxSelection(
                      new TaxRegistrationId("vat-inventory"), new TaxCode("vat-input-recoverable")),
                  null),
              "purchase-settled",
              "purchase-receipt");
      assertEquals(
          EconomicEventClass.SETTLED_PURCHASE,
          settledPurchase.resolvedJournal().classification().eventClass());
      assertEquals(3, settledPurchase.resolvedJournal().expandedLines().lines().size());
      assertEquals(
          Money.parse("EUR", "100.00"),
          settledPurchase.resolvedJournal().expandedLines().lines().get(0).amount().money());
      assertEquals(
          Money.parse("EUR", "20.00"),
          settledPurchase.resolvedJournal().expandedLines().lines().get(1).amount().money());
      assertEquals(
          Money.parse("EUR", "120.00"),
          settledPurchase.resolvedJournal().expandedLines().lines().get(2).amount().money());
      assertInventoryState(bookSession, "3", "103.33");

      ForeignExchangeDetails creditPurchaseForeignExchange = foreignExchange("100");
      PostEntryResult.Committed creditPurchase =
          committed(
              applicationService,
              new BookkeepingEntry.PurchaseOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("2200"),
                  new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                  new MonetaryAmount("EUR", "100"),
                  null,
                  creditPurchaseForeignExchange,
                  new TaxSelection(
                      new TaxRegistrationId("vat-inventory"),
                      new TaxCode("vat-input-nonrecoverable")),
                  null),
              "purchase-credit",
              "supplier-invoice");
      assertEquals(
          EconomicEventClass.CREDIT_PURCHASE,
          creditPurchase.resolvedJournal().classification().eventClass());
      assertEquals(2, creditPurchase.resolvedJournal().expandedLines().lines().size());
      assertEquals(
          creditPurchaseForeignExchange, creditPurchase.resolvedJournal().foreignExchangeDetails());
      assertEquals(
          Money.parse("EUR", "1.20"),
          creditPurchase.resolvedJournal().expandedLines().lines().get(0).amount().money());
      assertInventoryState(bookSession, "4", "104.53");

      PostEntryResult.Committed creditCapitalization =
          committed(
              applicationService,
              new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("2200"),
                  new MonetaryAmount("EUR", "500"),
                  foreignExchange("500"),
                  new TaxSelection(
                      new TaxRegistrationId("vat-inventory"),
                      new TaxCode("vat-input-nonrecoverable")),
                  null),
              "capitalization-credit",
              "landed-cost-invoice");
      assertEquals(
          EconomicEventClass.INVENTORY_CAPITALIZATION,
          creditCapitalization.resolvedJournal().classification().eventClass());
      assertEquals(2, creditCapitalization.resolvedJournal().expandedLines().lines().size());
      assertEquals(
          Money.parse("EUR", "6.00"),
          creditCapitalization.resolvedJournal().expandedLines().lines().get(0).amount().money());
      assertEquals(
          foreignExchange("500"), creditCapitalization.resolvedJournal().foreignExchangeDetails());
      assertInventoryState(bookSession, "4", "110.53");

      PostEntryResult.Committed recoverableCapitalization =
          committed(
              applicationService,
              new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("1000"),
                  new MonetaryAmount("EUR", "200"),
                  foreignExchange("200"),
                  new TaxSelection(
                      new TaxRegistrationId("vat-inventory"), new TaxCode("vat-input-recoverable")),
                  null),
              "capitalization-settled-recoverable",
              "landed-cost-invoice");
      assertEquals(3, recoverableCapitalization.resolvedJournal().expandedLines().lines().size());
      assertEquals(
          Money.parse("EUR", "2.00"),
          recoverableCapitalization
              .resolvedJournal()
              .expandedLines()
              .lines()
              .get(0)
              .amount()
              .money());
      assertEquals(
          Money.parse("EUR", "0.40"),
          recoverableCapitalization
              .resolvedJournal()
              .expandedLines()
              .lines()
              .get(1)
              .amount()
              .money());
      assertEquals(
          Money.parse("EUR", "2.40"),
          recoverableCapitalization
              .resolvedJournal()
              .expandedLines()
              .lines()
              .get(2)
              .amount()
              .money());
      assertEquals(
          foreignExchange("200"),
          recoverableCapitalization.resolvedJournal().foreignExchangeDetails());
      assertInventoryState(bookSession, "4", "112.53");

      PostEntryResult.Committed taxedForeignCurrencySale =
          committed(
              applicationService,
              new BookkeepingEntry.SaleSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1000"),
                  new AccountCode("4000"),
                  new MonetaryAmount("EUR", "20000"),
                  new dev.erst.fingrind.contract.bookkeeping.InventoryRelief(
                      new AccountCode("1400"),
                      new AccountCode("5000"),
                      new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                  null,
                  foreignExchange("20000"),
                  new TaxSelection(
                      new TaxRegistrationId("vat-inventory"), new TaxCode("vat-output")),
                  null),
              "sale-settled",
              "cash-receipt");
      assertEquals(
          EconomicEventClass.SETTLED_SALE,
          taxedForeignCurrencySale.resolvedJournal().classification().eventClass());
      assertEquals(5, taxedForeignCurrencySale.resolvedJournal().expandedLines().lines().size());
      assertEquals(
          Money.parse("EUR", "28.13"),
          taxedForeignCurrencySale
              .resolvedJournal()
              .expandedLines()
              .lines()
              .get(3)
              .amount()
              .money());
      assertInventoryState(bookSession, "3", "84.40");

      PostEntryResult.Committed writeDown =
          committed(
              applicationService,
              new InventoryBookkeepingEntryVariants.InventoryWriteDown(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("5100"),
                  new MonetaryAmount("EUR", "500")),
              "write-down",
              "inventory-write-down-assessment");
      assertEquals(
          EconomicEventClass.INVENTORY_WRITE_DOWN,
          writeDown.resolvedJournal().classification().eventClass());
      assertEquals(
          Money.parse("EUR", "5.00"),
          writeDown.resolvedJournal().expandedLines().lines().get(0).amount().money());
      assertInventoryState(bookSession, "3", "79.40");
    }
  }

  private static void recordInitialInventoryCountIncrease(
      InMemoryBookSession bookSession, PostingApplicationService applicationService) {
    PostEntryResult.Committed countIncrease =
        committed(
            applicationService,
            new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("5300"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("3"),
                new MonetaryAmount("EUR", "300"),
                null),
            "count-increase",
            "inventory-count-sheet");
    assertEquals(
        EconomicEventClass.INVENTORY_COUNT_INCREASE,
        countIncrease.resolvedJournal().classification().eventClass());
    assertInventoryState(bookSession, "3", "9.00");
  }

  private static void initializeTradingBook(InMemoryBookSession bookSession) {
    bookSession.openBook(
        PostingApplicationServiceTestSupport.FIXED_CLOCK.instant(),
        new BookIdentity(
            new EntityProfile(new BookEntityName("Acme Trading")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL,
            dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            java.time.LocalDate.parse("2026-01-01")),
        List.of());
    declareTradingAccount(
        bookSession,
        "1000",
        "Cash",
        dev.erst.fingrind.core.AccountType.ASSET,
        ExecutorAccountingTestSupport.accountTaxonomy(
            dev.erst.fingrind.core.AccountType.ASSET, dev.erst.fingrind.core.NormalBalance.DEBIT));
    declareTradingAccount(
        bookSession,
        "2200",
        "Trade Payables",
        dev.erst.fingrind.core.AccountType.LIABILITY,
        ExecutorAccountingTestSupport.financialPositionTaxonomy(
            dev.erst.fingrind.core.FinancialPositionLineClassification.TRADE_PAYABLE));
    declareTradingAccount(
        bookSession,
        "4000",
        "Sales Revenue",
        dev.erst.fingrind.core.AccountType.REVENUE,
        ExecutorAccountingTestSupport.accountTaxonomy(
            dev.erst.fingrind.core.AccountType.REVENUE,
            dev.erst.fingrind.core.NormalBalance.CREDIT));
    bookSession.declareAccount(
        new AccountDeclaration(
            new AccountCode("1400"),
            new dev.erst.fingrind.core.AccountName("Inventory"),
            dev.erst.fingrind.core.AccountType.ASSET,
            ExecutorAccountingTestSupport.financialPositionTaxonomy(
                dev.erst.fingrind.core.FinancialPositionLineClassification.INVENTORY),
            new dev.erst.fingrind.core.UnitOfMeasure("unit", 0)),
        PostingApplicationServiceTestSupport.FIXED_CLOCK.instant());
    declareTradingAccount(
        bookSession,
        "5000",
        "Cost of Sales",
        dev.erst.fingrind.core.AccountType.EXPENSE,
        ExecutorAccountingTestSupport.accountTaxonomy(dev.erst.fingrind.core.AccountType.EXPENSE));
    declareTradingAccount(
        bookSession,
        "5100",
        "Inventory Write-Down Loss",
        dev.erst.fingrind.core.AccountType.EXPENSE,
        ExecutorAccountingTestSupport.accountTaxonomy(dev.erst.fingrind.core.AccountType.EXPENSE));
    declareTradingAccount(
        bookSession,
        "5200",
        "Inventory Shrinkage Loss",
        dev.erst.fingrind.core.AccountType.EXPENSE,
        ExecutorAccountingTestSupport.accountTaxonomy(dev.erst.fingrind.core.AccountType.EXPENSE));
    declareTradingAccount(
        bookSession,
        "5300",
        "Inventory Count Gain",
        dev.erst.fingrind.core.AccountType.REVENUE,
        ExecutorAccountingTestSupport.accountTaxonomy(
            dev.erst.fingrind.core.AccountType.REVENUE,
            dev.erst.fingrind.core.NormalBalance.CREDIT));
  }

  private static void declareTradingAccount(
      InMemoryBookSession bookSession,
      String accountCode,
      String accountName,
      dev.erst.fingrind.core.AccountType accountType,
      dev.erst.fingrind.core.AccountTaxonomy accountTaxonomy) {
    bookSession.declareAccount(
        new AccountCode(accountCode),
        new dev.erst.fingrind.core.AccountName(accountName),
        accountType,
        accountTaxonomy,
        PostingApplicationServiceTestSupport.FIXED_CLOCK.instant());
  }

  private static PostEntryResult.Committed committed(
      PostingApplicationService applicationService,
      BookkeepingEntry entry,
      String idempotencyKey,
      String sourceDocumentType) {
    var result =
        applicationService.commit(
            new PostEntryCommand(
                entry,
                generatedEvidence(idempotencyKey, sourceDocumentType),
                requestProvenance(idempotencyKey),
                SourceChannel.CLI),
            TEST_AUTHORIZER);
    return assertInstanceOf(PostEntryResult.Committed.class, result, result::toString);
  }

  private static void assertInventoryState(
      InMemoryBookSession bookSession, String expectedQuantity, String expectedCostPool) {
    WeightedAverageCostingMath.InventoryPool pool =
        bookSession.findInventoryAccountState(new AccountCode("1400")).orElseThrow().pool();
    assertEquals(
        Quantity.ofScaledUnits(0, Long.parseLong(expectedQuantity)), pool.quantityOnHand());
    assertEquals(Money.parse("EUR", expectedCostPool), pool.costPool());
  }

  private static ForeignExchangeDetails foreignExchange(String functionalMinorUnits) {
    MonetaryAmount functionalAmount = new MonetaryAmount("EUR", functionalMinorUnits);
    MonetaryAmount transactionAmount =
        new MonetaryAmount(
            "USD", Long.toString(Math.multiplyExact(functionalAmount.toMoney().minorUnits(), 2L)));
    return new ForeignExchangeDetails(
        transactionAmount,
        functionalAmount,
        new QuotedExchangeRate(
            transactionAmount,
            functionalAmount,
            LocalDate.parse("2026-04-07"),
            "Test exchange rate"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }
}
