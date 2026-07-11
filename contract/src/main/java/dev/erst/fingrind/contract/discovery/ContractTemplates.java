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
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Request template descriptor namespace for discovery commands. */
public interface ContractTemplates extends ContractReversalTemplates {
  public record PostingRequestTemplateDescriptor(
      BookkeepingEntryKind entryKind,
      String effectiveDate,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String writeDownLossAccountCode,
      @Nullable String shrinkageLossAccountCode,
      @Nullable String countGainAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount,
      @Nullable String quantity,
      @Nullable MonetaryAmount unitCost,
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
                  writeDownLossAccountCode,
                  shrinkageLossAccountCode,
                  countGainAccountCode,
                  equityAccountCode,
                  amount,
                  quantity,
                  unitCost,
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
      writeDownLossAccountCode = validated.writeDownLossAccountCode();
      shrinkageLossAccountCode = validated.shrinkageLossAccountCode();
      countGainAccountCode = validated.countGainAccountCode();
      equityAccountCode = validated.equityAccountCode();
      amount = validated.amount();
      quantity = validated.quantity();
      unitCost = validated.unitCost();
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

  public record SettlementAdjunctTemplateDescriptor(String accountCode, MonetaryAmount amount)
      implements TemplateDescriptorType {
    public SettlementAdjunctTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      new AccountCode(accountCode);
      amount = ContractDescriptorValidation.requireValue(amount, "amount");
      if (!amount.toMoney().isPositive()) {
        throw new IllegalArgumentException("amount must carry one positive minor-unit value.");
      }
    }
  }

  public record TaxSelectionTemplateDescriptor(String taxRegistrationId, String taxCode)
      implements TemplateDescriptorType {
    public TaxSelectionTemplateDescriptor {
      taxRegistrationId =
          ContractDescriptorValidation.requireText(taxRegistrationId, "taxRegistrationId");
      taxCode = ContractDescriptorValidation.requireText(taxCode, "taxCode");
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          taxRegistrationId, TaxRegistrationId::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(taxCode, TaxCode::new);
    }
  }

  public record JournalLineTemplateDescriptor(
      String accountCode, JournalLine.EntrySide side, MonetaryAmount amount)
      implements TemplateDescriptorType {
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

  public record OpeningBalanceTemplateDescriptor(
      String accountCode, JournalLine.EntrySide side, MonetaryAmount amount)
      implements TemplateDescriptorType {
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

  public record ProvenanceTemplateDescriptor(
      String actorId,
      ActorType actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId)
      implements TemplateDescriptorType {
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

  public record AccountingEvidenceTemplateDescriptor(
      List<SourceDocumentTemplateDescriptor> sourceDocuments,
      List<ApprovalTemplateDescriptor> approvals)
      implements TemplateDescriptorType {
    public AccountingEvidenceTemplateDescriptor {
      var validated =
          ContractTemplateValidationSupport.validateAccountingEvidenceTemplate(
              sourceDocuments, approvals);
      sourceDocuments = validated.sourceDocuments();
      approvals = validated.approvals();
    }
  }

  public record SourceDocumentTemplateDescriptor(
      String sourceDocumentId, String sourceDocumentType, String documentDate)
      implements TemplateDescriptorType {
    public SourceDocumentTemplateDescriptor {
      var validated =
          ContractTemplateValidationSupport.validateSourceDocumentTemplate(
              sourceDocumentId, sourceDocumentType, documentDate);
      sourceDocumentId = validated.sourceDocumentId();
      sourceDocumentType = validated.sourceDocumentType();
      documentDate = validated.documentDate();
    }
  }

  public record ApprovalTemplateDescriptor(
      String approvalId,
      String approvalType,
      String approverId,
      ActorType approverType,
      ApprovalDecision decision,
      String approvedAt)
      implements TemplateDescriptorType {
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

  public record DeclareAccountTemplateDescriptor(
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountNodeKind accountNodeKind,
      @Nullable String parentAccountCode,
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification,
      @Nullable CashFlowAssetClassification cashFlowAssetClassification,
      @Nullable UnitOfMeasure unitOfMeasure)
      implements TemplateDescriptorType {
    /** Convenience constructor for non-inventory account declaration templates. */
    public DeclareAccountTemplateDescriptor(
        String accountCode,
        String accountName,
        AccountType accountType,
        AccountNodeKind accountNodeKind,
        @Nullable String parentAccountCode,
        @Nullable FinancialPositionLineClassification financialPositionLineClassification,
        @Nullable ProfitAndLossLineClassification profitAndLossLineClassification,
        @Nullable CashFlowAssetClassification cashFlowAssetClassification) {
      this(
          accountCode,
          accountName,
          accountType,
          accountNodeKind,
          parentAccountCode,
          financialPositionLineClassification,
          profitAndLossLineClassification,
          cashFlowAssetClassification,
          null);
    }

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
              cashFlowAssetClassification,
              unitOfMeasure);
      accountCode = validated.accountCode();
      accountName = validated.accountName();
      accountType = validated.accountType();
      accountNodeKind = validated.accountNodeKind();
      parentAccountCode = validated.parentAccountCode();
      financialPositionLineClassification = validated.financialPositionLineClassification();
      profitAndLossLineClassification = validated.profitAndLossLineClassification();
      cashFlowAssetClassification = validated.cashFlowAssetClassification();
      unitOfMeasure = validated.unitOfMeasure();
    }
  }

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

  public record DeclareTaxCodeTemplateDescriptor(
      String taxCode,
      String taxCodeName,
      int ratePartsPerMillion,
      TaxInclusionMode inclusionMode,
      TaxApplicationKind applicationKind)
      implements TemplateDescriptorType {
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
