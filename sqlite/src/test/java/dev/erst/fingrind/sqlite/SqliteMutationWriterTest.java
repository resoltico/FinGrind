package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
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
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
                          new MonetaryAmount("EUR", "1250")),
                      new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                          new AccountCode("3000"),
                          dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                          new MonetaryAmount("EUR", "1250")))));
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
                  new MonetaryAmount("EUR", "1250"),
                  null));
          assertRoundTripRetainedEntry(
              database,
              "posting-purchase-on-credit",
              new BookkeepingEntry.PurchaseOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("1400"),
                  new AccountCode("2100"),
                  new MonetaryAmount("EUR", "1250")));
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
                      new MonetaryAmount("EUR", "400")),
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
                      new MonetaryAmount("EUR", "400")),
                  taxSelection("vat-standard-sale"),
                  appliedSaleTax("vat-standard-sale", "2100")));
          assertRoundTripRetainedEntry(
              database,
              "posting-expense-on-credit-taxed",
              new BookkeepingEntry.ExpenseOnCredit(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("5000"),
                  new AccountCode("2100"),
                  new MonetaryAmount("EUR", "1210"),
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
    String token = postingId;
    dev.erst.fingrind.executor.bookkeeping.PostingLineageModel postingLineage =
        entry instanceof BookkeepingEntry.Reversal reversal
            ? dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.reversal(
                reversal.reversal().reference(), reversal.reversal().reason())
            : dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct();
    CommittedPosting posting =
        new CommittedPosting(
            new PostingId(postingId),
            entry.journalEntry(),
            postingLineage,
            entry.postingKind(),
            entry.postingOriginKind(),
            SqlitePostingFactFixtureSupport.accountingEvidence(token),
            committedProvenance(token),
            entry);
    RequestFingerprint requestFingerprint =
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64));

    SqliteMutationWriter.insertPostingFact(database, posting, requestFingerprint);
    SqliteMutationWriter.insertJournalLines(database, posting, SqliteCommitFaultHook.NONE);
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

  private static CommittedProvenance committedProvenance(String token) {
    return new CommittedProvenance(
        new dev.erst.fingrind.core.RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
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
        ForeignExchangeTreatmentKind.SPOT_SETTLEMENT);
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
        ForeignExchangeTreatmentKind.SPOT_SETTLEMENT);
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
