package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult;
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
      statement.bindText(7, bookIdentity.functionalCurrency().code());
      statement.bindInt(8, bookIdentity.fiscalYearStart().month());
      statement.bindInt(9, bookIdentity.fiscalYearStart().day());
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

  static void upsertAccount(SqliteNativeDatabase activeDatabase, RegisteredAccount account) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.UPSERT_ACCOUNT)) {
      statement.bindText(1, account.accountCode().value());
      statement.bindText(2, account.accountName().value());
      statement.bindText(3, account.accountType().wireValue());
      statement.bindText(4, account.accountRole().wireValue());
      statement.bindText(5, account.accountTaxonomy().nodeKind().wireValue());
      bindOptionalText(
          statement,
          6,
          account.accountTaxonomy().parentAccountCode().map(AccountCode::value).orElse(null));
      bindOptionalText(
          statement,
          7,
          account
              .accountTaxonomy()
              .financialPositionLineClassification()
              .map(value -> value.wireValue())
              .orElse(null));
      bindOptionalText(
          statement,
          8,
          account
              .accountTaxonomy()
              .profitAndLossLineClassification()
              .map(value -> value.wireValue())
              .orElse(null));
      statement.bindInt(9, Boolean.compare(account.active(), false));
      statement.bindText(10, CanonicalTemporalText.formatUtcInstant(account.declaredAt()));
      statement.step();
    }
  }

  static void insertPostingFact(SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
    RequestProvenance requestProvenance = postingFact.provenance().requestProvenance();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_FACT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, postingFact.postingKind().wireValue());
      statement.bindText(3, postingFact.postingOriginKind().wireValue());
      statement.bindText(
          4, CanonicalTemporalText.formatLocalDate(postingFact.journalEntry().effectiveDate()));
      statement.bindText(
          5, CanonicalTemporalText.formatUtcInstant(postingFact.provenance().recordedAt()));
      statement.bindText(6, requestProvenance.actorId().value());
      statement.bindText(7, requestProvenance.actorType().wireValue());
      statement.bindText(8, requestProvenance.commandId().value());
      statement.bindText(9, requestProvenance.idempotencyKey().value());
      statement.bindText(10, requestProvenance.causationId().value());
      bindOptionalText(
          statement,
          11,
          requestProvenance.correlationId().map(value -> value.value()).orElse(null));
      bindOptionalText(
          statement,
          12,
          postingFact.postingLineage().reversalReason().map(value -> value.value()).orElse(null));
      statement.bindText(13, postingFact.provenance().sourceChannel().wireValue());
      bindOptionalText(
          statement,
          14,
          postingFact
              .postingLineage()
              .reversalReference()
              .map(reference -> reference.priorPostingId().value())
              .orElse(null));
      statement.step();
    }
    for (int index = 0; index < postingFact.evidence().sourceDocuments().size(); index++) {
      var sourceDocument = postingFact.evidence().sourceDocuments().get(index);
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_SOURCE_DOCUMENT)) {
        statement.bindText(1, postingFact.postingId().value());
        statement.bindInt(2, index);
        statement.bindText(3, sourceDocument.sourceDocumentId().value());
        statement.bindText(4, sourceDocument.sourceDocumentType().value());
        statement.bindText(5, CanonicalTemporalText.formatLocalDate(sourceDocument.documentDate()));
        statement.bindText(6, CanonicalTemporalText.formatUtcInstant(sourceDocument.capturedAt()));
        statement.bindText(7, sourceDocument.storageLocator().value());
        statement.bindText(8, sourceDocument.contentSha256().value());
        statement.step();
      }
    }
    for (int index = 0; index < postingFact.evidence().approvals().size(); index++) {
      var approval = postingFact.evidence().approvals().get(index);
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_APPROVAL)) {
        statement.bindText(1, postingFact.postingId().value());
        statement.bindInt(2, index);
        statement.bindText(3, approval.approvalId().value());
        statement.bindText(4, approval.approvalType().value());
        statement.bindText(5, approval.approverId().value());
        statement.bindText(6, approval.approverType().wireValue());
        statement.bindText(7, approval.decision().wireValue());
        statement.bindText(8, CanonicalTemporalText.formatUtcInstant(approval.approvedAt()));
        statement.step();
      }
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

  static TransferredPeriodResult insertPeriodResultTransfer(
      SqliteNativeDatabase activeDatabase,
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      AccountCode resultHoldingAccountCode,
      List<CurrencyBalance> transferredTotals,
      Instant transferredAt,
      List<CommittedPosting> closingPostings) {
    int transferOrder;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_PERIOD_RESULT_TRANSFER)) {
      statement.bindText(
          1, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateFrom()));
      statement.bindText(
          2, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateTo()));
      statement.bindText(3, resultHoldingAccountCode.value());
      statement.bindText(4, CanonicalTemporalText.formatUtcInstant(transferredAt));
      if (statement.step() != SqliteNativeResultCode.code("ROW")) {
        throw new IllegalStateException(
            "SQLite period result transfer insert returned no transfer order.");
      }
      transferOrder = statement.columnInt(0);
      if (statement.step() != SqliteNativeResultCode.code("DONE")) {
        throw new IllegalStateException(
            "SQLite period result transfer insert returned more than one transfer order.");
      }
    }
    for (CurrencyBalance closedTotal : transferredTotals) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_PERIOD_RESULT_TRANSFER_TOTAL)) {
        statement.bindInt(1, transferOrder);
        statement.bindText(2, closedTotal.debitTotal().currencyUnit().code());
        statement.bindLong(3, closedTotal.debitTotal().minorUnits());
        statement.bindLong(4, closedTotal.creditTotal().minorUnits());
        statement.step();
      }
    }
    for (CommittedPosting closingPosting : closingPostings) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_PERIOD_RESULT_TRANSFER_POSTING)) {
        statement.bindInt(1, transferOrder);
        statement.bindText(2, closingPosting.postingId().value());
        statement.step();
      }
    }
    return new TransferredPeriodResult(
        transferOrder,
        reportingPeriod,
        resultHoldingAccountCode,
        transferredTotals,
        transferredAt,
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
}
