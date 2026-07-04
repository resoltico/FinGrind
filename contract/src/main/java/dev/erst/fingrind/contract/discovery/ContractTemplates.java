package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Request template descriptor namespace for discovery commands. */
public interface ContractTemplates {
  /** Canonical request-template document for print-request-template. */
  public record PostingRequestTemplateDescriptor(
      BookkeepingEntryKind entryKind,
      String effectiveDate,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief,
      @Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct,
      @Nullable ForeignExchangeTemplateDescriptor foreignExchange,
      @Nullable TaxSelectionTemplateDescriptor tax,
      @Nullable List<JournalLineTemplateDescriptor> lines,
      @Nullable List<OpeningBalanceTemplateDescriptor> openingBalances,
      AccountingEvidenceTemplateDescriptor evidence,
      ProvenanceTemplateDescriptor provenance,
      @Nullable ReversalTemplateDescriptor reversal)
      implements TemplateDescriptorType {
    /** Validates one posting-request template descriptor payload. */
    public PostingRequestTemplateDescriptor {
      var validated =
          ContractPostingRequestTemplateDescriptorValidationSupport.validate(
              new ContractPostingRequestTemplateDescriptorValidationSupport
                  .PostingRequestTemplateDraft(
                  entryKind,
                  effectiveDate,
                  cashAccountCode,
                  receivableAccountCode,
                  payableAccountCode,
                  revenueAccountCode,
                  inventoryAccountCode,
                  expenseAccountCode,
                  equityAccountCode,
                  amount,
                  inventoryRelief,
                  settlementAdjunct,
                  foreignExchange,
                  tax,
                  lines,
                  openingBalances,
                  evidence,
                  provenance,
                  reversal));
      entryKind = validated.entryKind();
      effectiveDate = validated.effectiveDate();
      cashAccountCode = validated.cashAccountCode();
      receivableAccountCode = validated.receivableAccountCode();
      payableAccountCode = validated.payableAccountCode();
      revenueAccountCode = validated.revenueAccountCode();
      inventoryAccountCode = validated.inventoryAccountCode();
      expenseAccountCode = validated.expenseAccountCode();
      equityAccountCode = validated.equityAccountCode();
      amount = validated.amount();
      inventoryRelief = validated.inventoryRelief();
      settlementAdjunct = validated.settlementAdjunct();
      foreignExchange = validated.foreignExchange();
      tax = validated.tax();
      lines = validated.lines();
      openingBalances = validated.openingBalances();
      evidence = validated.evidence();
      provenance = validated.provenance();
      reversal = validated.reversal();
    }
  }

