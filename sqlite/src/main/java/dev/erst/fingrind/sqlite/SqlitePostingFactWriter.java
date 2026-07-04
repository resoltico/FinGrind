package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
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
    bindOriginatingEntryFactValues(statement, originatingEntryFactValues(originatingEntry));
  }

  private static OriginatingEntryFactValues originatingEntryFactValues(
      @Nullable BookkeepingEntry originatingEntry) {
    if (originatingEntry == null) {
      return OriginatingEntryFactValues.empty();
    }
    return switch (originatingEntry) {
      case BookkeepingEntry.SaleSettled sale ->
          simpleOriginatingEntryFactValues(
              sale.cashAccountCode().value(), sale.revenueAccountCode().value(), sale.amount());
      case BookkeepingEntry.SaleOnCredit sale ->
          simpleOriginatingEntryFactValues(
              sale.receivableAccountCode().value(),
              sale.revenueAccountCode().value(),
              sale.amount());
      case BookkeepingEntry.PurchaseSettled purchase ->
          simpleOriginatingEntryFactValues(
              purchase.inventoryAccountCode().value(),
              purchase.cashAccountCode().value(),
              purchase.amount());
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          simpleOriginatingEntryFactValues(
              purchase.inventoryAccountCode().value(),
              purchase.payableAccountCode().value(),
              purchase.amount());
      case BookkeepingEntry.ExpenseSettled expense ->
          simpleOriginatingEntryFactValues(
              expense.expenseAccountCode().value(),
              expense.cashAccountCode().value(),
              expense.amount());
      case BookkeepingEntry.ExpenseOnCredit expense ->
          simpleOriginatingEntryFactValues(
              expense.expenseAccountCode().value(),
              expense.payableAccountCode().value(),
              expense.amount());
      case BookkeepingEntry.Receipt receipt ->
          settlementOriginatingEntryFactValues(
              receipt.cashAccountCode().value(),
              receipt.receivableAccountCode().value(),
              receipt.amount(),
              receipt.settlementAdjunct());
      case BookkeepingEntry.Payment payment ->
          settlementOriginatingEntryFactValues(
              payment.payableAccountCode().value(),
              payment.cashAccountCode().value(),
              payment.amount(),
              payment.settlementAdjunct());
      case BookkeepingEntry.OwnerContribution contribution ->
          simpleOriginatingEntryFactValues(
              contribution.cashAccountCode().value(),
              contribution.equityAccountCode().value(),
              contribution.amount());
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          simpleOriginatingEntryFactValues(
              withdrawal.equityAccountCode().value(),
              withdrawal.cashAccountCode().value(),
              withdrawal.amount());
      default -> OriginatingEntryFactValues.empty();
    };
  }

  private static void bindOriginatingEntryFactValues(
      SqliteNativeStatement statement, OriginatingEntryFactValues factValues) {
    bindOptionalText(statement, 4, factValues.primaryDebitAccountCode());
    bindOptionalText(statement, 5, factValues.primaryCreditAccountCode());
    bindOptionalText(statement, 6, factValues.adjunctAccountCode());
    bindOptionalText(statement, 7, factValues.amountCurrencyCode());
    bindOptionalLong(statement, 8, factValues.amountMinorUnits());
    bindOptionalLong(statement, 9, factValues.adjunctAmountMinorUnits());
  }

  private static OriginatingEntryFactValues simpleOriginatingEntryFactValues(
      String primaryDebitAccountCode, String primaryCreditAccountCode, MonetaryAmount amount) {
    return new OriginatingEntryFactValues(
        primaryDebitAccountCode,
        primaryCreditAccountCode,
        null,
        amount.currencyCode(),
        Long.parseLong(amount.minorUnits()),
        null);
  }

  private static OriginatingEntryFactValues settlementOriginatingEntryFactValues(
      String primaryDebitAccountCode,
      String primaryCreditAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct) {
    return new OriginatingEntryFactValues(
        primaryDebitAccountCode,
        primaryCreditAccountCode,
        settlementAdjunct == null ? null : settlementAdjunct.accountCode().value(),
        amount.currencyCode(),
        Long.parseLong(amount.minorUnits()),
        settlementAdjunct == null ? null : Long.parseLong(settlementAdjunct.amount().minorUnits()));
  }

  private static void bindOptionalText(
      SqliteNativeStatement statement, int parameterIndex, @Nullable String value) {
    statement.bindText(parameterIndex, value);
  }

  private static void bindOptionalLong(
      SqliteNativeStatement statement, int parameterIndex, @Nullable Long value) {
    if (value == null) {
      statement.bindNull(parameterIndex);
      return;
    }
    statement.bindLong(parameterIndex, value);
  }

  private static @Nullable AppliedTax appliedTax(@Nullable BookkeepingEntry originatingEntry) {
    return switch (originatingEntry) {
      case BookkeepingEntry.SaleSettled sale -> sale.appliedTax();
      case BookkeepingEntry.SaleOnCredit sale -> sale.appliedTax();
      case BookkeepingEntry.ExpenseSettled expense -> expense.appliedTax();
      case BookkeepingEntry.ExpenseOnCredit expense -> expense.appliedTax();
      case null, default -> null;
    };
  }

  private static @Nullable ForeignExchangeDetails foreignExchangeDetails(
      @Nullable BookkeepingEntry originatingEntry) {
    return originatingEntry == null ? null : originatingEntry.foreignExchangeDetails();
  }

  private record OriginatingEntryFactValues(
      @Nullable String primaryDebitAccountCode,
      @Nullable String primaryCreditAccountCode,
      @Nullable String adjunctAccountCode,
      @Nullable String amountCurrencyCode,
      @Nullable Long amountMinorUnits,
      @Nullable Long adjunctAmountMinorUnits) {
    private static OriginatingEntryFactValues empty() {
      return new OriginatingEntryFactValues(null, null, null, null, null, null);
    }
  }
}
