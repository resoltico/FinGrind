package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
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
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Request and ledger-plan template descriptor namespace for discovery commands. */
public final class ContractTemplates {
  private ContractTemplates() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return DescriptorNamespaceSupport.descriptorTypes(TemplateDescriptorType.class);
  }

  /** Sealed inventory root for the request-template descriptor namespace. */
  public sealed interface TemplateDescriptorType
      permits PostingRequestTemplateDescriptor,
          JournalLineTemplateDescriptor,
          AccountingEvidenceTemplateDescriptor,
          SourceDocumentTemplateDescriptor,
          ApprovalTemplateDescriptor,
          ProvenanceTemplateDescriptor,
          ReversalTemplateDescriptor,
          LedgerPlanTemplateDescriptor,
          LedgerPlanStepTemplateDescriptor,
          OpenBookTemplateDescriptor,
          LedgerPlanQueryTemplateDescriptor,
          DeclareAccountTemplateDescriptor,
          LedgerAssertionTemplateDescriptor {}

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
              lines),
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
      new IdempotencyKey(idempotencyKey);
      causationId = ContractDescriptorValidation.requireText(causationId, "causationId");
      correlationId =
          ContractDescriptorValidation.requireOptionalText(correlationId, "correlationId");
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
      new SourceDocumentId(sourceDocumentId);
      new SourceDocumentType(sourceDocumentType);
      CanonicalTemporalText.parseLocalDate(documentDate, "documentDate");
      CanonicalTemporalText.parseUtcInstant(capturedAt, "capturedAt");
      new StorageLocator(storageLocator);
      new ContentSha256(contentSha256);
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
      new ApprovalId(approvalId);
      new ApprovalType(approvalType);
      new ActorId(approverId);
      CanonicalTemporalText.parseUtcInstant(approvedAt, "approvedAt");
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

  /** Canonical ledger-plan template document for print-plan-template. */
  public record LedgerPlanTemplateDescriptor(
      String planId, List<LedgerPlanStepTemplateDescriptor> steps)
      implements TemplateDescriptorType {
    /** Validates one ledger-plan template descriptor payload. */
    public LedgerPlanTemplateDescriptor {
      planId = ContractDescriptorValidation.requireText(planId, "planId");
      steps = ContractDescriptorValidation.copyList(steps, "steps");
      if (steps.isEmpty()) {
        throw new IllegalArgumentException("steps must not be empty.");
      }
    }
  }

  /** Canonical ledger-plan step template descriptor. */
  public record LedgerPlanStepTemplateDescriptor(
      String stepId,
      LedgerStepKind kind,
      @Nullable OpenBookTemplateDescriptor openBook,
      @Nullable PostingRequestTemplateDescriptor posting,
      @Nullable DeclareAccountTemplateDescriptor declareAccount,
      @Nullable LedgerPlanQueryTemplateDescriptor query,
      @Nullable LedgerAssertionTemplateDescriptor assertion,
      @Nullable String postingId)
      implements TemplateDescriptorType {
    /** Validates one ledger-plan step template descriptor payload. */
    public LedgerPlanStepTemplateDescriptor {
      stepId = ContractDescriptorValidation.requireText(stepId, "stepId");
      kind = ContractDescriptorValidation.requireValue(kind, "kind");
      postingId = ContractDescriptorValidation.requireOptionalText(postingId, "postingId");
      ContractTemplateShapeValidator.validateStepShape(
          kind, openBook, posting, declareAccount, query, assertion, postingId);
    }
  }

  /** Canonical open-book template nested inside a ledger plan. */
  public record OpenBookTemplateDescriptor(
      String entityName,
      List<String> businessActivityTags,
      String functionalCurrency,
      String fiscalYearStart)
      implements TemplateDescriptorType {
    /** Validates one open-book template descriptor payload. */
    public OpenBookTemplateDescriptor {
      entityName = ContractDescriptorValidation.requireText(entityName, "entityName");
      new BookEntityName(entityName);
      businessActivityTags =
          ContractDescriptorValidation.copyList(businessActivityTags, "businessActivityTags");
      businessActivityTags.forEach(BusinessActivityTag::new);
      functionalCurrency =
          ContractDescriptorValidation.requireText(functionalCurrency, "functionalCurrency");
      CurrencyUnit.of(functionalCurrency);
      fiscalYearStart =
          ContractDescriptorValidation.requireText(fiscalYearStart, "fiscalYearStart");
      FiscalYearStart.parse(fiscalYearStart);
    }
  }

  /** Canonical ledger-plan query template nested inside query-oriented steps. */
  public record LedgerPlanQueryTemplateDescriptor(
      @Nullable String accountCode,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      @Nullable Integer limit,
      @Nullable String cursor)
      implements TemplateDescriptorType {
    /** Validates one ledger-plan query template descriptor payload. */
    public LedgerPlanQueryTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireOptionalText(accountCode, "accountCode");
      effectiveDateFrom =
          ContractDescriptorValidation.requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo =
          ContractDescriptorValidation.requireOptionalText(effectiveDateTo, "effectiveDateTo");
      if (limit != null
          && (limit < InteractionLimits.PAGE_LIMIT_MIN
              || limit > InteractionLimits.PAGE_LIMIT_MAX)) {
        throw new IllegalArgumentException(
            "limit must be between "
                + InteractionLimits.PAGE_LIMIT_MIN
                + " and "
                + InteractionLimits.PAGE_LIMIT_MAX
                + ".");
      }
      cursor = ContractDescriptorValidation.requireOptionalText(cursor, "cursor");
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

  /** Canonical assertion template nested inside a ledger plan. */
  public record LedgerAssertionTemplateDescriptor(
      LedgerAssertionKind kind,
      @Nullable String accountCode,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      @Nullable MonetaryAmount netAmount,
      @Nullable BalanceSide balanceSide,
      @Nullable String postingId)
      implements TemplateDescriptorType {
    /** Validates one ledger-assertion template descriptor payload. */
    public LedgerAssertionTemplateDescriptor {
      kind = ContractDescriptorValidation.requireValue(kind, "kind");
      accountCode = ContractDescriptorValidation.requireOptionalText(accountCode, "accountCode");
      effectiveDateFrom =
          ContractDescriptorValidation.requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo =
          ContractDescriptorValidation.requireOptionalText(effectiveDateTo, "effectiveDateTo");
      netAmount = ContractDescriptorValidation.requireOptionalValue(netAmount, "netAmount");
      postingId = ContractDescriptorValidation.requireOptionalText(postingId, "postingId");
      ContractTemplateShapeValidator.validateAssertionShape(
          kind, accountCode, effectiveDateFrom, effectiveDateTo, netAmount, balanceSide, postingId);
    }
  }
}
