package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
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
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_FACT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, postingFact.postingKind().wireValue());
      statement.bindText(3, postingFact.postingOriginKind().wireValue());
      bindOriginatingEntry(statement, postingFact.originatingEntry());
      statement.bindText(
          10, CanonicalTemporalText.formatLocalDate(postingFact.journalEntry().effectiveDate()));
      statement.bindText(
          11, CanonicalTemporalText.formatUtcInstant(postingFact.provenance().recordedAt()));
      statement.bindText(12, requestProvenance.actorId().value());
      statement.bindText(13, requestProvenance.actorType().wireValue());
      statement.bindText(14, requestProvenance.commandId().value());
      statement.bindText(15, requestProvenance.idempotencyKey().value());
      statement.bindText(16, requestProvenance.causationId().value());
      bindOptionalText(
          statement,
          17,
          requestProvenance.correlationId().map(value -> value.value()).orElse(null));
      bindOptionalText(
          statement,
          18,
          postingFact.postingLineage().reversalReason().map(value -> value.value()).orElse(null));
      statement.bindText(19, postingFact.provenance().sourceChannel().wireValue());
      bindOptionalText(
          statement,
          20,
          postingFact
              .postingLineage()
              .reversalReference()
              .map(reference -> reference.priorPostingId().value())
              .orElse(null));
      statement.bindInt(21, requestFingerprint.version());
      statement.bindText(22, requestFingerprint.sha256Hex());
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
    AppliedTax appliedTax = appliedTax(postingFact.originatingEntry());
    if (appliedTax != null) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqliteTaxSql.INSERT_POSTING_APPLIED_TAX)) {
        statement.bindText(1, postingFact.postingId().value());
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
    ForeignExchangeDetails foreignExchangeDetails =
        foreignExchangeDetails(postingFact.originatingEntry());
    if (foreignExchangeDetails == null) {
      return;
    }
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_FOREIGN_EXCHANGE)) {
      statement.bindText(1, postingFact.postingId().value());
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
      SqliteNativeStatement statement, @Nullable BookkeepingEntry originatingEntry) {
    if (originatingEntry == null) {
      bindOptionalText(statement, 4, null);
      bindOptionalText(statement, 5, null);
      bindOptionalText(statement, 6, null);
      bindOptionalText(statement, 7, null);
      bindOptionalText(statement, 8, null);
      bindOptionalText(statement, 9, null);
      return;
    }
    switch (originatingEntry) {
      case BookkeepingEntry.Sale sale -> {
        statement.bindText(4, sale.cashAccountCode().value());
        statement.bindText(5, sale.revenueAccountCode().value());
        bindOptionalText(statement, 6, null);
        bindOptionalText(statement, 7, null);
        statement.bindText(8, sale.amount().currencyCode());
        statement.bindLong(9, Long.parseLong(sale.amount().minorUnits()));
      }
      case BookkeepingEntry.Expense expense -> {
        statement.bindText(4, expense.cashAccountCode().value());
        bindOptionalText(statement, 5, null);
        statement.bindText(6, expense.expenseAccountCode().value());
        bindOptionalText(statement, 7, null);
        statement.bindText(8, expense.amount().currencyCode());
        statement.bindLong(9, Long.parseLong(expense.amount().minorUnits()));
      }
      case BookkeepingEntry.OwnerContribution contribution -> {
        statement.bindText(4, contribution.cashAccountCode().value());
        bindOptionalText(statement, 5, null);
        bindOptionalText(statement, 6, null);
        statement.bindText(7, contribution.equityAccountCode().value());
        statement.bindText(8, contribution.amount().currencyCode());
        statement.bindLong(9, Long.parseLong(contribution.amount().minorUnits()));
      }
      case BookkeepingEntry.OwnerWithdrawal withdrawal -> {
        statement.bindText(4, withdrawal.cashAccountCode().value());
        bindOptionalText(statement, 5, null);
        bindOptionalText(statement, 6, null);
        statement.bindText(7, withdrawal.equityAccountCode().value());
        statement.bindText(8, withdrawal.amount().currencyCode());
        statement.bindLong(9, Long.parseLong(withdrawal.amount().minorUnits()));
      }
      default -> {
        bindOptionalText(statement, 4, null);
        bindOptionalText(statement, 5, null);
        bindOptionalText(statement, 6, null);
        bindOptionalText(statement, 7, null);
        bindOptionalText(statement, 8, null);
        bindOptionalText(statement, 9, null);
      }
    }
  }

  private static void bindOptionalText(
      SqliteNativeStatement statement, int parameterIndex, @Nullable String value) {
    statement.bindText(parameterIndex, value);
  }

  private static @Nullable AppliedTax appliedTax(@Nullable BookkeepingEntry originatingEntry) {
    return switch (originatingEntry) {
      case BookkeepingEntry.Sale sale -> sale.appliedTax();
      case BookkeepingEntry.Expense expense -> expense.appliedTax();
      case null, default -> null;
    };
  }

  private static @Nullable ForeignExchangeDetails foreignExchangeDetails(
      @Nullable BookkeepingEntry originatingEntry) {
    return originatingEntry == null ? null : originatingEntry.foreignExchangeDetails();
  }
}
