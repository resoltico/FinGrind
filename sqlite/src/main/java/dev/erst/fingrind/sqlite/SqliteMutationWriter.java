package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.SweptInterimResult;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared SQLite write helpers for persisted book metadata, accounts, and postings. */
final class SqliteMutationWriter {
  private SqliteMutationWriter() {}

  static void insertInitializedAt(SqliteNativeDatabase activeDatabase, Instant initializedAt) {
    insertBookMetaValue(
        activeDatabase,
        SqlitePostingSql.INITIALIZED_AT_META_KEY,
        CanonicalTemporalText.formatUtcInstant(initializedAt));
  }

  static void insertBookIdentity(SqliteNativeDatabase activeDatabase, BookIdentity bookIdentity) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_BOOK_IDENTITY)) {
      statement.bindText(1, bookIdentity.entityName().value());
      statement.bindText(2, bookIdentity.bookDoctrine().accountingKernelProfileId().value());
      statement.bindText(3, bookIdentity.bookDoctrine().accountingBasis().wireValue());
      statement.bindText(4, bookIdentity.bookDoctrine().accountingFrameworkPosition().wireValue());
      statement.bindText(5, bookIdentity.bookDoctrine().entityForm().wireValue());
      statement.bindText(6, bookIdentity.bookDoctrine().bookTemplateId().wireValue());
      bindOptionalText(
          statement,
          7,
          bookIdentity.bookDoctrine().inventoryCostingDoctrine() == null
              ? null
              : bookIdentity.bookDoctrine().inventoryCostingDoctrine().wireValue());
      statement.bindText(8, bookIdentity.functionalCurrency().code());
      statement.bindInt(9, bookIdentity.fiscalYearStart().month());
      statement.bindInt(10, bookIdentity.fiscalYearStart().day());
      statement.bindText(
          11, CanonicalTemporalText.formatLocalDate(bookIdentity.bookStartEffectiveDate()));
      statement.step();
    }
  }

  static void insertBookMetaValue(
      SqliteNativeDatabase activeDatabase, String metaKey, String value) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_BOOK_META_VALUE)) {
      statement.bindText(1, metaKey);
      statement.bindText(2, value);
      statement.step();
    }
  }

  static void upsertTaxRegistration(
      SqliteNativeDatabase activeDatabase, DeclaredTaxRegistration registration) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteTaxSql.UPSERT_TAX_REGISTRATION)) {
      statement.bindText(1, registration.taxRegistrationId().value());
      statement.bindText(2, registration.taxRegistrationName().value());
      statement.bindText(3, registration.jurisdiction().value());
      bindOptionalText(
          statement,
          4,
          registration.registrationNumber() == null
              ? null
              : registration.registrationNumber().value());
      statement.bindText(5, registration.payableAccountCode().value());
      statement.bindText(6, registration.recoverableAccountCode().value());
      statement.bindText(7, registration.obligationFrequency().wireValue());
      statement.bindInt(8, registration.dueDaysAfterPeriodEnd());
      statement.bindText(9, CanonicalTemporalText.formatUtcInstant(registration.declaredAt()));
      statement.step();
    }
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteTaxSql.DELETE_TAX_CODES_FOR_REGISTRATION)) {
      statement.bindText(1, registration.taxRegistrationId().value());
      statement.step();
    }
    for (var taxCode : registration.taxCodes()) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqliteTaxSql.INSERT_TAX_REGISTRATION_CODE)) {
        statement.bindText(1, registration.taxRegistrationId().value());
        statement.bindText(2, taxCode.taxCode().value());
        statement.bindText(3, taxCode.taxCodeName().value());
        statement.bindInt(4, taxCode.rate().partsPerMillionOfWhole());
        statement.bindText(5, taxCode.inclusionMode().wireValue());
        statement.bindText(6, taxCode.applicationKind().wireValue());
        statement.step();
      }
    }
  }

  static void insertPostingFact(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      RequestFingerprint requestFingerprint) {
    SqlitePostingFactWriter.insertPostingFact(activeDatabase, postingFact, requestFingerprint);
  }

  static void insertJournalLines(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      SqliteCommitFaultHook commitFaultHook) {
    preparePendingJournalLineTable(activeDatabase);
    clearPendingJournalLineTable(activeDatabase);
    List<JournalLine> lines = postingFact.journalEntry().lines();
    for (int index = 0; index < lines.size(); index++) {
      JournalLine line = lines.get(index);
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_PENDING_JOURNAL_LINE)) {
        statement.bindInt(1, index);
        statement.bindText(2, line.accountCode().value());
        statement.bindText(3, line.side().wireValue());
        SqlitePersistedMoneyCodec.bindPositiveMoney(statement, 4, 5, line.amount());
        statement.step();
      }
    }
    requireBalancedPendingJournalLineTable(activeDatabase);
    commitFaultHook.beforePersistJournalLines(postingFact);
    persistPendingJournalLineTable(activeDatabase, postingFact.postingId().value());
    clearPendingJournalLineTable(activeDatabase);
  }

  static SweptInterimResult insertInterimResultSweep(
      SqliteNativeDatabase activeDatabase,
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      AccountCode resultHoldingAccountCode,
      List<CurrencyBalance> sweptTotals,
      Instant sweptAt,
      List<CommittedPosting> closingPostings) {
    int sweepOrder;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteReportingPeriodCloseSql.INSERT_PERIOD_RESULT_TRANSFER)) {
      statement.bindText(
          1, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateFrom()));
      statement.bindText(
          2, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateTo()));
      statement.bindText(3, resultHoldingAccountCode.value());
      statement.bindText(4, CanonicalTemporalText.formatUtcInstant(sweptAt));
      if (statement.step() != SqliteNativeResultCode.code("ROW")) {
        throw new IllegalStateException(
            "SQLite interim-result sweep insert returned no sweep order.");
      }
      sweepOrder = statement.columnInt(0);
      if (statement.step() != SqliteNativeResultCode.code("DONE")) {
        throw new IllegalStateException(
            "SQLite interim-result sweep insert returned more than one sweep order.");
      }
    }
    for (CurrencyBalance closedTotal : sweptTotals) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(
              SqliteReportingPeriodCloseSql.INSERT_PERIOD_RESULT_TRANSFER_TOTAL)) {
        statement.bindInt(1, sweepOrder);
        statement.bindText(2, closedTotal.debitTotal().currencyUnit().code());
        statement.bindLong(3, closedTotal.debitTotal().minorUnits());
        statement.bindLong(4, closedTotal.creditTotal().minorUnits());
        statement.step();
      }
    }
    for (CommittedPosting closingPosting : closingPostings) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(
              SqliteReportingPeriodCloseSql.INSERT_PERIOD_RESULT_TRANSFER_POSTING)) {
        statement.bindInt(1, sweepOrder);
        statement.bindText(2, closingPosting.postingId().value());
        statement.step();
      }
    }
    return new SweptInterimResult(
        sweepOrder,
        reportingPeriod,
        resultHoldingAccountCode,
        sweptTotals,
        sweptAt,
        closingPostings.stream().map(CommittedPosting::postingId).toList());
  }

  static ClosedFiscalYearRecord insertFiscalYearClose(
      SqliteNativeDatabase activeDatabase,
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      AccountCode capitalAccountCode,
      AccountCode resultHoldingAccountCode,
      AccountCode retainedAccumulatedAccountCode,
      Instant closedAt,
      List<CommittedPosting> closePostings) {
    int closeOrder;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteReportingPeriodCloseSql.INSERT_FISCAL_YEAR_CLOSE)) {
      statement.bindText(
          1, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateFrom()));
      statement.bindText(
          2, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateTo()));
      statement.bindText(3, capitalAccountCode.value());
      statement.bindText(4, resultHoldingAccountCode.value());
      statement.bindText(5, retainedAccumulatedAccountCode.value());
      statement.bindText(6, CanonicalTemporalText.formatUtcInstant(closedAt));
      if (statement.step() != SqliteNativeResultCode.code("ROW")) {
        throw new IllegalStateException("SQLite fiscal-year close insert returned no close order.");
      }
      closeOrder = statement.columnInt(0);
      if (statement.step() != SqliteNativeResultCode.code("DONE")) {
        throw new IllegalStateException(
            "SQLite fiscal-year close insert returned more than one close order.");
      }
    }
    for (CommittedPosting closePosting : closePostings) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqliteReportingPeriodCloseSql.INSERT_FISCAL_YEAR_CLOSE_POSTING)) {
        statement.bindInt(1, closeOrder);
        statement.bindText(2, closePosting.postingId().value());
        statement.step();
      }
    }
    return new ClosedFiscalYearRecord(
        closeOrder,
        reportingPeriod,
        capitalAccountCode,
        resultHoldingAccountCode,
        retainedAccumulatedAccountCode,
        closedAt,
        closePostings.stream().map(CommittedPosting::postingId).toList());
  }

  private static void preparePendingJournalLineTable(SqliteNativeDatabase activeDatabase) {
    activeDatabase.executeStatement(SqlitePostingSql.CREATE_PENDING_JOURNAL_LINE);
  }

  private static void clearPendingJournalLineTable(SqliteNativeDatabase activeDatabase) {
    activeDatabase.executeStatement(SqlitePostingSql.CLEAR_PENDING_JOURNAL_LINE);
  }

  private static void requireBalancedPendingJournalLineTable(SqliteNativeDatabase activeDatabase) {
    boolean validPendingLines =
        SqliteStatementQueries.existsRow(
            activeDatabase, SqlitePostingSql.VALID_PENDING_JOURNAL_LINE, statement -> {});
    if (!validPendingLines) {
      throw new IllegalStateException(
          "SQLite journal-line staging rejected one unbalanced or malformed posting.");
    }
  }

  private static void persistPendingJournalLineTable(
      SqliteNativeDatabase activeDatabase, String postingId) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.PERSIST_PENDING_JOURNAL_LINE)) {
      statement.bindText(1, postingId);
      statement.step();
    }
  }

  private static void bindOptionalText(
      SqliteNativeStatement statement, int parameterIndex, @Nullable String value) {
    statement.bindText(parameterIndex, value);
  }
}
