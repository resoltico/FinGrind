package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Posting, account, and query JSON records emitted by the CLI transport layer. */
public interface CliBookQueryJsonModels {

  record DeclaredAccountPayload(
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      String accountNodeKind,
      @Nullable String parentAccountCode,
      @Nullable String financialPositionLineClassification,
      @Nullable String profitAndLossLineClassification,
      String normalBalance,
      boolean active,
      String declaredAt)
      implements CliSuccessPayload {
    public DeclaredAccountPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      accountRole = requireText(accountRole, "accountRole");
      accountNodeKind = requireText(accountNodeKind, "accountNodeKind");
      parentAccountCode = requireOptionalText(parentAccountCode, "parentAccountCode");
      financialPositionLineClassification =
          requireOptionalText(
              financialPositionLineClassification, "financialPositionLineClassification");
      profitAndLossLineClassification =
          requireOptionalText(profitAndLossLineClassification, "profitAndLossLineClassification");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
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
      String sourceDocumentId,
      String sourceDocumentType,
      String documentDate,
      String capturedAt,
      String storageLocator,
      String contentSha256) {
    public SourceDocumentPayload {
      sourceDocumentId = requireText(sourceDocumentId, "sourceDocumentId");
      sourceDocumentType = requireText(sourceDocumentType, "sourceDocumentType");
      documentDate = requireText(documentDate, "documentDate");
      capturedAt = requireText(capturedAt, "capturedAt");
      storageLocator = requireText(storageLocator, "storageLocator");
      contentSha256 = requireText(contentSha256, "contentSha256");
    }
  }

  record ApprovalPayload(
      String approvalId,
      String approvalType,
      String approverId,
      String approverType,
      String decision,
      String approvedAt) {
    public ApprovalPayload {
      approvalId = requireText(approvalId, "approvalId");
      approvalType = requireText(approvalType, "approvalType");
      approverId = requireText(approverId, "approverId");
      approverType = requireText(approverType, "approverType");
      decision = requireText(decision, "decision");
      approvedAt = requireText(approvedAt, "approvedAt");
    }
  }

  record PostingPayload(
      String postingId,
      String postingKind,
      String reversalState,
      String effectiveDate,
      String recordedAt,
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId,
      String sourceChannel,
      AccountingEvidencePayload evidence,
      @Nullable ReversalPayload reversal,
      List<JournalLinePayload> lines)
      implements CliSuccessPayload {
    public PostingPayload {
      postingId = requireText(postingId, "postingId");
      postingKind = requireText(postingKind, "postingKind");
      reversalState = requireText(reversalState, "reversalState");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
      actorId = requireText(actorId, "actorId");
      actorType = requireText(actorType, "actorType");
      commandId = requireText(commandId, "commandId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      causationId = requireText(causationId, "causationId");
      correlationId = requireOptionalText(correlationId, "correlationId");
      sourceChannel = requireText(sourceChannel, "sourceChannel");
      Objects.requireNonNull(evidence, "evidence");
      lines = copyList(lines, "lines");
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

  record BookContextPayload(CliAdministrationJsonModels.BookIdentityPayload bookIdentity) {
    public BookContextPayload {
      Objects.requireNonNull(bookIdentity, "bookIdentity");
    }
  }

  record PostingQueryContextPayload(
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      @Nullable String accountCodeFilter,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateFromMeaning,
      @Nullable String effectiveDateTo,
      @Nullable String effectiveDateToMeaning) {
    public PostingQueryContextPayload {
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      accountCodeFilter = requireOptionalText(accountCodeFilter, "accountCodeFilter");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateFromMeaning =
          requireOptionalText(effectiveDateFromMeaning, "effectiveDateFromMeaning");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      effectiveDateToMeaning =
          requireOptionalText(effectiveDateToMeaning, "effectiveDateToMeaning");
    }
  }

  record PostingDetailsPayload(BookContextPayload context, PostingPayload posting)
      implements CliSuccessPayload {
    public PostingDetailsPayload {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(posting, "posting");
    }
  }

  record PostingListPayload(
      PostingQueryContextPayload context,
      int limit,
      @Nullable String nextCursor,
      List<PostingPayload> postings)
      implements CliSuccessPayload {
    public PostingListPayload {
      Objects.requireNonNull(context, "context");
      requirePositive(limit, "limit");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      postings = copyList(postings, "postings");
    }
  }

  record AccountListPayload(
      BookContextPayload context,
      int limit,
      @Nullable String nextCursor,
      List<DeclaredAccountPayload> accounts)
      implements CliSuccessPayload {
    public AccountListPayload {
      Objects.requireNonNull(context, "context");
      requirePositive(limit, "limit");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      accounts = copyList(accounts, "accounts");
    }
  }

  record AccountBalancePayload(
      CliReportJsonModels.ReportContextPayload context,
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      String normalBalance,
      boolean active,
      String declaredAt,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      List<BalanceBucketPayload> balances)
      implements CliSuccessPayload {
    public AccountBalancePayload {
      Objects.requireNonNull(context, "context");
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      accountRole = requireText(accountRole, "accountRole");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      balances = copyList(balances, "balances");
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
