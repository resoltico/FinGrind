package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Maps SQLite native statement rows into FinGrind posting domain objects. */
final class SqlitePostingMapper {
  private SqlitePostingMapper() {}

  static RegisteredAccount registeredAccount(SqliteNativeStatement accountRow) {
    return new RegisteredAccount(
        new AccountCode(requiredText(accountRow, SqlitePostingColumnIndexes.COL_ACCOUNT_CODE)),
        new AccountName(requiredText(accountRow, SqlitePostingColumnIndexes.COL_ACCOUNT_NAME)),
        AccountType.fromWireValue(
            requiredText(accountRow, SqlitePostingColumnIndexes.COL_ACCOUNT_TYPE)),
        new AccountTaxonomy(
            AccountNodeKind.fromWireValue(
                requiredText(accountRow, SqlitePostingColumnIndexes.COL_ACCOUNT_NODE_KIND)),
            optionalText(accountRow, SqlitePostingColumnIndexes.COL_ACCOUNT_PARENT_ACCOUNT_CODE)
                .map(AccountCode::new),
            optionalText(
                    accountRow,
                    SqlitePostingColumnIndexes.COL_ACCOUNT_FINANCIAL_POSITION_LINE_CLASSIFICATION)
                .map(FinancialPositionLineClassification::fromWireValue),
            optionalText(
                    accountRow,
                    SqlitePostingColumnIndexes.COL_ACCOUNT_PROFIT_AND_LOSS_LINE_CLASSIFICATION)
                .map(ProfitAndLossLineClassification::fromWireValue),
            optionalText(
                    accountRow,
                    SqlitePostingColumnIndexes.COL_ACCOUNT_CASH_FLOW_ASSET_CLASSIFICATION)
                .map(CashFlowAssetClassification::fromWireValue)),
        requiredInt(accountRow, SqlitePostingColumnIndexes.COL_ACCOUNT_ACTIVE) == 1,
        CanonicalTemporalText.parseUtcInstant(
            requiredText(accountRow, SqlitePostingColumnIndexes.COL_ACCOUNT_DECLARED_AT),
            "account.declaredAt"));
  }

  static CommittedPosting committedPosting(
      SqliteNativeStatement postingRow,
      List<JournalLine> lines,
      AccountingEvidence evidence,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    PostingId postingId =
        new PostingId(requiredText(postingRow, SqlitePostingColumnIndexes.COL_POSTING_ID));
    JournalEntry journalEntry =
        new JournalEntry(
            CanonicalTemporalText.parseLocalDate(
                requiredText(postingRow, SqlitePostingColumnIndexes.COL_EFFECTIVE_DATE),
                "posting.effectiveDate"),
            lines);
    PostingLineageModel postingLineage = readPostingLineageModel(postingRow);
    PostingOriginKind postingOriginKind =
        PostingOriginKind.fromWireValue(
            requiredText(postingRow, SqlitePostingColumnIndexes.COL_POSTING_ORIGIN_KIND));
    dev.erst.fingrind.core.RequestProvenance requestProvenance =
        new dev.erst.fingrind.core.RequestProvenance(
            new ActorId(requiredText(postingRow, SqlitePostingColumnIndexes.COL_ACTOR_ID)),
            ActorType.fromWireValue(
                requiredText(postingRow, SqlitePostingColumnIndexes.COL_ACTOR_TYPE)),
            new CommandId(requiredText(postingRow, SqlitePostingColumnIndexes.COL_COMMAND_ID)),
            new IdempotencyKey(
                requiredText(postingRow, SqlitePostingColumnIndexes.COL_IDEMPOTENCY_KEY)),
            new CausationId(requiredText(postingRow, SqlitePostingColumnIndexes.COL_CAUSATION_ID)),
            optionalText(postingRow, SqlitePostingColumnIndexes.COL_CORRELATION_ID)
                .map(CorrelationId::new));
    CommittedProvenance provenance =
        new CommittedProvenance(
            requestProvenance,
            CanonicalTemporalText.parseUtcInstant(
                requiredText(postingRow, SqlitePostingColumnIndexes.COL_RECORDED_AT),
                "posting.recordedAt"),
            SourceChannel.fromWireValue(
                requiredText(postingRow, SqlitePostingColumnIndexes.COL_SOURCE_CHANNEL)));
    return new CommittedPosting(
        postingId,
        journalEntry,
        postingLineage,
        dev.erst.fingrind.core.PostingKind.fromWireValue(
            requiredText(postingRow, SqlitePostingColumnIndexes.COL_POSTING_KIND)),
        postingOriginKind,
        evidence,
        provenance,
        SqlitePostingOriginatingEntryMapper.originatingEntry(
            postingRow,
            journalEntry,
            postingLineage,
            postingOriginKind,
            appliedTax,
            foreignExchangeDetails));
  }

