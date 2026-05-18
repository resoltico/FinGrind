package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.executor.bookkeeping.ClosedPeriod;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared SQLite write helpers for persisted book metadata, accounts, and postings. */
final class SqliteMutationWriter {
  private SqliteMutationWriter() {}

  static void insertInitializedAt(SqliteNativeDatabase activeDatabase, Instant initializedAt) {
    insertBookMetaValue(
        activeDatabase, SqlitePostingSql.INITIALIZED_AT_META_KEY, initializedAt.toString());
  }

  static void insertBookIdentity(SqliteNativeDatabase activeDatabase, BookIdentity bookIdentity) {
    String encodedBusinessActivityTags =
        bookIdentity.entityProfile().businessActivityTags().stream()
            .map(BusinessActivityTag::value)
            .map(SqliteMutationWriter::encodeBookMetaValue)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_BOOK_IDENTITY)) {
      statement.bindText(1, bookIdentity.entityName().value());
      statement.bindText(2, bookIdentity.functionalCurrency().code());
      statement.bindText(3, bookIdentity.fiscalYearStart().wireValue());
      statement.step();
    }
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_ENTITY_PROFILE)) {
      statement.bindText(1, bookIdentity.entityProfile().entityForm().wireValue());
      statement.bindText(2, bookIdentity.entityProfile().ownerModel().wireValue());
      statement.bindText(3, bookIdentity.entityProfile().reportingObligationStatus().wireValue());
      statement.bindText(4, bookIdentity.entityProfile().taxRegistrationStatus().wireValue());
      statement.bindText(5, encodedBusinessActivityTags);
      statement.step();
    }
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_BOOK_POLICY)) {
      statement.bindText(1, bookIdentity.accountingBasis().wireValue());
      statement.step();
    }
  }

  static void insertBookMetaValue(SqliteNativeDatabase activeDatabase, String key, String value) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_BOOK_INITIALIZED_AT)) {
      statement.bindText(1, key);
      statement.bindText(2, value);
      statement.step();
    }
  }

  static void upsertAccount(SqliteNativeDatabase activeDatabase, RegisteredAccount account) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.UPSERT_ACCOUNT)) {
      statement.bindText(1, account.accountCode().value());
      statement.bindText(2, account.accountName().value());
      statement.bindText(3, account.accountType().wireValue());
      statement.bindText(4, account.accountRole().wireValue());
      bindOptionalText(
          statement,
          5,
          account.accountTaxonomy().parentAccountCode().map(AccountCode::value).orElse(null));
      bindOptionalText(
          statement,
          6,
          account
              .accountTaxonomy()
              .financialPositionLineClassification()
              .map(value -> value.wireValue())
              .orElse(null));
      bindOptionalText(
          statement,
          7,
          account
              .accountTaxonomy()
              .profitAndLossLineClassification()
              .map(value -> value.wireValue())
              .orElse(null));
      statement.bindInt(8, Boolean.compare(account.active(), false));
      statement.bindText(9, account.declaredAt().toString());
      statement.step();
    }
  }

  static void insertPostingFact(SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
    RequestProvenance requestProvenance = postingFact.provenance().requestProvenance();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_FACT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, postingFact.postingKind().wireValue());
      statement.bindText(3, postingFact.journalEntry().effectiveDate().toString());
      statement.bindText(4, postingFact.provenance().recordedAt().toString());
      statement.bindText(5, requestProvenance.actorId().value());
      statement.bindText(6, requestProvenance.actorType().wireValue());
      statement.bindText(7, requestProvenance.commandId().value());
      statement.bindText(8, requestProvenance.idempotencyKey().value());
      statement.bindText(9, requestProvenance.causationId().value());
      bindOptionalText(
          statement,
          10,
          requestProvenance.correlationId().map(value -> value.value()).orElse(null));
      bindOptionalText(
          statement,
          11,
          postingFact.postingLineage().reversalReason().map(value -> value.value()).orElse(null));
      statement.bindText(12, postingFact.provenance().sourceChannel().wireValue());
      bindOptionalText(
          statement,
          13,
          postingFact
              .postingLineage()
              .reversalReference()
              .map(reference -> reference.priorPostingId().value())
              .orElse(null));
      statement.step();
    }
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

  static ClosedPeriod insertPeriodClose(
      SqliteNativeDatabase activeDatabase,
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      AccountCode closingEquityAccountCode,
      List<CurrencyBalance> closedTotals,
      Instant closedAt,
      List<CommittedPosting> closingPostings) {
    int closeOrder;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_PERIOD_CLOSE)) {
      statement.bindText(1, reportingPeriod.effectiveDateFrom().toString());
      statement.bindText(2, reportingPeriod.effectiveDateTo().toString());
      statement.bindText(3, closingEquityAccountCode.value());
      statement.bindText(4, closedAt.toString());
      if (statement.step() != SqliteNativeResultCodes.ROW) {
        throw new IllegalStateException("SQLite period close insert returned no close order.");
      }
      closeOrder = statement.columnInt(0);
      if (statement.step() != SqliteNativeResultCodes.DONE) {
        throw new IllegalStateException(
            "SQLite period close insert returned more than one close order.");
      }
    }
    for (CurrencyBalance closedTotal : closedTotals) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_PERIOD_CLOSE_TOTAL)) {
        statement.bindInt(1, closeOrder);
        statement.bindText(2, closedTotal.debitTotal().currencyUnit().code());
        statement.bindLong(3, closedTotal.debitTotal().minorUnits());
        statement.bindLong(4, closedTotal.creditTotal().minorUnits());
        statement.step();
      }
    }
    for (CommittedPosting closingPosting : closingPostings) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_PERIOD_CLOSE_POSTING)) {
        statement.bindInt(1, closeOrder);
        statement.bindText(2, closingPosting.postingId().value());
        statement.step();
      }
    }
    return new ClosedPeriod(
        closeOrder,
        reportingPeriod,
        closingEquityAccountCode,
        closedTotals,
        closedAt,
        closingPostings.stream().map(CommittedPosting::postingId).toList());
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

  private static String encodeBookMetaValue(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