  /** Canonical settlement-adjunct template nested inside receipt and payment requests. */
  public record SettlementAdjunctTemplateDescriptor(String accountCode, MonetaryAmount amount)
      implements TemplateDescriptorType {
    /** Validates one settlement-adjunct template descriptor payload. */
    public SettlementAdjunctTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      new AccountCode(accountCode);
      amount = ContractDescriptorValidation.requireValue(amount, "amount");
      if (!amount.toMoney().isPositive()) {
        throw new IllegalArgumentException("amount must carry one positive minor-unit value.");
      }
    }
  }

  /** Canonical request-side tax selector nested inside one posting request template. */
  public record TaxSelectionTemplateDescriptor(String taxRegistrationId, String taxCode)
      implements TemplateDescriptorType {
    /** Validates one tax-selection template descriptor payload. */
    public TaxSelectionTemplateDescriptor {
      taxRegistrationId =
          ContractDescriptorValidation.requireText(taxRegistrationId, "taxRegistrationId");
      taxCode = ContractDescriptorValidation.requireText(taxCode, "taxCode");
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          taxRegistrationId, TaxRegistrationId::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(taxCode, TaxCode::new);
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
      var validated =
          ContractTemplateValidationSupport.validateProvenanceTemplate(
              actorId, actorType, commandId, idempotencyKey, causationId, correlationId);
      actorId = validated.actorId();
      actorType = validated.actorType();
      commandId = validated.commandId();
      idempotencyKey = validated.idempotencyKey();
      causationId = validated.causationId();
      correlationId = validated.correlationId();
    }
  }

  /** Canonical request-template evidence descriptor. */
  public record AccountingEvidenceTemplateDescriptor(
      List<SourceDocumentTemplateDescriptor> sourceDocuments,
      List<ApprovalTemplateDescriptor> approvals)
      implements TemplateDescriptorType {
    /** Validates one evidence-template descriptor payload. */
    public AccountingEvidenceTemplateDescriptor {
      var validated =
          ContractTemplateValidationSupport.validateAccountingEvidenceTemplate(
              sourceDocuments, approvals);
      sourceDocuments = validated.sourceDocuments();
      approvals = validated.approvals();
    }
  }

  /** Canonical request-template source-document descriptor. */
  public record SourceDocumentTemplateDescriptor(
      String sourceDocumentId, String sourceDocumentType, String documentDate)
      implements TemplateDescriptorType {
    /** Validates one source-document template descriptor payload. */
    public SourceDocumentTemplateDescriptor {
      var validated =
          ContractTemplateValidationSupport.validateSourceDocumentTemplate(
              sourceDocumentId, sourceDocumentType, documentDate);
      sourceDocumentId = validated.sourceDocumentId();
      sourceDocumentType = validated.sourceDocumentType();
      documentDate = validated.documentDate();
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
      var validated =
          ContractTemplateValidationSupport.validateApprovalTemplate(
              approvalId, approvalType, approverId, approverType, decision, approvedAt);
      approvalId = validated.approvalId();
      approvalType = validated.approvalType();
      approverId = validated.approverId();
      approverType = validated.approverType();
      decision = validated.decision();
      approvedAt = validated.approvedAt();
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
      AccountNodeKind accountNodeKind,
      @Nullable String parentAccountCode,
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification,
      @Nullable CashFlowAssetClassification cashFlowAssetClassification)
      implements TemplateDescriptorType {
    /** Validates one declare-account template descriptor payload. */
    public DeclareAccountTemplateDescriptor {
      var validated =
          ContractDeclarationTemplateValidationSupport.validateDeclareAccountTemplate(
              accountCode,
              accountName,
              accountType,
              accountNodeKind,
              parentAccountCode,
              financialPositionLineClassification,
              profitAndLossLineClassification,
              cashFlowAssetClassification);
      accountCode = validated.accountCode();
      accountName = validated.accountName();
      accountType = validated.accountType();
      accountNodeKind = validated.accountNodeKind();
      parentAccountCode = validated.parentAccountCode();
      financialPositionLineClassification = validated.financialPositionLineClassification();
      profitAndLossLineClassification = validated.profitAndLossLineClassification();
      cashFlowAssetClassification = validated.cashFlowAssetClassification();
    }
  }

  /** Canonical declare-tax-registration request template descriptor. */
  public record DeclareTaxRegistrationTemplateDescriptor(
      String taxRegistrationId,
      String taxRegistrationName,
      TaxJurisdiction jurisdiction,
      @Nullable String registrationNumber,
      String payableAccountCode,
      String recoverableAccountCode,
      TaxObligationFrequency obligationFrequency,
      int dueDaysAfterPeriodEnd,
      List<DeclareTaxCodeTemplateDescriptor> taxCodes)
      implements TemplateDescriptorType {
    /** Validates one declare-tax-registration template descriptor payload. */
    public DeclareTaxRegistrationTemplateDescriptor {
      var validated =
          ContractDeclarationTemplateValidationSupport.validateDeclareTaxRegistrationTemplate(
              taxRegistrationId,
              taxRegistrationName,
              jurisdiction,
              registrationNumber,
              payableAccountCode,
              recoverableAccountCode,
              obligationFrequency,
              dueDaysAfterPeriodEnd,
              taxCodes);
      taxRegistrationId = validated.taxRegistrationId();
      taxRegistrationName = validated.taxRegistrationName();
      jurisdiction = validated.jurisdiction();
      registrationNumber = validated.registrationNumber();
      payableAccountCode = validated.payableAccountCode();
      recoverableAccountCode = validated.recoverableAccountCode();
      obligationFrequency = validated.obligationFrequency();
      dueDaysAfterPeriodEnd = validated.dueDaysAfterPeriodEnd();
      taxCodes = validated.taxCodes();
    }
  }

  /** Canonical declared tax-code template nested inside one tax registration. */
  public record DeclareTaxCodeTemplateDescriptor(
      String taxCode,
      String taxCodeName,
      int ratePartsPerMillion,
      TaxInclusionMode inclusionMode,
      TaxApplicationKind applicationKind)
      implements TemplateDescriptorType {
    /** Validates one declared tax-code template descriptor payload. */
    public DeclareTaxCodeTemplateDescriptor {
      var validated =
          ContractDeclarationTemplateValidationSupport.validateDeclareTaxCodeTemplate(
              taxCode, taxCodeName, ratePartsPerMillion, inclusionMode, applicationKind);
      taxCode = validated.taxCode();
      taxCodeName = validated.taxCodeName();
      ratePartsPerMillion = validated.ratePartsPerMillion();
      inclusionMode = validated.inclusionMode();
      applicationKind = validated.applicationKind();
    }
  }
}
