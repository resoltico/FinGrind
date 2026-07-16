package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import org.jspecify.annotations.Nullable;

/** Dedicated SQLite persistence for one committed posting fact and its retained attachments. */
final class SqlitePostingFactWriter {
  private SqlitePostingFactWriter() {}

  static void insertPostingFact(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      RequestFingerprint requestFingerprint) {
    RequestProvenance requestProvenance = postingFact.provenance().requestProvenance();
    BookkeepingEntry retainedOriginatingEntry =
        postingFact
            .callerAuthoredEntry()
            .orElse(postingFact.resolvedOriginatingEntry().orElse(null));
    BookkeepingEntry resolvedOrRetainedOriginatingEntry =
        postingFact
            .resolvedOriginatingEntry()
            .orElse(postingFact.callerAuthoredEntry().orElse(null));
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_FACT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, postingFact.postingKind().wireValue());
      statement.bindText(3, postingFact.postingOriginKind().wireValue());
      bindOriginatingEntry(statement, retainedOriginatingEntry, resolvedOrRetainedOriginatingEntry);
      statement.bindText(
          13, CanonicalTemporalText.formatLocalDate(postingFact.journalEntry().effectiveDate()));
      statement.bindText(
          14, CanonicalTemporalText.formatUtcInstant(postingFact.provenance().recordedAt()));
      statement.bindText(15, requestProvenance.actorId().value());
      statement.bindText(16, requestProvenance.actorType().wireValue());
      statement.bindText(17, requestProvenance.commandId().value());
      statement.bindText(18, requestProvenance.idempotencyKey().value());
      statement.bindText(19, requestProvenance.causationId().value());
      bindOptionalText(
          statement,
          20,
          requestProvenance.correlationId().map(value -> value.value()).orElse(null));
      bindOptionalText(
          statement,
          21,
          postingFact.postingLineage().reversalReason().map(value -> value.value()).orElse(null));
      statement.bindText(22, postingFact.provenance().sourceChannel().wireValue());
      bindOptionalText(
          statement,
          23,
          postingFact
              .postingLineage()
              .reversalReference()
              .map(reference -> reference.priorPostingId().value())
              .orElse(null));
      statement.bindInt(24, requestFingerprint.version());
      statement.bindText(25, requestFingerprint.sha256Hex());
      statement.step();
    }
    insertPostingSourceDocuments(activeDatabase, postingFact);
    insertPostingApprovals(activeDatabase, postingFact);
    insertPostingAppliedTax(
        activeDatabase,
        postingFact.postingId().value(),
        appliedTax(resolvedOrRetainedOriginatingEntry));
    insertPostingForeignExchange(
        activeDatabase,
        postingFact.postingId().value(),
        foreignExchangeDetails(resolvedOrRetainedOriginatingEntry));
  }

  private static void insertPostingSourceDocuments(
      SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
    for (int index = 0; index < postingFact.evidence().sourceDocuments().size(); index++) {
      var sourceDocument = postingFact.evidence().sourceDocuments().get(index);
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_SOURCE_DOCUMENT)) {
        statement.bindText(1, postingFact.postingId().value());
        statement.bindInt(2, index);
        statement.bindText(3, sourceDocument.sourceDocumentId().value());
        statement.bindText(4, sourceDocument.sourceDocumentType().value());
        statement.bindText(5, CanonicalTemporalText.formatLocalDate(sourceDocument.documentDate()));
        statement.step();
      }
    }
  }

  private static void insertPostingApprovals(
      SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
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

  private static void insertPostingAppliedTax(
      SqliteNativeDatabase activeDatabase, String postingId, @Nullable AppliedTax appliedTax) {
    if (appliedTax == null) {
      return;
    }
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteTaxSql.INSERT_POSTING_APPLIED_TAX)) {
      statement.bindText(1, postingId);
      statement.bindText(2, appliedTax.taxRegistrationId().value());
      statement.bindText(3, appliedTax.taxCode().value());
      statement.bindText(4, appliedTax.taxCodeName().value());
      statement.bindInt(5, appliedTax.rate().partsPerMillionOfWhole());
      statement.bindText(6, appliedTax.inclusionMode().wireValue());
      statement.bindText(7, appliedTax.applicationKind().wireValue());
      statement.bindText(8, appliedTax.taxableAmount().currencyCode());
      statement.bindLong(9, Long.parseLong(appliedTax.taxableAmount().minorUnits()));
      statement.bindLong(10, Long.parseLong(appliedTax.taxAmount().minorUnits()));
      statement.bindLong(11, Long.parseLong(appliedTax.grossAmount().minorUnits()));
      bindOptionalText(
          statement,
          12,
          appliedTax.taxAccountCode() == null ? null : appliedTax.taxAccountCode().value());
      statement.step();
    }
  }

  private static void insertPostingForeignExchange(
      SqliteNativeDatabase activeDatabase,
      String postingId,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    if (foreignExchangeDetails == null) {
      return;
    }
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_FOREIGN_EXCHANGE)) {
      statement.bindText(1, postingId);
      statement.bindText(2, foreignExchangeDetails.treatmentKind().wireValue());
      statement.bindText(3, foreignExchangeDetails.transactionAmount().currencyCode());
      statement.bindLong(
          4, Long.parseLong(foreignExchangeDetails.transactionAmount().minorUnits()));
      statement.bindText(5, foreignExchangeDetails.functionalAmount().currencyCode());
      statement.bindLong(6, Long.parseLong(foreignExchangeDetails.functionalAmount().minorUnits()));
      statement.bindLong(
          7,
          Long.parseLong(
              foreignExchangeDetails
                  .quotedExchangeRate()
                  .transactionCurrencyAmount()
                  .minorUnits()));
      statement.bindLong(
          8,
          Long.parseLong(
              foreignExchangeDetails.quotedExchangeRate().functionalCurrencyAmount().minorUnits()));
      statement.bindText(
          9,
          CanonicalTemporalText.formatLocalDate(
              foreignExchangeDetails.quotedExchangeRate().quotedOn()));
      statement.bindText(10, foreignExchangeDetails.quotedExchangeRate().quoteSource());
      statement.step();
    }
  }

  private static void bindOriginatingEntry(
      SqliteNativeStatement statement,
      @Nullable BookkeepingEntry retainedOriginatingEntry,
      @Nullable BookkeepingEntry resolvedOriginatingEntry) {
    SqliteOriginatingEntryFactMapper.bindOriginatingEntry(
        statement, retainedOriginatingEntry, resolvedOriginatingEntry);
  }

  private static void bindOptionalText(
      SqliteNativeStatement statement, int parameterIndex, @Nullable String value) {
    statement.bindText(parameterIndex, value);
  }

  private static @Nullable AppliedTax appliedTax(@Nullable BookkeepingEntry originatingEntry) {
    return switch (originatingEntry) {
      case BookkeepingEntry.SaleSettled sale -> sale.appliedTax();
      case BookkeepingEntry.SaleOnCredit sale -> sale.appliedTax();
      case BookkeepingEntry.PurchaseSettled purchase -> purchase.appliedTax();
      case BookkeepingEntry.PurchaseOnCredit purchase -> purchase.appliedTax();
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          capitalization.appliedTax();
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          capitalization.appliedTax();
      case BookkeepingEntry.ExpenseSettled expense -> expense.appliedTax();
      case BookkeepingEntry.ExpenseOnCredit expense -> expense.appliedTax();
      case null, default -> null;
    };
  }

  private static @Nullable ForeignExchangeDetails foreignExchangeDetails(
      @Nullable BookkeepingEntry originatingEntry) {
    return originatingEntry == null ? null : originatingEntry.foreignExchangeDetails();
  }
}
