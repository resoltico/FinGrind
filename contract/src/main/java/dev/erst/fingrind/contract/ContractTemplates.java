package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
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
          ProvenanceTemplateDescriptor,
          ReversalTemplateDescriptor,
          LedgerPlanTemplateDescriptor,
          LedgerPlanStepTemplateDescriptor,
          LedgerPlanQueryTemplateDescriptor,
          DeclareAccountTemplateDescriptor,
          LedgerAssertionTemplateDescriptor {}

  /** Canonical request-template document for print-request-template. */
  public record PostingRequestTemplateDescriptor(
      String effectiveDate,
      List<JournalLineTemplateDescriptor> lines,
      ProvenanceTemplateDescriptor provenance,
      @Nullable ReversalTemplateDescriptor reversal)
      implements TemplateDescriptorType {
    /** Validates one posting-request template descriptor payload. */
    public PostingRequestTemplateDescriptor {
      effectiveDate = ContractDescriptorValidation.requireText(effectiveDate, "effectiveDate");
      lines = ContractDescriptorValidation.copyList(lines, "lines");
      if (lines.size() < 2) {
        throw new IllegalArgumentException("lines must contain at least two journal lines.");
      }
      provenance = ContractDescriptorValidation.requireValue(provenance, "provenance");
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
          kind, posting, declareAccount, query, assertion, postingId);
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
      String accountCode, String accountName, NormalBalance normalBalance)
      implements TemplateDescriptorType {
    /** Validates one declare-account template descriptor payload. */
    public DeclareAccountTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      new AccountCode(accountCode);
      accountName = ContractDescriptorValidation.requireText(accountName, "accountName");
      normalBalance = ContractDescriptorValidation.requireValue(normalBalance, "normalBalance");
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
