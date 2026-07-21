package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Posting, account, and query JSON records emitted by the CLI transport layer. */
public interface CliBookQueryJsonModels {

  record DeclaredAccountPayload(
      String accountCode,
      String accountName,
      String accountType,
      String accountNodeKind,
      @Nullable String parentAccountCode,
      @Nullable String contraOfAccountCode,
      @Nullable String financialPositionLineClassification,
      @Nullable String cashFlowAssetClassification,
      @Nullable String profitAndLossLineClassification,
      @Nullable UnitOfMeasurePayload unitOfMeasure,
      String normalBalance,
      boolean active,
      String declaredAt)
      implements CliSuccessPayload {
    public DeclaredAccountPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      accountNodeKind = requireText(accountNodeKind, "accountNodeKind");
      parentAccountCode = requireOptionalText(parentAccountCode, "parentAccountCode");
      contraOfAccountCode = requireOptionalText(contraOfAccountCode, "contraOfAccountCode");
      financialPositionLineClassification =
          requireOptionalText(
              financialPositionLineClassification, "financialPositionLineClassification");
      cashFlowAssetClassification =
          requireOptionalText(cashFlowAssetClassification, "cashFlowAssetClassification");
      profitAndLossLineClassification =
          requireOptionalText(profitAndLossLineClassification, "profitAndLossLineClassification");
      if (unitOfMeasure != null) {
        new UnitOfMeasure(unitOfMeasure.token(), unitOfMeasure.quantityScale());
      }
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
    }
  }

  record UnitOfMeasurePayload(String token, int quantityScale) {
    public UnitOfMeasurePayload {
      new UnitOfMeasure(requireText(token, "token"), quantityScale);
    }
  }

  record AccountingEvidencePayload(
      List<SourceDocumentPayload> sourceDocuments, List<ApprovalPayload> approvals) {
    public AccountingEvidencePayload {
      sourceDocuments = copyList(sourceDocuments, "sourceDocuments");
      approvals = copyList(approvals, "approvals");
    }
  }

  record SourceDocumentPayload(
      String sourceDocumentId, String sourceDocumentType, String documentDate) {
    public SourceDocumentPayload {
      sourceDocumentId = requireText(sourceDocumentId, "sourceDocumentId");
      sourceDocumentType = requireText(sourceDocumentType, "sourceDocumentType");
      documentDate = requireText(documentDate, "documentDate");
    }
  }

  record ApprovalPayload(
      String approvalId,
      String approvalType,
      String approverReference,
      String approverType,
      String decision,
      String approvedAt) {
    public ApprovalPayload {
      approvalId = requireText(approvalId, "approvalId");
      approvalType = requireText(approvalType, "approvalType");
      approverReference = requireText(approverReference, "approverReference");
      approverType = requireText(approverType, "approverType");
      decision = requireText(decision, "decision");
      approvedAt = requireText(approvedAt, "approvedAt");
    }
  }

  record PostingPayload(
      String postingId,
      String postingKind,
      String postingOriginKind,
      String reversalState,
      @Nullable String reversesPostingId,
      @Nullable String reversedByPostingId,
      String effectiveDate,
      String recordedAt,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId,
      String sourceChannel,
      AccountingEvidencePayload evidence,
      @Nullable CliPostingEntryPayload entry,
      @Nullable ReversalPayload reversal,
      List<JournalLinePayload> lines)
      implements CliSuccessPayload {
    public PostingPayload {
      postingId = requireText(postingId, "postingId");
      postingKind = requireText(postingKind, "postingKind");
      postingOriginKind = requireText(postingOriginKind, "postingOriginKind");
      reversalState = requireText(reversalState, "reversalState");
      reversesPostingId = requireOptionalText(reversesPostingId, "reversesPostingId");
      reversedByPostingId = requireOptionalText(reversedByPostingId, "reversedByPostingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
      commandId = requireText(commandId, "commandId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      causationId = requireText(causationId, "causationId");
      correlationId = requireOptionalText(correlationId, "correlationId");
      sourceChannel = requireText(sourceChannel, "sourceChannel");
      Objects.requireNonNull(evidence, "evidence");
      lines = copyList(lines, "lines");
    }
  }

  record PostingSummaryPayload(
      String postingId,
      String postingKind,
      String postingOriginKind,
      String reversalState,
      @Nullable String reversesPostingId,
      @Nullable String reversedByPostingId,
      String effectiveDate,
      String recordedAt,
      MonetaryAmount debitTotal,
      MonetaryAmount creditTotal,
      List<String> accountCodes,
      List<String> sourceDocumentIds,
      List<String> approvalIds) {
    public PostingSummaryPayload {
      postingId = requireText(postingId, "postingId");
      postingKind = requireText(postingKind, "postingKind");
      postingOriginKind = requireText(postingOriginKind, "postingOriginKind");
      reversalState = requireText(reversalState, "reversalState");
      reversesPostingId = requireOptionalText(reversesPostingId, "reversesPostingId");
      reversedByPostingId = requireOptionalText(reversedByPostingId, "reversedByPostingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
      Objects.requireNonNull(debitTotal, "debitTotal");
      Objects.requireNonNull(creditTotal, "creditTotal");
      accountCodes = copyList(accountCodes, "accountCodes");
      sourceDocumentIds = copyList(sourceDocumentIds, "sourceDocumentIds");
      approvalIds = copyList(approvalIds, "approvalIds");
    }
  }

  record ReversalPayload(String priorPostingId, String reason) {
    public ReversalPayload {
      priorPostingId = requireText(priorPostingId, "priorPostingId");
      reason = requireText(reason, "reason");
    }
  }

  record JournalLinePayload(String accountCode, String side, MonetaryAmount amount) {
    public JournalLinePayload {
      accountCode = requireText(accountCode, "accountCode");
      side = requireText(side, "side");
      Objects.requireNonNull(amount, "amount");
    }
  }

  record PostingDetailsPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      GetPostingResolvedQuery resolvedQuery,
      String generatedAt,
      PostingPayload posting)
      implements ProtocolSuccessPayload {
    public PostingDetailsPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      Objects.requireNonNull(posting, "posting");
    }
  }

  /** The exact selected posting identity. */
  record GetPostingResolvedQuery(String postingId) {
    public GetPostingResolvedQuery {
      postingId = requireText(postingId, "postingId");
    }
  }

  record PostingListPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      PostingListResolvedQuery resolvedQuery,
      String generatedAt,
      @Nullable String nextCursor,
      List<PostingSummaryPayload> postings)
      implements ProtocolSuccessPayload {
    public PostingListPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      postings = copyList(postings, "postings");
    }
  }

  record AccountListPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      AccountListResolvedQuery resolvedQuery,
      String generatedAt,
      @Nullable String nextCursor,
      List<DeclaredAccountPayload> accounts)
      implements ProtocolSuccessPayload {
    public AccountListPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      accounts = copyList(accounts, "accounts");
    }
  }

  /** The exact accepted account-register page selection. */
  record AccountListResolvedQuery(int limit, @Nullable String cursor) {
    public AccountListResolvedQuery {
      requirePositive(limit, "limit");
      cursor = requireOptionalText(cursor, "cursor");
    }
  }

  /** The exact accepted posting-register page selection. */
  record PostingListResolvedQuery(
      @Nullable String accountCodeFilter,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      int limit,
      @Nullable String cursor) {
    public PostingListResolvedQuery {
      accountCodeFilter = requireOptionalText(accountCodeFilter, "accountCodeFilter");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      requirePositive(limit, "limit");
      cursor = requireOptionalText(cursor, "cursor");
    }
  }

  record BalanceBucketPayload(
      MonetaryAmount debitTotal,
      MonetaryAmount creditTotal,
      MonetaryAmount netAmount,
      String balanceSide) {
    public BalanceBucketPayload {
      Objects.requireNonNull(debitTotal, "debitTotal");
      Objects.requireNonNull(creditTotal, "creditTotal");
      Objects.requireNonNull(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }
}
