package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryCosting;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryDisposal;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for SQLite write-side staging invariants. */
class SqliteMutationWriterTest extends SqlitePostingFactStoreTestSupport {
  private static final MethodHandle REQUIRE_BALANCED_PENDING_JOURNAL_LINE_TABLE =
      mutationWriterHelper("requireBalancedPendingJournalLineTable");

  @Test
  void balancedPendingJournalLineTable_rejectsUnbalancedOrMalformedRows() {
    Path bookPath = tempDirectory.resolve("pending-journal-line.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          database.executeStatement(SqlitePostingSql.CREATE_PENDING_JOURNAL_LINE);
          database.executeStatement(
              """
              insert into pending_journal_line (
                  line_order,
                  account_code,
                  entry_side,
                  currency_code,
                  amount_minor
              ) values (
                  0,
                  '1000',
                  'DEBIT',
                  'EUR',
                  1000
              )
              """);

          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> requireBalancedPendingJournalLineTable(database));
          assertEquals(
              "SQLite journal-line staging rejected one unbalanced or malformed posting.",
              exception.getMessage());
        });
  }

  @Test
  void insertInterimResultSweep_requiresExactlyOneReturnedCloseOrderRow() {
    Path bookPath = tempDirectory.resolve("interim-result-sweep-return-shapes.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteStatementRedirectingDatabase noRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteReportingPeriodCloseSql.INSERT_PERIOD_RESULT_TRANSFER.equals(sql)
                              ? "select ?1 as close_order where 0 and ?2 is not null and ?3 is not null and ?4 is not null"
                              : sql));
          IllegalStateException noRowFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteMutationWriter.insertInterimResultSweep(
                          noRowDatabase,
                          new dev.erst.fingrind.core.ReportingPeriod(
                              LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                          new AccountCode("3200"),
                          List.of(),
                          Instant.parse("2026-04-30T10:15:30Z"),
                          List.of()));
          assertEquals(
              "SQLite interim-result sweep insert returned no sweep order.",
              noRowFailure.getMessage());

          SqliteStatementRedirectingDatabase extraRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteReportingPeriodCloseSql.INSERT_PERIOD_RESULT_TRANSFER.equals(sql)
                              ? "select ?1 as close_order union all select ?2 where ?3 is not null and ?4 is not null"
                              : sql));
          IllegalStateException extraRowFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteMutationWriter.insertInterimResultSweep(
                          extraRowDatabase,
                          new dev.erst.fingrind.core.ReportingPeriod(
                              LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                          new AccountCode("3200"),
                          List.of(),
                          Instant.parse("2026-04-30T10:15:30Z"),
                          List.of()));
          assertEquals(
              "SQLite interim-result sweep insert returned more than one sweep order.",
              extraRowFailure.getMessage());
        });
  }

  @Test
  void insertInterimResultSweep_persistsClosedTotalsRows() {
    Path bookPath = tempDirectory.resolve("interim-result-sweep-totals.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);
          SqliteStoreFixtureSupport.insertAccountRow(
              database,
              "3200",
              "Result holding",
              "EQUITY",
              SqlitePostingFactFixtureSupport.financialPositionTaxonomy(
                  FinancialPositionLineClassification.RESULT_HOLDING),
              1,
              "2026-01-01T00:00:00Z");

          SqliteMutationWriter.insertInterimResultSweep(
              database,
              new dev.erst.fingrind.core.ReportingPeriod(
                  LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
              new AccountCode("3200"),
              List.of(
                  CurrencyBalance.ofTotals(
                      Money.ofMinorUnits(CurrencyUnit.of("EUR"), 1250),
                      Money.ofMinorUnits(CurrencyUnit.of("EUR"), 750))),
              Instant.parse("2026-04-30T10:15:30Z"),
              List.of());

          assertEquals(
              1,
              SqliteStatementQueries.querySingleInt(
                  database, "select count(*) from interim_result_sweep_total"));
        });
  }

  @Test
  void insertPostingFact_roundTripsRemainingRetainedEntryVariants() {
    Path bookPath = tempDirectory.resolve("posting-fact-retained-entry-roundtrip.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);

          assertRoundTripRetainedEntry(
              database,
              "posting-opening-position",
              new BookkeepingEntry.OpeningPosition(
                  LocalDate.parse("2026-04-07"),
                  List.of(
                      new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                          new AccountCode("1000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                          new MonetaryAmount("EUR", "1250"),
                          null),
                      new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                          new AccountCode("3000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                          new MonetaryAmount("EUR", "1250"),
                          null))));
          assertRoundTripRetainedEntry(
              database,
              "posting-direct-journal-foreign-exchange",
              new BookkeepingEntry.DirectJournal(
                  new dev.erst.fingrind.core.JournalEntry(
                      LocalDate.parse("2026-04-07"),
                      List.of(
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("1000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                              money("EUR", "92.00")),
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("4000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                              money("EUR", "92.00")))),
                  foreignExchangeDetails()));
          assertRoundTripRetainedEntry(
              database,
              "posting-expense",
              new BookkeepingEntry.ExpenseSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("5000"),
                  new AccountCode("1000"),
                  new MonetaryAmount("EUR", "1250"),
                  null,
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-purchase-settled",
              new BookkeepingEntry.PurchaseSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("1000"),
                  new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                  new MonetaryAmount("EUR", "1250"),
                  null,
                  null,
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-purchase-on-credit",
              new BookkeepingEntry.PurchaseOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("2100"),
                  new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                  new MonetaryAmount("EUR", "1250"),
                  null,
                  null,
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-sale-settled-inventory-relief",
              new BookkeepingEntry.SaleSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1000"),
                  new AccountCode("4000"),
                  new MonetaryAmount("EUR", "1250"),
                  new InventoryRelief(
                      new AccountCode("1400"),
                      new AccountCode("5000"),
                      new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                  null,
                  null,
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-sale-on-credit-taxed",
              new BookkeepingEntry.SaleOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1100"),
                  new AccountCode("4000"),
                  new MonetaryAmount("EUR", "1000"),
                  new InventoryRelief(
                      new AccountCode("1400"),
                      new AccountCode("5000"),
                      new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                  null,
                  null,
                  taxSelection("vat-standard-sale"),
                  appliedSaleTax("vat-standard-sale", "2100")));
          assertRoundTripRetainedEntry(
              database,
              "posting-sale-on-credit-plain",
              new BookkeepingEntry.SaleOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1100"),
                  new AccountCode("4000"),
                  new MonetaryAmount("EUR", "9200"),
                  null,
                  null,
                  foreignExchangeDetails(),
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-expense-on-credit-taxed",
              new BookkeepingEntry.ExpenseOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("5000"),
                  new AccountCode("2100"),
                  new MonetaryAmount("EUR", "1210"),
                  null,
                  taxSelection("vat-standard-expense"),
                  appliedExpenseTax("vat-standard-expense", "1300")));
          assertRoundTripRetainedEntry(
              database,
              "posting-receipt-plain",
              new BookkeepingEntry.Receipt(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1000"),
                  new AccountCode("1100"),
                  new MonetaryAmount("EUR", "1250"),
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-receipt-adjunct",
              new BookkeepingEntry.Receipt(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1000"),
                  new AccountCode("1100"),
                  new MonetaryAmount("EUR", "1250"),
                  settlementAdjunct("6100", "50")));
          assertRoundTripRetainedEntry(
              database,
              "posting-payment-plain",
              new BookkeepingEntry.Payment(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("2100"),
                  new AccountCode("1000"),
                  new MonetaryAmount("EUR", "1250"),
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-payment-adjunct",
              new BookkeepingEntry.Payment(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("2100"),
                  new AccountCode("1000"),
                  new MonetaryAmount("EUR", "1250"),
                  settlementAdjunct("6200", "75")));
          assertRoundTripRetainedEntry(
              database,
              "posting-owner-contribution",
              new BookkeepingEntry.OwnerContribution(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1000"),
                  new AccountCode("3000"),
                  new MonetaryAmount("EUR", "1250"),
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-owner-withdrawal",
              new BookkeepingEntry.OwnerWithdrawal(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("3010"),
                  new AccountCode("1000"),
                  new MonetaryAmount("EUR", "1250"),
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-sale-foreign-exchange",
              new BookkeepingEntry.SaleSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1000"),
                  new AccountCode("4000"),
                  new MonetaryAmount("EUR", "9200"),
                  null,
                  null,
                  foreignExchangeDetails(),
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-reversal-foreign-exchange",
              new BookkeepingEntry.Reversal(
                  LocalDate.parse("2026-04-07"),
                  new PostingLineage.Reversal(
                      new dev.erst.fingrind.core.ReversalReference(
                          new PostingId("posting-direct-journal-foreign-exchange")),
                      new dev.erst.fingrind.core.ReversalReason("Correction")),
                  reversalForeignExchangeDetails(),
                  new dev.erst.fingrind.core.JournalEntry(
                      LocalDate.parse("2026-04-07"),
                      List.of(
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("1000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                              money("EUR", "12.50")),
                          new dev.erst.fingrind.core.JournalLine(
                              new AccountCode("4000"),
                              dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                              money("EUR", "12.50"))))));
        });
  }

  @Test
  void insertPostingFact_roundTripsOpeningPositionInventoryQuantity() {
    Path bookPath = tempDirectory.resolve("posting-opening-position-inventory-quantity.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);
          SqliteStoreFixtureSupport.insertAccountRow(
              database,
              "1400",
              "Inventory",
              "ASSET",
              SqlitePostingFactFixtureSupport.financialPositionTaxonomy(
                  FinancialPositionLineClassification.INVENTORY),
              1,
              "2026-04-07T10:15:30Z");

          String postingId = "posting-opening-position-inventory";
          BookkeepingEntry.OpeningPosition openingPosition =
              new BookkeepingEntry.OpeningPosition(
                  LocalDate.parse("2026-04-07"),
                  List.of(
                      new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                          new AccountCode("1400"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                          new MonetaryAmount("EUR", "1250"),
                          new dev.erst.fingrind.contract.bookkeeping.QuantityText("2")),
                      new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                          new AccountCode("3000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                          new MonetaryAmount("EUR", "1250"),
                          null)));

          assertRoundTripRetainedEntry(
              database,
              postingId,
              openingPosition,
              () ->
                  SqliteInventoryCostingWriter.insertInventoryMovement(
                      database,
                      "movement-opening-position-inventory",
                      new AccountCode("1400"),
                      LocalDate.parse("2026-04-07"),
                      InventoryMovementKind.OPENING,
                      2L,
                      1_250L,
                      new PostingId(postingId)));
        });
  }

  @Test
  void readOpeningPosition_wrapsPersistedInventoryQuantityQueryFailure() {
    Path bookPath =
        tempDirectory.resolve("posting-opening-position-inventory-query-failure.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);
          String postingId = "posting-opening-position-query-failure";
          assertRoundTripRetainedEntry(
              database,
              postingId,
              new BookkeepingEntry.OpeningPosition(
                  LocalDate.parse("2026-04-07"),
                  List.of(
                      new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                          new AccountCode("1000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                          new MonetaryAmount("EUR", "1250"),
                          null),
                      new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                          new AccountCode("3000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                          new MonetaryAmount("EUR", "1250"),
                          null))));

          SqliteStatementRedirectingDatabase failingDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteInventoryCostingSql.LOAD_OPENING_INVENTORY_QUANTITIES_BY_POSTING_ID
                                  .equals(sql)
                              ? "select missing_column from missing_table"
                              : sql));
          IllegalStateException failure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      new SqlitePostingReader()
                          .findOneCommittedPosting(
                              failingDatabase,
                              SqlitePostingSql.FIND_POSTING_BY_ID,
                              statement -> statement.bindText(1, postingId)));

          String message = Objects.requireNonNull(failure.getMessage());
          assertTrue(
              message.startsWith("Failed to read persisted opening inventory quantities."),
              message);
        });
  }

  @Test
  void insertPostingFact_roundTripsForeignExchangeForEveryCreditSideVariant() {
    Path bookPath = tempDirectory.resolve("posting-fact-credit-side-foreign-exchange.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);

          assertRoundTripRetainedEntry(
              database,
              "posting-sale-on-credit-foreign-exchange",
              new BookkeepingEntry.SaleOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1100"),
                  new AccountCode("4000"),
                  new MonetaryAmount("EUR", "9200"),
                  null,
                  null,
                  foreignExchangeDetails(),
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-purchase-on-credit-foreign-exchange",
              new BookkeepingEntry.PurchaseOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("2100"),
                  new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                  new MonetaryAmount("EUR", "9200"),
                  null,
                  foreignExchangeDetails(),
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-inventory-capitalization-on-credit-foreign-exchange",
              new dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants
                  .InventoryCapitalizationOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("2100"),
                  new MonetaryAmount("EUR", "9200"),
                  foreignExchangeDetails(),
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-expense-on-credit-foreign-exchange",
              new BookkeepingEntry.ExpenseOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("5000"),
                  new AccountCode("2100"),
                  new MonetaryAmount("EUR", "9200"),
                  foreignExchangeDetails(),
                  null,
                  null));
        });
  }

  @Test
  void insertPostingFact_roundTripsEveryInventoryMaintenanceVariant() {
    Path bookPath = tempDirectory.resolve("posting-fact-inventory-maintenance-roundtrip.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);

          assertRoundTripRetainedEntry(
              database,
              "posting-inventory-capitalization-settled",
              new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("1000"),
                  new MonetaryAmount("EUR", "1210"),
                  null,
                  taxSelection("vat-standard-expense"),
                  appliedExpenseTax("vat-standard-expense", "1300")));
          assertRoundTripRetainedEntry(
              database,
              "posting-inventory-capitalization-on-credit",
              new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("2100"),
                  new MonetaryAmount("EUR", "1250"),
                  null,
                  null,
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-inventory-write-down",
              new InventoryBookkeepingEntryVariants.InventoryWriteDown(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("6100"),
                  new MonetaryAmount("EUR", "125")));
          assertRoundTripRetainedEntry(
              database,
              "posting-inventory-shrinkage",
              new InventoryBookkeepingEntryVariants.InventoryShrinkage(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("6200"),
                  new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-inventory-count-increase",
              new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("7100"),
                  new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                  new MonetaryAmount("EUR", "1250"),
                  null));
        });
  }

  @Test
  void insertFiscalYearClose_requiresExactlyOneReturnedCloseOrderRow() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-return-shapes.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteStatementRedirectingDatabase noRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteReportingPeriodCloseSql.INSERT_FISCAL_YEAR_CLOSE.equals(sql)
                              ? "select ?1 as close_order where 0 and ?2 is not null and ?3 is not null and ?4 is not null and ?5 is not null and ?6 is not null"
                              : sql));
          IllegalStateException noRowFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteMutationWriter.insertFiscalYearClose(
                          noRowDatabase,
                          new dev.erst.fingrind.core.ReportingPeriod(
                              LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
                          new AccountCode("3000"),
                          new AccountCode("3200"),
                          new AccountCode("3300"),
                          Instant.parse("2026-12-31T23:59:59Z"),
                          List.of()));
          assertEquals(
              "SQLite fiscal-year close insert returned no close order.",
              noRowFailure.getMessage());

          SqliteStatementRedirectingDatabase extraRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteReportingPeriodCloseSql.INSERT_FISCAL_YEAR_CLOSE.equals(sql)
                              ? "select ?1 as close_order union all select ?2 where ?3 is not null and ?4 is not null and ?5 is not null and ?6 is not null"
                              : sql));
          IllegalStateException extraRowFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteMutationWriter.insertFiscalYearClose(
                          extraRowDatabase,
                          new dev.erst.fingrind.core.ReportingPeriod(
                              LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
                          new AccountCode("3000"),
                          new AccountCode("3200"),
                          new AccountCode("3300"),
                          Instant.parse("2026-12-31T23:59:59Z"),
                          List.of()));
          assertEquals(
              "SQLite fiscal-year close insert returned more than one close order.",
              extraRowFailure.getMessage());
        });
  }

  private static void requireBalancedPendingJournalLineTable(SqliteNativeDatabase activeDatabase) {
    try {
      REQUIRE_BALANCED_PENDING_JOURNAL_LINE_TABLE.invokeExact(activeDatabase);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite pending-journal-line validation helper.", throwable);
    }
  }

  private static MethodHandle mutationWriterHelper(String methodName) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteMutationWriter.class, MethodHandles.lookup());
      return lookup.findStatic(
          SqliteMutationWriter.class,
          methodName,
          MethodType.methodType(void.class, SqliteNativeDatabase.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind SQLite mutation-writer helper: " + methodName, exception);
    }
  }

  private static void assertRoundTripRetainedEntry(
      SqliteNativeDatabase database, String postingId, BookkeepingEntry entry) {
    assertRoundTripRetainedEntry(database, postingId, entry, () -> {});
  }

  private static void assertRoundTripRetainedEntry(
      SqliteNativeDatabase database,
      String postingId,
      BookkeepingEntry entry,
      Runnable afterJournalPersistence) {
    String token = postingId;
    BookkeepingEntry resolvedEntry = resolvedEntryForPersistence(entry);
    dev.erst.fingrind.executor.bookkeeping.PostingLineageModel postingLineage =
        entry instanceof BookkeepingEntry.Reversal reversal
            ? dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.reversal(
                reversal.reversal().reference(), reversal.reversal().reason())
            : dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct();
    CommittedPosting posting =
        new CommittedPosting(
            new PostingId(postingId),
            resolvedEntry.journalEntry(),
            postingLineage,
            entry.postingKind(),
            entry.postingOriginKind(),
            SqlitePostingFactFixtureSupport.accountingEvidence(token),
            committedProvenance(token),
            entry,
            resolvedEntry);
    RequestFingerprint requestFingerprint =
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64));

    SqliteMutationWriter.insertPostingFact(database, posting, requestFingerprint);
    SqliteMutationWriter.insertJournalLines(database, posting, SqliteCommitFaultHook.NONE);
    afterJournalPersistence.run();
    CommittedPosting persisted =
        new SqlitePostingReader()
            .findOneCommittedPosting(
                database,
                SqlitePostingSql.FIND_POSTING_BY_ID,
                statement -> statement.bindText(1, postingId))
            .orElseThrow();

    assertEquals(Optional.of(entry), persisted.callerAuthoredEntry());
    assertEquals(entry.postingOriginKind(), persisted.postingOriginKind());
  }

  private static BookkeepingEntry resolvedEntryForPersistence(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.PurchaseSettled purchase ->
          new BookkeepingEntry.PurchaseSettled(
              purchase.effectiveDate(),
              purchase.inventoryAccountCode(),
              purchase.cashAccountCode(),
              purchase.quantity(),
              purchase.unitCost(),
              resolvedInventoryAcquisition(purchase.quantity(), purchase.unitCost()),
              purchase.foreignExchangeDetails(),
              null,
              null);
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          new BookkeepingEntry.PurchaseOnCredit(
              purchase.effectiveDate(),
              purchase.inventoryAccountCode(),
              purchase.payableAccountCode(),
              purchase.quantity(),
              purchase.unitCost(),
              resolvedInventoryAcquisition(purchase.quantity(), purchase.unitCost()),
              purchase.foreignExchangeDetails(),
              null,
              null);
      case BookkeepingEntry.SaleSettled sale when sale.inventoryRelief() != null ->
          new BookkeepingEntry.SaleSettled(
              sale.effectiveDate(),
              sale.cashAccountCode(),
              sale.revenueAccountCode(),
              sale.amount(),
              sale.inventoryRelief(),
              resolvedInventoryCosting(sale.amount(), sale.inventoryRelief().quantity()),
              sale.foreignExchangeDetails(),
              sale.taxSelection(),
              sale.appliedTax());
      case BookkeepingEntry.SaleOnCredit sale when sale.inventoryRelief() != null ->
          new BookkeepingEntry.SaleOnCredit(
              sale.effectiveDate(),
              sale.receivableAccountCode(),
              sale.revenueAccountCode(),
              sale.amount(),
              sale.inventoryRelief(),
              resolvedInventoryCosting(sale.amount(), sale.inventoryRelief().quantity()),
              sale.foreignExchangeDetails(),
              sale.taxSelection(),
              sale.appliedTax());
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          new InventoryBookkeepingEntryVariants.InventoryShrinkage(
              shrinkage.effectiveDate(),
              shrinkage.inventoryAccountCode(),
              shrinkage.shrinkageLossAccountCode(),
              shrinkage.quantity(),
              resolvedInventoryDisposal(shrinkage.quantity()));
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
              countIncrease.effectiveDate(),
              countIncrease.inventoryAccountCode(),
              countIncrease.countGainAccountCode(),
              countIncrease.quantity(),
              countIncrease.unitCost(),
              resolvedInventoryAcquisition(countIncrease.quantity(), countIncrease.unitCost()));
      default -> entry;
    };
  }

  private static ResolvedInventoryAcquisition resolvedInventoryAcquisition(
      dev.erst.fingrind.contract.bookkeeping.QuantityText quantity, MonetaryAmount unitCost) {
    Quantity resolvedQuantity = resolvedQuantity(quantity);
    MonetaryAmount acquisitionCost =
        MonetaryAmount.of(
            WeightedAverageCostingMath.acquire(
                    WeightedAverageCostingMath.InventoryPool.zero(
                        CurrencyUnit.of(unitCost.currencyCode()), resolvedQuantity.scale()),
                    resolvedQuantity,
                    unitCost.toMoney())
                .costPool());
    return new ResolvedInventoryAcquisition(resolvedQuantity, acquisitionCost, acquisitionCost);
  }

  private static ResolvedInventoryCosting resolvedInventoryCosting(
      MonetaryAmount amount, dev.erst.fingrind.contract.bookkeeping.QuantityText quantity) {
    Quantity resolvedQuantity = resolvedQuantity(quantity);
    return new ResolvedInventoryCosting(amount.toMoney(), resolvedQuantity, amount.toMoney());
  }

  private static ResolvedInventoryDisposal resolvedInventoryDisposal(
      dev.erst.fingrind.contract.bookkeeping.QuantityText quantity) {
    Quantity resolvedQuantity = resolvedQuantity(quantity);
    Money carryingCost = Money.parse("EUR", "12.50");
    return new ResolvedInventoryDisposal(carryingCost, resolvedQuantity, carryingCost);
  }

  private static Quantity resolvedQuantity(
      dev.erst.fingrind.contract.bookkeeping.QuantityText quantity) {
    String value = quantity.value();
    int decimalPointIndex = value.indexOf('.');
    int scale = decimalPointIndex < 0 ? 0 : value.length() - decimalPointIndex - 1;
    return quantity.resolve(new UnitOfMeasure("fixture-unit", scale));
  }

  private static CommittedProvenance committedProvenance(String token) {
    return new CommittedProvenance(
        new dev.erst.fingrind.core.RequestProvenance(
            new CommandId("command-" + token),
            new IdempotencyKey("idem-" + token),
            new CausationId("cause-" + token),
            Optional.of(new CorrelationId("corr-" + token))),
        Instant.parse("2026-04-07T10:15:30Z"),
        dev.erst.fingrind.core.SourceChannel.CLI);
  }

  private static ForeignExchangeDetails foreignExchangeDetails() {
    return new ForeignExchangeDetails(
        new MonetaryAmount("USD", "10000"),
        new MonetaryAmount("EUR", "9200"),
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "10000"),
            new MonetaryAmount("EUR", "9200"),
            LocalDate.parse("2026-04-06"),
            "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static ForeignExchangeDetails reversalForeignExchangeDetails() {
    return new ForeignExchangeDetails(
        new MonetaryAmount("USD", "1360"),
        new MonetaryAmount("EUR", "1250"),
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "1360"),
            new MonetaryAmount("EUR", "1250"),
            LocalDate.parse("2026-04-06"),
            "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static TaxSelection taxSelection(String taxCode) {
    return new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode(taxCode));
  }

  private static AppliedTax appliedSaleTax(String taxCode, String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode(taxCode),
        new TaxCodeName("VAT Standard Sale"),
        new TaxRate(210_000),
        TaxInclusionMode.EXCLUSIVE,
        TaxApplicationKind.OUTPUT_SALE,
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "210"),
        new MonetaryAmount("EUR", "1210"),
        new AccountCode(taxAccountCode));
  }

  private static AppliedTax appliedExpenseTax(String taxCode, String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode(taxCode),
        new TaxCodeName("VAT Standard Expense"),
        new TaxRate(210_000),
        TaxInclusionMode.INCLUSIVE,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "210"),
        new MonetaryAmount("EUR", "1210"),
        new AccountCode(taxAccountCode));
  }

  private static SettlementAdjunct settlementAdjunct(String accountCode, String minorUnits) {
    return new SettlementAdjunct(
        new AccountCode(accountCode), new MonetaryAmount("EUR", minorUnits));
  }
}
