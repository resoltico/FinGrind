package dev.erst.fingrind.sqlite;

import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.accountTaxonomy;
import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.generatedEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AcceptedPosting;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Durable aggregate and compensating-lifecycle coverage for accrual cut-offs. */
class SqliteAccrualCutoffPersistenceTest extends SqlitePostingFactStoreTestSupport {
  private static final Instant RECORDED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode PREPAID_EXPENSE = new AccountCode("1410");
  private static final AccountCode DEFERRED_REVENUE = new AccountCode("2300");
  private static final AccountCode ACCRUED_EXPENSE = new AccountCode("2200");
  private static final AccountCode EXPENSE = new AccountCode("5100");
  private static final AccountCode REVENUE = new AccountCode("4000");
  private static final AccrualCutoffId CUTOFF_ID = new AccrualCutoffId("annual-insurance-2026");
  private static final AccrualCutoffId DEFERRED_CUTOFF_ID =
      new AccrualCutoffId("annual-support-2026");
  private static final AccrualCutoffId ACCRUED_CUTOFF_ID =
      new AccrualCutoffId("annual-hosting-2026");

  @Test
  void persistence_keepsAccrualCutoffLifecycleAppendOnlyAndCompensating() {
    Path bookPath = tempDirectory.resolve("accrual-cutoff-persistence.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(store);
      declareAccount(
          store,
          PREPAID_EXPENSE,
          "Prepaid Insurance",
          AccountType.ASSET,
          financialPositionTaxonomy(FinancialPositionLineClassification.PREPAID_EXPENSE));
      declareAccount(
          store,
          EXPENSE,
          "Insurance Expense",
          AccountType.EXPENSE,
          accountTaxonomy(AccountType.EXPENSE));

      StoreOwnedDatabase database = new StoreOwnedDatabase(requireStoreDatabase(store));
      SqliteAcceptedPostingPersistence persistence =
          new SqliteAcceptedPostingPersistence(SqliteCommitFaultHook.NONE);

      AccrualCutoffBookkeepingEntryVariants.Prepayment originEntry = prepayment();
      CommittedPosting origin =
          persist(
              persistence,
              database.value(),
              accepted(
                  originEntry,
                  originEntry,
                  originEntry.journalEntry(),
                  PostingLineageModel.direct(),
                  PostingOriginKind.PREPAYMENT,
                  "origin"),
              "posting-cutoff-origin");
      assertEquals(1, queryInt(database.value(), "select count(*) from accrual_cutoff"));
      assertEquals(
          0, queryInt(database.value(), "select count(*) from accrual_cutoff_application"));

      AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition callerRecognition =
          new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
              LocalDate.parse("2026-04-15"), CUTOFF_ID, amount("25.00"), null);
      AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition resolvedRecognition =
          new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
              LocalDate.parse("2026-04-15"),
              CUTOFF_ID,
              amount("25.00"),
              new ResolvedAccrualCutoffApplication(
                  dev.erst.fingrind.core.AccrualCutoffKind.PREPAYMENT,
                  dev.erst.fingrind.core.AccrualCutoffApplicationKind.RECOGNITION,
                  EXPENSE,
                  PREPAID_EXPENSE));
      CommittedPosting recognition =
          persist(
              persistence,
              database.value(),
              accepted(
                  callerRecognition,
                  resolvedRecognition,
                  resolvedRecognition.journalEntry(),
                  PostingLineageModel.direct(),
                  PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION,
                  "recognition"),
              "posting-cutoff-recognition");
      assertCutoffState(database.value(), "25.00", "75.00", "2026-04-15");

      CommittedPosting recognitionReversal =
          persistReversal(
              persistence,
              database.value(),
              recognition,
              "posting-cutoff-recognition-reversal",
              "recognition-reversal",
              LocalDate.parse("2026-04-15"));
      assertEquals(
          new PostingId("efd0b9a9-a8bd-37b4-b60d-f0716f8d5503"), recognitionReversal.postingId());
      assertCutoffState(database.value(), "0.00", "100.00", "2026-04-15");

