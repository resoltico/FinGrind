package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.InventoryCostingDoctrine;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Ledger-plan template descriptor namespace for discovery commands. */
public interface ContractPlanTemplates {
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

    /** Returns the single published scaffold posting step for execute-plan discovery. */
    public LedgerPlanStepTemplateDescriptor canonicalPostingScaffoldStep() {
      LedgerPlanStepTemplateDescriptor step =
          steps.stream()
              .filter(stepTemplate -> stepTemplate.kind().commitsPosting())
              .reduce(
                  (first, second) -> {
                    throw new IllegalStateException(
                        "Expected exactly one canonical committed-posting scaffold step with a posting payload.");
                  })
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Expected exactly one canonical committed-posting scaffold step with a posting payload."));
      Objects.requireNonNull(
          step.posting(),
          "Canonical committed-posting scaffold step must publish a posting template.");
      return step;
    }

    /** Returns the canonical posting template nested under the published scaffold posting step. */
    public ContractTemplates.PostingRequestTemplateDescriptor canonicalPostingTemplate() {
      return Objects.requireNonNull(
          canonicalPostingScaffoldStep().posting(),
          "Canonical committed-posting scaffold step must publish a posting template.");
    }
  }

  /** Canonical ledger-plan step template descriptor. */
  public record LedgerPlanStepTemplateDescriptor(
      String stepId,
      LedgerStepKind kind,
      ContractPlanTemplates.@Nullable EnsureBookTemplateDescriptor ensureBook,
      ContractTemplates.@Nullable PostingRequestTemplateDescriptor posting,
      ContractTemplates.@Nullable DeclareAccountTemplateDescriptor declareAccount,
      ContractPlanTemplates.@Nullable LedgerPlanQueryTemplateDescriptor query,
      ContractPlanTemplates.@Nullable LedgerAssertionTemplateDescriptor assertion,
      @Nullable String postingId)
      implements TemplateDescriptorType {
    /** Validates one ledger-plan step template descriptor payload. */
    public LedgerPlanStepTemplateDescriptor(
        String stepId,
        LedgerStepKind kind,
        ContractPlanTemplates.@Nullable EnsureBookTemplateDescriptor ensureBook,
        ContractTemplates.@Nullable PostingRequestTemplateDescriptor posting,
        ContractTemplates.@Nullable DeclareAccountTemplateDescriptor declareAccount,
        ContractPlanTemplates.@Nullable LedgerPlanQueryTemplateDescriptor query,
        ContractPlanTemplates.@Nullable LedgerAssertionTemplateDescriptor assertion,
        @Nullable String postingId) {
      this.stepId = ContractDescriptorValidation.requireText(stepId, "stepId");
      this.kind = ContractDescriptorValidation.requireValue(kind, "kind");
      this.ensureBook = ContractDescriptorValidation.requireOptionalValue(ensureBook, "ensureBook");
      this.posting = ContractDescriptorValidation.requireOptionalValue(posting, "posting");
      this.declareAccount =
          ContractDescriptorValidation.requireOptionalValue(declareAccount, "declareAccount");
      this.query = ContractDescriptorValidation.requireOptionalValue(query, "query");
      this.assertion = ContractDescriptorValidation.requireOptionalValue(assertion, "assertion");
      this.postingId = ContractDescriptorValidation.requireOptionalText(postingId, "postingId");
      ContractTemplateShapeValidator.validateStepShape(
          this.kind,
          this.ensureBook,
          this.posting,
          this.declareAccount,
          this.query,
          this.assertion,
          this.postingId);
    }
  }

  /** Canonical ensure-book template nested inside a ledger plan. */
  public record EnsureBookTemplateDescriptor(
      String entityName,
      String bookTemplateId,
      String accountingBasis,
      @Nullable String inventoryCosting,
      String functionalCurrency,
      String fiscalYearStart)
      implements TemplateDescriptorType {
    /** Validates one ensure-book template descriptor payload. */
    public EnsureBookTemplateDescriptor {
      entityName = ContractDescriptorValidation.requireText(entityName, "entityName");
      new BookEntityName(entityName);
      bookTemplateId = ContractDescriptorValidation.requireText(bookTemplateId, "bookTemplateId");
      BookTemplateId.fromWireValue(bookTemplateId);
      accountingBasis =
          ContractDescriptorValidation.requireText(accountingBasis, "accountingBasis");
      AccountingBasis.fromWireValue(accountingBasis);
      inventoryCosting =
          ContractDescriptorValidation.requireOptionalText(inventoryCosting, "inventoryCosting");
      if (inventoryCosting != null) {
        InventoryCostingDoctrine.fromWireValue(inventoryCosting);
      }
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
          && (limit < ProtocolInteractionLimits.PAGE_LIMIT_MIN
              || limit > ProtocolInteractionLimits.PAGE_LIMIT_MAX)) {
        throw new IllegalArgumentException(
            "limit must be between "
                + ProtocolInteractionLimits.PAGE_LIMIT_MIN
                + " and "
                + ProtocolInteractionLimits.PAGE_LIMIT_MAX
                + ".");
      }
      cursor = ContractDescriptorValidation.requireOptionalText(cursor, "cursor");
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
