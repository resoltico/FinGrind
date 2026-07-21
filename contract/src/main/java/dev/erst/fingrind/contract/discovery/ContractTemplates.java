package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Request template descriptor namespace for discovery commands. */
public interface ContractTemplates extends ContractPostingRequestTemplates {

  public record RecognitionIntervalTemplateDescriptor(String startDate, String endDate) {
    public RecognitionIntervalTemplateDescriptor {
      startDate = ContractDescriptorValidation.requireText(startDate, "startDate");
      endDate = ContractDescriptorValidation.requireText(endDate, "endDate");
      new AccrualCutoffRecognitionInterval(LocalDate.parse(startDate), LocalDate.parse(endDate));
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
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId)
      implements TemplateDescriptorType {
    public ProvenanceTemplateDescriptor {
      var validated =
          ContractTemplateValidationSupport.validateProvenanceTemplate(
              commandId, idempotencyKey, causationId, correlationId);
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
      String approverReference,
      String approverType,
      ApprovalDecision decision,
      String approvedAt)
      implements TemplateDescriptorType {
    public ApprovalTemplateDescriptor {
      var validated =
          ContractTemplateValidationSupport.validateApprovalTemplate(
              approvalId, approvalType, approverReference, approverType, decision, approvedAt);
      approvalId = validated.approvalId();
      approvalType = validated.approvalType();
      approverReference = validated.approverReference();
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
      @Nullable String contraOfAccountCode,
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification,
      @Nullable CashFlowAssetClassification cashFlowAssetClassification,
      @Nullable UnitOfMeasure unitOfMeasure)
      implements TemplateDescriptorType {
    public DeclareAccountTemplateDescriptor {
      var validated =
          ContractDeclarationTemplateValidationSupport.validateDeclareAccountTemplate(
              new ContractDeclarationTemplateValidationSupport.DeclareAccountTemplateValues(
                  accountCode,
                  accountName,
                  accountType,
                  accountNodeKind,
                  parentAccountCode,
                  contraOfAccountCode,
                  financialPositionLineClassification,
                  profitAndLossLineClassification,
                  cashFlowAssetClassification,
                  unitOfMeasure));
      accountCode = validated.accountCode();
      accountName = validated.accountName();
      accountType = validated.accountType();
      accountNodeKind = validated.accountNodeKind();
      parentAccountCode = validated.parentAccountCode();
      contraOfAccountCode = validated.contraOfAccountCode();
      financialPositionLineClassification = validated.financialPositionLineClassification();
      profitAndLossLineClassification = validated.profitAndLossLineClassification();
      cashFlowAssetClassification = validated.cashFlowAssetClassification();
      unitOfMeasure = validated.unitOfMeasure();
    }
  }

  /** Minimal request scaffold for retiring one declared account. */
  public record RetireAccountTemplateDescriptor(String accountCode)
      implements TemplateDescriptorType {
    public RetireAccountTemplateDescriptor {
      accountCode =
          ContractDeclarationTemplateValidationSupport.validateRetireAccountTemplate(accountCode);
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