  static List<JournalLine> journalLines(SqliteNativeStatement lineRows) {
    List<JournalLine> lines = new ArrayList<>();
    while (lineRows.step() == SqliteNativeResultCode.code("ROW")) {
      lines.add(
          new JournalLine(
              new AccountCode(
                  requiredText(lineRows, SqlitePostingColumnIndexes.COL_LINE_ACCOUNT_CODE)),
              JournalLine.EntrySide.fromWireValue(
                  requiredText(lineRows, SqlitePostingColumnIndexes.COL_LINE_ENTRY_SIDE)),
              SqlitePersistedMoneyCodec.readMoney(
                  lineRows,
                  SqlitePostingColumnIndexes.COL_LINE_CURRENCY_CODE,
                  SqlitePostingColumnIndexes.COL_LINE_AMOUNT_MINOR)));
    }
    return lines;
  }

  static PostingLineageModel readPostingLineageModel(SqliteNativeStatement postingRow) {
    Optional<String> priorPostingId =
        optionalText(postingRow, SqlitePostingColumnIndexes.COL_PRIOR_POSTING_ID);
    Optional<String> reason = optionalText(postingRow, SqlitePostingColumnIndexes.COL_REASON);
    if (priorPostingId.isEmpty() && reason.isEmpty()) {
      return PostingLineageModel.direct();
    }
    if (priorPostingId.isEmpty() || reason.isEmpty()) {
      throw new IllegalStateException(
          "Persisted posting lineage is inconsistent: reversal reference and reason must be present together.");
    }
    return PostingLineageModel.reversal(
        new ReversalReference(new PostingId(priorPostingId.orElseThrow())),
        new ReversalReason(reason.orElseThrow()));
  }

  static dev.erst.fingrind.contract.bookkeeping.PostingLineage readPostingLineage(
      SqliteNativeStatement postingRow) {
    return BookkeepingPublishedLanguageTranslator.toPublished(readPostingLineageModel(postingRow));
  }

  static Optional<ReversalReference> readReversalReference(SqliteNativeStatement postingRow) {
    return readPostingLineageModel(postingRow).reversalReference();
  }

  static AccountingEvidence accountingEvidence(
      SqliteNativeStatement sourceDocumentRows, SqliteNativeStatement approvalRows) {
    return new AccountingEvidence(
        sourceDocumentReferences(sourceDocumentRows), approvalReferences(approvalRows));
  }

  static List<SourceDocumentReference> sourceDocumentReferences(
      SqliteNativeStatement sourceDocumentRows) {
    List<SourceDocumentReference> sourceDocuments = new ArrayList<>();
    while (sourceDocumentRows.step() == SqliteNativeResultCode.code("ROW")) {
      sourceDocuments.add(
          new SourceDocumentReference(
              new SourceDocumentId(
                  requiredText(
                      sourceDocumentRows, SqlitePostingColumnIndexes.COL_SOURCE_DOCUMENT_ID)),
              new SourceDocumentType(
                  requiredText(
                      sourceDocumentRows, SqlitePostingColumnIndexes.COL_SOURCE_DOCUMENT_TYPE)),
              CanonicalTemporalText.parseLocalDate(
                  requiredText(
                      sourceDocumentRows, SqlitePostingColumnIndexes.COL_SOURCE_DOCUMENT_DATE),
                  "sourceDocument.documentDate")));
    }
    return List.copyOf(sourceDocuments);
  }

  static List<ApprovalReference> approvalReferences(SqliteNativeStatement approvalRows) {
    List<ApprovalReference> approvals = new ArrayList<>();
    while (approvalRows.step() == SqliteNativeResultCode.code("ROW")) {
      approvals.add(
          new ApprovalReference(
              new ApprovalId(
                  requiredText(approvalRows, SqlitePostingColumnIndexes.COL_APPROVAL_ID)),
              new ApprovalType(
                  requiredText(approvalRows, SqlitePostingColumnIndexes.COL_APPROVAL_TYPE)),
              new ActorId(requiredText(approvalRows, SqlitePostingColumnIndexes.COL_APPROVER_ID)),
              ActorType.fromWireValue(
                  requiredText(approvalRows, SqlitePostingColumnIndexes.COL_APPROVER_TYPE)),
              ApprovalDecision.fromWireValue(
                  requiredText(approvalRows, SqlitePostingColumnIndexes.COL_APPROVAL_DECISION)),
              CanonicalTemporalText.parseUtcInstant(
                  requiredText(approvalRows, SqlitePostingColumnIndexes.COL_APPROVED_AT),
                  "approval.approvedAt")));
    }
    return List.copyOf(approvals);
  }

  static String requiredText(SqliteNativeStatement row, int columnIndex) {
    String value = row.columnText(columnIndex);
    return Objects.requireNonNull(
        value, "Required value at SQLite column index " + columnIndex + " is null.");
  }

  static Optional<String> optionalText(SqliteNativeStatement row, int columnIndex) {
    String value = row.columnText(columnIndex);
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(value);
  }

  static int requiredInt(SqliteNativeStatement row, int columnIndex) {
    return row.columnInt(columnIndex);
  }
}
