package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Request template descriptor namespace for discovery commands. */
public interface ContractTemplates {
  /** Canonical request-template document for print-request-template. */
  public record PostingRequestTemplateDescriptor(
      BookkeepingEntryKind entryKind,
      String effectiveDate,
      @Nullable String cashAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable List<JournalLineTemplateDescriptor> lines,
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances,
      AccountingEvidenceTemplateDescriptor evidence,
      ProvenanceTemplateDescriptor provenance,
      @Nullable ReversalTemplateDescriptor reversal)
      implements TemplateDescriptorType {
    /** Validates one posting-request template descriptor payload. */
    public PostingRequestTemplateDescriptor {
      entryKind = ContractDescriptorValidation.requireValue(entryKind, "entryKind");
      effectiveDate = ContractDescriptorValidation.requireText(effectiveDate, "effectiveDate");
      cashAccountCode =
          ContractDescriptorValidation.requireOptionalText(cashAccountCode, "cashAccountCode");
      revenueAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              revenueAccountCode, "revenueAccountCode");
      expenseAccountCode =
          ContractDescriptorValidation.requireOptionalText(
              expenseAccountCode, "expenseAccountCode");
      equityAccountCode =
          ContractDescriptorValidation.requireOptionalText(equityAccountCode, "equityAccountCode");
      lines = lines == null ? null : ContractDescriptorValidation.copyList(lines, "lines");
      openingBalances =
          openingBalances == null
              ? null
              : ContractDescriptorValidation.copyList(openingBalances, "openingBalances");
      evidence = ContractDescriptorValidation.requireValue(evidence, "evidence");
      provenance = ContractDescriptorValidation.requireValue(provenance, "provenance");
      ContractPostingRequestTemplateValidators.validate(
          entryKind,
          new ContractPostingRequestTemplateValidators.PostingTemplateFields(
              cashAccountCode,
              revenueAccountCode,
              expenseAccountCode,
              equityAccountCode,
              amount,
              lines,
              openingBalances),
          reversal);
    }
  }

  /** Canonical request-template journal-line descriptor. */
  public record JournalLineTemplateDescriptor(
      String accountCode, JournalLine.EntrySide side, MonetaryAmount amount)
      implements TemplateDescriptorType {
    /** Validates one journal-line template descriptor payload. */
    public JournalLineTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      new AccountCode(accountCode);
      side = ContractDescriptorValidation.requireValue(side, "side");
      amount = ContractDescriptorValidation.requireValue(amount, "amount");
      if (!amount.toMoney().isPositive()) {
        throw new IllegalArgumentException("amount must carry one positive minor-unit value.");
      }
    }
  }

  /** Canonical request-template opening-balance descriptor. */
  public record OpeningBalanceTemplateDescriptor(
      String accountCode, JournalLine.EntrySide side, MonetaryAmount amount)
      implements TemplateDescriptorType {
    /** Validates one opening-balance template descriptor payload. */
    public OpeningBalanceTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      new AccountCode(accountCode);
      side = ContractDescriptorValidation.requireValue(side, "side");
      amount = ContractDescriptorValidation.requireValue(amount, "amount");
      if (!amount.toMoney().isPositive()) {
        throw new IllegalArgumentException("amount must carry one positive minor-unit value.");
      }
    }
  }

  /** Canonical request-template provenance descriptor. */
  public record ProvenanceTemplateDescriptor(
      String actorId,
      ActorType actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId)
      implements TemplateDescriptorType {
    /** Validates one provenance template descriptor payload. */
    public ProvenanceTemplateDescriptor {
      actorId = ContractDescriptorValidation.requireText(actorId, "actorId");
      actorType = ContractDescriptorValidation.requireValue(actorType, "actorType");
      commandId = ContractDescriptorValidation.requireText(commandId, "commandId");
      idempotencyKey = ContractDescriptorValidation.requireText(idempotencyKey, "idempotencyKey");
      causationId = ContractDescriptorValidation.requireText(causationId, "causationId");
      correlationId =
          ContractDescriptorValidation.requireOptionalText(correlationId, "correlationId");
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(actorId, ActorId::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          commandId, CommandId::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          idempotencyKey, IdempotencyKey::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          causationId, CausationId::new);
      ContractTemplateValidationSupport.validateLiveOptionalTextUnlessPlaceholder(
          correlationId, CorrelationId::new);
    }
  }

  /** Canonical request-template evidence descriptor. */
  public record AccountingEvidenceTemplateDescriptor(
      List<SourceDocumentTemplateDescriptor> sourceDocuments,
      List<ApprovalTemplateDescriptor> approvals)
      implements TemplateDescriptorType {
    /** Validates one evidence-template descriptor payload. */
    public AccountingEvidenceTemplateDescriptor {
      sourceDocuments = ContractDescriptorValidation.copyList(sourceDocuments, "sourceDocuments");
      approvals = ContractDescriptorValidation.copyList(approvals, "approvals");
      if (!ContractTemplateValidationSupport.containsPlaceholderEvidence(
          sourceDocuments, approvals)) {
        new AccountingEvidence(
            sourceDocuments.stream()
                .map(
                    sourceDocument ->
                        new SourceDocumentReference(
                            new SourceDocumentId(sourceDocument.sourceDocumentId()),
                            new SourceDocumentType(sourceDocument.sourceDocumentType()),
                            CanonicalTemporalText.parseLocalDate(
                                sourceDocument.documentDate(), "sourceDocuments.documentDate"),
                            CanonicalTemporalText.parseUtcInstant(
                                sourceDocument.capturedAt(), "sourceDocuments.capturedAt"),
                            new StorageLocator(sourceDocument.storageLocator()),
                            new ContentSha256(sourceDocument.contentSha256())))
                .toList(),
            approvals.stream()
                .map(
                    approval ->
                        new ApprovalReference(
                            new ApprovalId(approval.approvalId()),
                            new ApprovalType(approval.approvalType()),
                            new ActorId(approval.approverId()),
                            approval.approverType(),
                            approval.decision(),
                            CanonicalTemporalText.parseUtcInstant(
                                approval.approvedAt(), "approvals.approvedAt")))
                .toList());
      }
    }
  }

  /** Canonical request-template source-document descriptor. */
  public record SourceDocumentTemplateDescriptor(
      String sourceDocumentId,
      String sourceDocumentType,
      String documentDate,
      String capturedAt,
      String storageLocator,
      String contentSha256)
      implements TemplateDescriptorType {
    /** Validates one source-document template descriptor payload. */
    public SourceDocumentTemplateDescriptor {
      sourceDocumentId =
          ContractDescriptorValidation.requireText(sourceDocumentId, "sourceDocumentId");
      sourceDocumentType =
          ContractDescriptorValidation.requireText(sourceDocumentType, "sourceDocumentType");
      documentDate = ContractDescriptorValidation.requireText(documentDate, "documentDate");
      capturedAt = ContractDescriptorValidation.requireText(capturedAt, "capturedAt");
      storageLocator = ContractDescriptorValidation.requireText(storageLocator, "storageLocator");
      contentSha256 = ContractDescriptorValidation.requireText(contentSha256, "contentSha256");
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          sourceDocumentId, SourceDocumentId::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          sourceDocumentType, SourceDocumentType::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          documentDate, value -> CanonicalTemporalText.parseLocalDate(value, "documentDate"));
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          capturedAt, value -> CanonicalTemporalText.parseUtcInstant(value, "capturedAt"));
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          storageLocator, StorageLocator::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          contentSha256, ContentSha256::new);
    }
  }

  /** Canonical request-template approval descriptor. */
  public record ApprovalTemplateDescriptor(
      String approvalId,
      String approvalType,
      String approverId,
      ActorType approverType,
      ApprovalDecision decision,
      String approvedAt)
      implements TemplateDescriptorType {
    /** Validates one approval template descriptor payload. */
    public ApprovalTemplateDescriptor {
      approvalId = ContractDescriptorValidation.requireText(approvalId, "approvalId");
      approvalType = ContractDescriptorValidation.requireText(approvalType, "approvalType");
      approverId = ContractDescriptorValidation.requireText(approverId, "approverId");
      approverType = ContractDescriptorValidation.requireValue(approverType, "approverType");
      decision = ContractDescriptorValidation.requireValue(decision, "decision");
      approvedAt = ContractDescriptorValidation.requireText(approvedAt, "approvedAt");
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          approvalId, ApprovalId::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          approvalType, ApprovalType::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(approverId, ActorId::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          approvedAt, value -> CanonicalTemporalText.parseUtcInstant(value, "approvedAt"));
    }
  }

  /** Canonical request-template reversal descriptor. */
  public record ReversalTemplateDescriptor(String priorPostingId, String reason)
      implements TemplateDescriptorType {
    /** Validates one reversal template descriptor payload. */
    public ReversalTemplateDescriptor {
      priorPostingId = ContractDescriptorValidation.requireText(priorPostingId, "priorPostingId");
      reason = ContractDescriptorValidation.requireText(reason, "reason");
    }
  }

  /** Canonical declare-account template nested inside a ledger plan. */
  public record DeclareAccountTemplateDescriptor(
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountNodeKind accountNodeKind,
      @Nullable String parentAccountCode,
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification)
      implements TemplateDescriptorType {
    /** Validates one declare-account template descriptor payload. */
    public DeclareAccountTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      new AccountCode(accountCode);
      accountName = ContractDescriptorValidation.requireText(accountName, "accountName");
      accountType = ContractDescriptorValidation.requireValue(accountType, "accountType");
      accountRole = ContractDescriptorValidation.requireValue(accountRole, "accountRole");
      accountNodeKind =
          ContractDescriptorValidation.requireValue(accountNodeKind, "accountNodeKind");
      parentAccountCode =
          ContractDescriptorValidation.requireOptionalText(parentAccountCode, "parentAccountCode");
      if (parentAccountCode != null) {
        new AccountCode(parentAccountCode);
      }
      financialPositionLineClassification =
          ContractDescriptorValidation.requireOptionalValue(
              financialPositionLineClassification, "financialPositionLineClassification");
      profitAndLossLineClassification =
          ContractDescriptorValidation.requireOptionalValue(
              profitAndLossLineClassification, "profitAndLossLineClassification");
    }
  }
}