      CommittedPosting originReversal =
          persistReversal(
              persistence,
              database.value(),
              origin,
              "posting-cutoff-origin-reversal",
              "origin-reversal",
              LocalDate.parse("2026-04-15"));
      assertEquals(
          new PostingId("58ff5c55-1e23-30b9-8910-ee2c966a36e9"), originReversal.postingId());
      assertCutoffState(database.value(), "100.00", "0.00", "2026-04-15");
      assertEquals(
          3, queryInt(database.value(), "select count(*) from accrual_cutoff_application"));
      assertEquals(
          "RECOGNITION:2500;APPLICATION_REVERSAL:-2500;ORIGIN_REVERSAL:10000",
          queryText(
              database.value(),
              """
              select group_concat(entry, ';')
              from (
                  select application_kind || ':' || cast(amount_minor as text) as entry
                  from accrual_cutoff_application
                  order by rowid
              )
              """));

      SqliteNativeException updateFailure =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  database
                      .value()
                      .executeStatement("update accrual_cutoff set originated_on = '2026-04-08'"));
      assertEquals(SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), updateFailure.resultCode());
      SqliteNativeException deleteFailure =
          assertThrows(
              SqliteNativeException.class,
              () -> database.value().executeStatement("delete from accrual_cutoff_application"));
      assertEquals(SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), deleteFailure.resultCode());
    }
  }

  @Test
  void persistence_rehydratesEveryAccrualCutoffKind_andExposesAsOfAggregateState() {
    Path bookPath = tempDirectory.resolve("accrual-cutoff-variants.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(store);
      declareAccrualAccounts(store);

      StoreOwnedDatabase database = new StoreOwnedDatabase(requireStoreDatabase(store));
      SqliteAcceptedPostingPersistence persistence =
          new SqliteAcceptedPostingPersistence(SqliteCommitFaultHook.NONE);
      AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue = deferredRevenue();
      AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense = accruedExpense();
      CommittedPosting deferredOrigin =
          persist(
              persistence,
              database.value(),
              accepted(
                  deferredRevenue,
                  deferredRevenue,
                  deferredRevenue.journalEntry(),
                  PostingLineageModel.direct(),
                  PostingOriginKind.DEFERRED_REVENUE,
                  "deferred-origin"),
              "posting-deferred-origin");
      CommittedPosting accruedOrigin =
          persist(
              persistence,
              database.value(),
              accepted(
                  accruedExpense,
                  accruedExpense,
                  accruedExpense.journalEntry(),
                  PostingLineageModel.direct(),
                  PostingOriginKind.ACCRUED_EXPENSE,
                  "accrued-origin"),
              "posting-accrued-origin");

      CommittedPosting deferredRecognition =
          persist(
              persistence, database.value(), deferredRecognition(), "posting-deferred-recognition");
      CommittedPosting accruedSettlement =
          persist(persistence, database.value(), accruedSettlement(), "posting-accrued-settlement");

      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.DeferredRevenue.class,
          store
              .findPosting(deferredOrigin.postingId())
              .orElseThrow()
              .callerAuthoredEntry()
              .orElseThrow());
      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.AccruedExpense.class,
          store
              .findPosting(accruedOrigin.postingId())
              .orElseThrow()
              .callerAuthoredEntry()
              .orElseThrow());
      assertResolvedAccrualEntry(
          store.findPosting(deferredRecognition.postingId()).orElseThrow(),
          AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition.class);
      assertResolvedAccrualEntry(
          store.findPosting(accruedSettlement.postingId()).orElseThrow(),
          AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement.class);
      List<AccrualCutoffRecord> asOfOrigins =
          SqliteAccrualCutoffStatementQueries.loadCutoffs(
              database.value(), Optional.of(LocalDate.parse("2026-04-10")));
      assertEquals(2, asOfOrigins.size());
      assertEquals(Money.parse("EUR", "0.00"), asOfOrigins.getFirst().appliedAmount());
      List<AccrualCutoffRecord> current =
          SqliteAccrualCutoffStatementQueries.loadCutoffs(database.value(), Optional.empty());
      assertEquals(2, current.size());
      assertEquals(Money.parse("EUR", "40.00"), current.getFirst().appliedAmount());
      assertEquals(Money.parse("EUR", "50.00"), current.getLast().appliedAmount());
      assertTrue(
          SqliteAccrualCutoffStatementQueries.findCutoff(
                  database.value(), new AccrualCutoffId("missing-cutoff"))
              .isEmpty());
      assertTrue(
          SqliteAccrualCutoffStatementQueries.findApplicationContext(
                  database.value(), new PostingId("c5516148-21eb-31e3-b0a1-467a7231878c"))
              .isEmpty());
      assertTrue(
          SqliteAccrualCutoffStatementQueries.findApplicationReversalInput(
                  database.value(), new PostingId("c5516148-21eb-31e3-b0a1-467a7231878c"))
              .isEmpty());

      BookkeepingEntry.DirectJournal directJournal =
          new BookkeepingEntry.DirectJournal(
              accrualJournalEntry(LocalDate.parse("2026-04-22")), null);
      persist(
          persistence,
          database.value(),
          accepted(
              directJournal,
              directJournal,
              directJournal.journalEntry(),
              PostingLineageModel.direct(),
              PostingOriginKind.DIRECT_JOURNAL,
              "direct-journal"),
          "posting-direct-journal");
      assertOpeningPositionFactColumnsAreEmpty(database.value());
      assertInstanceOf(
          BookkeepingEntry.DirectJournal.class,
          store
              .findPosting(new PostingId("8842f480-bcb5-3ef1-91fc-665b6988f136"))
              .orElseThrow()
              .callerAuthoredEntry()
              .orElseThrow());
      SqliteAccrualCutoffWriter.persist(
          database.value(),
          new CommittedPosting(
              new PostingId("d7acb527-0da2-35c8-b2d4-4167ab1cde92"),
              deferredRevenue.journalEntry(),
              PostingLineageModel.direct(),
              PostingKind.STANDARD,
              PostingOriginKind.DIRECT_JOURNAL,
              generatedEvidence("no-caller-entry", "journal-support"),
              committedProvenance("no-caller-entry")));
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(database.value(), store.postingReader());
      assertEquals(
          DEFERRED_CUTOFF_ID,
          validationBook.findAccrualCutoff(DEFERRED_CUTOFF_ID).orElseThrow().accrualCutoffId());
      try (SqlitePostingCapabilitySession capabilitySession =
          new SqlitePostingCapabilitySession(store)) {
        assertEquals(
            DEFERRED_CUTOFF_ID,
            capabilitySession
                .findAccrualCutoff(DEFERRED_CUTOFF_ID)
                .orElseThrow()
                .accrualCutoffId());
        assertEquals(2, capabilitySession.accrualCutoffs(Optional.empty()).size());
      }
    }
  }

  @Test
  void persistence_rejectsInconsistentAccrualCutoffFactsAndRebuildsEveryDurableVariant()
      throws Exception {
    Path bookPath = tempDirectory.resolve("accrual-cutoff-mapper-guards.sqlite");
    try (SqliteNativeDatabase database = openNativeDatabase(bookAccess(bookPath))) {
      database.executeScript(
          """
          create table accrual_cutoff (
              accrual_cutoff_id text primary key,
              kind text not null,
              origin_posting_id text not null,
              originated_on text not null,
              cutoff_account_code text not null,
              recognition_account_code text not null,
              amount_currency_code text not null,
              original_amount_minor integer not null,
              recognition_start_date text,
              recognition_end_date text
          );
          create table accrual_cutoff_application (
              application_posting_id text primary key,
              accrual_cutoff_id text not null,
              application_kind text not null,
              effective_date text not null,
              amount_currency_code text not null,
              amount_minor integer not null
          );
          insert into accrual_cutoff values
              ('prepayment', 'PREPAYMENT', '%s', '2026-04-01', '1410', '5100', 'EUR', 10000, '2026-04-01', '2026-04-30'),
              ('deferred-revenue', 'DEFERRED_REVENUE', '%s', '2026-04-01', '2300', '4000', 'EUR', 10000, '2026-04-01', '2026-04-30'),
              ('accrued-expense', 'ACCRUED_EXPENSE', '%s', '2026-04-01', '2200', '5100', 'EUR', 10000, null, null);
          insert into accrual_cutoff_application values
              ('%s', 'prepayment', 'RECOGNITION', '2026-04-02', 'EUR', 1000),
              ('%s', 'deferred-revenue', 'RECOGNITION', '2026-04-02', 'EUR', 1000),
              ('%s', 'accrued-expense', 'RECOGNITION', '2026-04-02', 'EUR', 1000),
              ('%s', 'prepayment', 'SETTLEMENT', '2026-04-02', 'EUR', 1000),
              ('%s', 'accrued-expense', 'SETTLEMENT', '2026-04-02', 'EUR', 1000),
              ('%s', 'missing-cutoff', 'RECOGNITION', '2026-04-02', 'EUR', 1000),
              ('%s', 'missing-cutoff', 'SETTLEMENT', '2026-04-02', 'EUR', 1000);
          """
              .formatted(
                  SqliteTestPostingIds.valueForLabel("origin-prepayment"),
                  SqliteTestPostingIds.valueForLabel("origin-deferred"),
                  SqliteTestPostingIds.valueForLabel("origin-accrued"),
                  SqliteTestPostingIds.valueForLabel("recognition-prepayment"),
                  SqliteTestPostingIds.valueForLabel("recognition-deferred"),
                  SqliteTestPostingIds.valueForLabel("recognition-accrued"),
                  SqliteTestPostingIds.valueForLabel("settlement-prepayment"),
                  SqliteTestPostingIds.valueForLabel("settlement-accrued"),
                  SqliteTestPostingIds.valueForLabel("missing-cutoff-recognition"),
                  SqliteTestPostingIds.valueForLabel("missing-cutoff-settlement")));

      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.Prepayment.class,
          mapAccrualOrigin(database, "origin-prepayment", PostingOriginKind.PREPAYMENT));
      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.DeferredRevenue.class,
          mapAccrualOrigin(database, "origin-deferred", PostingOriginKind.DEFERRED_REVENUE));
      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.AccruedExpense.class,
          mapAccrualOrigin(database, "origin-accrued", PostingOriginKind.ACCRUED_EXPENSE));
      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition.class,
          mapAccrualOrigin(
              database, "recognition-prepayment", PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION));
      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement.class,
          mapAccrualOrigin(
              database, "settlement-accrued", PostingOriginKind.ACCRUED_EXPENSE_SETTLEMENT));

      assertThrows(
          IllegalStateException.class,
          () -> mapAccrualOrigin(database, "missing-origin", PostingOriginKind.PREPAYMENT));
      assertThrows(
          IllegalStateException.class,
          () ->
              mapAccrualOrigin(database, "origin-prepayment", PostingOriginKind.DEFERRED_REVENUE));
      assertThrows(
          IllegalStateException.class,
          () ->
              mapAccrualOrigin(
                  database, "missing-application", PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION));
      assertThrows(
          IllegalStateException.class,
          () ->
              mapAccrualOrigin(
                  database,
                  "recognition-prepayment",
                  PostingOriginKind.ACCRUED_EXPENSE_SETTLEMENT));
      assertThrows(
          IllegalStateException.class,
          () ->
              mapAccrualOrigin(
                  database, "settlement-accrued", PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION));

      AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition =
          new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
              LocalDate.parse("2026-04-02"),
              new AccrualCutoffId("prepayment"),
              amount("10.00"),
              null);
      AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement =
          new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
              LocalDate.parse("2026-04-02"),
              new AccrualCutoffId("accrued-expense"),
              CASH,
              amount("10.00"),
              null);
      assertNull(
          SqliteResolvedAccrualCutoffApplicationReader.resolve(
              database, new PostingId("c5516148-21eb-31e3-b0a1-467a7231878c"), recognition));
      assertNull(
          SqliteResolvedAccrualCutoffApplicationReader.resolve(
              database, new PostingId("1cf9cc40-3e16-3d80-a820-f87fb8c7f7f3"), recognition));
      assertNull(
          SqliteResolvedAccrualCutoffApplicationReader.resolve(
              database, new PostingId("84c51206-a015-35e1-8445-2ed5e29427fb"), settlement));
      assertNull(
          SqliteResolvedAccrualCutoffApplicationReader.resolve(
              database, new PostingId("c5516148-21eb-31e3-b0a1-467a7231878c"), settlement));
      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition.class,
          SqliteResolvedAccrualCutoffApplicationReader.resolve(
              database, new PostingId("84c51206-a015-35e1-8445-2ed5e29427fb"), recognition));
      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition.class,
          SqliteResolvedAccrualCutoffApplicationReader.resolve(
              database, new PostingId("4ee7bd24-7429-3e24-a4f7-3ae5dddc2e41"), recognition));
      assertInstanceOf(
          AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement.class,
          SqliteResolvedAccrualCutoffApplicationReader.resolve(
              database, new PostingId("1cf9cc40-3e16-3d80-a820-f87fb8c7f7f3"), settlement));
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteResolvedAccrualCutoffApplicationReader.resolve(
                  database, new PostingId("d117e5f6-6685-3520-a7b1-e22744e3b1d8"), recognition));
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteResolvedAccrualCutoffApplicationReader.resolve(
                  database, new PostingId("8f902d73-a74d-3b05-8450-aaf66ba43643"), settlement));
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteResolvedAccrualCutoffApplicationReader.resolve(
                  database, new PostingId("9202895f-977a-3896-b399-e66e63eb240a"), recognition));
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteResolvedAccrualCutoffApplicationReader.resolve(
                  database, new PostingId("519eba04-644b-3858-a3e7-3528313e65c2"), settlement));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new SqliteAccrualCutoffStatementQueries.ApplicationReversalInput(
                  new AccrualCutoffId("prepayment"), Money.parse("EUR", "0.00")));
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteAccrualCutoffOriginatingEntryFactValues.originatingEntryFactValues(
                  recognition));
    }
  }

  private static void assertResolvedAccrualEntry(
      CommittedPosting posting, Class<? extends BookkeepingEntry> expectedType) {
    assertInstanceOf(expectedType, posting.callerAuthoredEntry().orElseThrow());
    assertInstanceOf(expectedType, posting.resolvedOriginatingEntry().orElseThrow());
  }

  private static BookkeepingEntry mapAccrualOrigin(
      SqliteNativeDatabase database, String postingId, PostingOriginKind postingOriginKind) {
    try (SqliteNativeStatement postingRow =
        database.prepare(accrualPostingProjectionSql(postingId))) {
      assertEquals(SqliteNativeResultCode.code("ROW"), postingRow.step());
      return Objects.requireNonNull(
          SqliteAccrualCutoffOriginatingEntryMapper.originatingEntry(
              database,
              new PostingId(
                  java.util
                      .UUID
                      .nameUUIDFromBytes(
                          ("fingrind-test-postingid:" + postingId)
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                      .toString()),
              postingRow,
              accrualJournalEntry(
                  postingId.startsWith("origin-")
                      ? LocalDate.parse("2026-04-01")
                      : LocalDate.parse("2026-04-02")),
              postingOriginKind));
    }
  }

  private static void assertOpeningPositionFactColumnsAreEmpty(SqliteNativeDatabase database) {
    BookkeepingEntry.OpeningPosition openingPosition =
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-01"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    CASH, JournalLine.EntrySide.DEBIT, amount("10.00"), null),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    DEFERRED_REVENUE, JournalLine.EntrySide.CREDIT, amount("10.00"), null)));
    assertOrdinaryFactColumnsAreEmpty(database, openingPosition);
    BookkeepingEntry.Reversal reversal =
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-02"),
            new PostingLineage.Reversal(
                new ReversalReference(new PostingId("60bdb11d-299e-3a3e-ba72-9193afd14b09")),
                new ReversalReason("operator correction")),
            null,
            accrualJournalEntry(LocalDate.parse("2026-04-02")));
    assertOrdinaryFactColumnsAreEmpty(database, reversal);
  }

  private static void assertOrdinaryFactColumnsAreEmpty(
      SqliteNativeDatabase database, BookkeepingEntry entry) {
    try (SqliteNativeStatement statement =
        database.prepare("select ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12")) {
      SqliteOriginatingEntryFactMapper.bindOriginatingEntry(statement, entry, entry);
      assertEquals(SqliteNativeResultCode.code("ROW"), statement.step());
      assertNull(statement.columnText(0));
      assertNull(statement.columnText(8));
    }
  }

  private static JournalEntry accrualJournalEntry(LocalDate effectiveDate) {
    return new JournalEntry(
        effectiveDate,
        List.of(
            new JournalLine(CASH, JournalLine.EntrySide.DEBIT, Money.parse("EUR", "10.00")),
            new JournalLine(REVENUE, JournalLine.EntrySide.CREDIT, Money.parse("EUR", "10.00"))));
  }

  private static String accrualPostingProjectionSql(String postingId) {
    return """
        select
            '%s', 'STANDARD', 'DIRECT_JOURNAL', '1000', '1000', null, 'EUR', 1000,
            null, null, null, null, '2026-04-02', '2026-04-07T10:15:30Z', 'actor-1',
            'AGENT', 'command-1', 'idempotency-1', 'cause-1', null, null, 'CLI', null,
            null, null
        """
        .formatted(postingId);
  }

  private static void declareAccrualAccounts(SqlitePostingFactStore store) {
    declareAccount(
        store,
        PREPAID_EXPENSE,
        "Prepaid Insurance",
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.PREPAID_EXPENSE));
    declareAccount(
        store,
        DEFERRED_REVENUE,
        "Deferred Support Revenue",
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.DEFERRED_REVENUE));
    declareAccount(
        store,
        ACCRUED_EXPENSE,
        "Accrued Hosting Expense",
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.ACCRUED_EXPENSE));
    declareAccount(
        store,
        EXPENSE,
        "Insurance Expense",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
    declareAccount(
        store,
        REVENUE,
        "Support Revenue",
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE));
  }

  private static AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue() {
    return new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
        LocalDate.parse("2026-04-08"),
        DEFERRED_CUTOFF_ID,
        CASH,
        DEFERRED_REVENUE,
        REVENUE,
        amount("120.00"),
        new AccrualCutoffRecognitionInterval(
            LocalDate.parse("2026-04-10"), LocalDate.parse("2026-05-31")));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense() {
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
        LocalDate.parse("2026-04-09"),
        ACCRUED_CUTOFF_ID,
        EXPENSE,
        ACCRUED_EXPENSE,
        amount("80.00"));
  }

  private static AcceptedPosting deferredRecognition() {
    AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition caller =
        new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
            LocalDate.parse("2026-04-20"), DEFERRED_CUTOFF_ID, amount("40.00"), null);
    AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition resolved =
        new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
            caller.effectiveDate(),
            caller.accrualCutoffId(),
            caller.amount(),
            new ResolvedAccrualCutoffApplication(
                dev.erst.fingrind.core.AccrualCutoffKind.DEFERRED_REVENUE,
                dev.erst.fingrind.core.AccrualCutoffApplicationKind.RECOGNITION,
                DEFERRED_REVENUE,
                REVENUE));
    return accepted(
        caller,
        resolved,
        resolved.journalEntry(),
        PostingLineageModel.direct(),
        PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION,
        "deferred-recognition");
  }

  private static AcceptedPosting accruedSettlement() {
    AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement caller =
        new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
            LocalDate.parse("2026-04-21"), ACCRUED_CUTOFF_ID, CASH, amount("50.00"), null);
    AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement resolved =
        new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
            caller.effectiveDate(),
            caller.accrualCutoffId(),
            caller.cashAccountCode(),
            caller.amount(),
            new ResolvedAccrualCutoffApplication(
                dev.erst.fingrind.core.AccrualCutoffKind.ACCRUED_EXPENSE,
                dev.erst.fingrind.core.AccrualCutoffApplicationKind.SETTLEMENT,
                ACCRUED_EXPENSE,
                CASH));
    return accepted(
        caller,
        resolved,
        resolved.journalEntry(),
        PostingLineageModel.direct(),
        PostingOriginKind.ACCRUED_EXPENSE_SETTLEMENT,
        "accrued-settlement");
  }

  private static void declareAccount(
      SqlitePostingFactStore store,
      AccountCode accountCode,
      String accountName,
      AccountType accountType,
      dev.erst.fingrind.core.AccountTaxonomy taxonomy) {
    store.declareAccount(
        new AccountDeclaration(accountCode, new AccountName(accountName), accountType, taxonomy),
        RECORDED_AT,
        SqliteAttestationTestSupport.authorizer());
  }

  /** Borrowed native handle whose lifecycle remains owned by the enclosing posting-fact store. */
  private record StoreOwnedDatabase(SqliteNativeDatabase value) {}

  private static CommittedPosting persist(
      SqliteAcceptedPostingPersistence persistence,
      SqliteNativeDatabase database,
      AcceptedPosting acceptedPosting,
      String postingId) {
    return persistence.persistAcceptedPosting(
        database,
        acceptedPosting,
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
        committedProvenance(postingId),
        () ->
            new PostingId(
                java.util
                    .UUID
                    .nameUUIDFromBytes(
                        ("fingrind-test-postingid:" + postingId)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString()));
  }

  private static CommittedPosting persistReversal(
      SqliteAcceptedPostingPersistence persistence,
      SqliteNativeDatabase database,
      CommittedPosting priorPosting,
      String postingId,
      String token,
      LocalDate effectiveDate) {
    JournalEntry reversalJournal = negate(priorPosting.journalEntry(), effectiveDate);
    ReversalReference reference = new ReversalReference(priorPosting.postingId());
    ReversalReason reason = new ReversalReason("operator correction");
    BookkeepingEntry.Reversal reversalEntry =
        new BookkeepingEntry.Reversal(
            reversalJournal.effectiveDate(),
            new PostingLineage.Reversal(reference, reason),
            null,
            reversalJournal);
    return persist(
        persistence,
        database,
        accepted(
            reversalEntry,
            reversalEntry,
            reversalJournal,
            PostingLineageModel.reversal(reference, reason),
            PostingOriginKind.REVERSAL,
            token),
        postingId);
  }

  private static AcceptedPosting accepted(
      BookkeepingEntry callerEntry,
      BookkeepingEntry resolvedEntry,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingOriginKind postingOriginKind,
      String token) {
    return new AcceptedPosting(
        journalEntry,
        postingLineage,
        PostingKind.STANDARD,
        postingOriginKind,
        generatedEvidence(token, "prepayment-invoice"),
        requestProvenance(token),
        SourceChannel.CLI,
        callerEntry,
        resolvedEntry,
        List.of(),
        Map.of());
  }

  private static RequestProvenance requestProvenance(String token) {
    return new RequestProvenance(
        SqliteTestCommandIds.fromLabel("command-" + token),
        new IdempotencyKey("idempotency-" + token),
        new CausationId("cause-" + token),
        Optional.of(new CorrelationId("correlation-" + token)));
  }

  private static CommittedProvenance committedProvenance(String postingId) {
    return new CommittedProvenance(requestProvenance(postingId), RECORDED_AT, SourceChannel.CLI);
  }

  private static AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment() {
    return new AccrualCutoffBookkeepingEntryVariants.Prepayment(
        LocalDate.parse("2026-04-07"),
        CUTOFF_ID,
        PREPAID_EXPENSE,
        EXPENSE,
        CASH,
        amount("100.00"),
        new AccrualCutoffRecognitionInterval(
            LocalDate.parse("2026-04-10"), LocalDate.parse("2026-05-31")));
  }

  private static MonetaryAmount amount(String decimalAmount) {
    return MonetaryAmount.of(Money.parse("EUR", decimalAmount));
  }

  private static JournalEntry negate(JournalEntry original, LocalDate effectiveDate) {
    return new JournalEntry(
        effectiveDate,
        original.lines().stream()
            .map(
                line ->
                    new JournalLine(
                        line.accountCode(),
                        line.side() == JournalLine.EntrySide.DEBIT
                            ? JournalLine.EntrySide.CREDIT
                            : JournalLine.EntrySide.DEBIT,
                        line.amount()))
            .toList());
  }

  private static void assertCutoffState(
      SqliteNativeDatabase database,
      String expectedAppliedAmount,
      String expectedRemainingAmount,
      String expectedHorizonDate) {
    AccrualCutoffRecord.Prepayment cutoff =
        (AccrualCutoffRecord.Prepayment)
            SqliteAccrualCutoffStatementQueries.findCutoff(database, CUTOFF_ID).orElseThrow();
    assertEquals(Money.parse("EUR", expectedAppliedAmount), cutoff.appliedAmount());
    assertEquals(Money.parse("EUR", expectedRemainingAmount), cutoff.remainingAmount());
    assertEquals(
        Optional.of(LocalDate.parse(expectedHorizonDate)), cutoff.latestApplicationEffectiveDate());
  }
}
