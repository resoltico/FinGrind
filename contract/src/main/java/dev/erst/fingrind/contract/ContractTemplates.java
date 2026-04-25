package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Request and ledger-plan template descriptor namespace for discovery commands. */
public final class ContractTemplates {
  private static final Map<LedgerStepKind, StepShapeRequirements> STEP_SHAPE_REQUIREMENTS =
      stepShapeRequirements();
  private static final Map<LedgerAssertionKind, AssertionShapeRequirements>
      ASSERTION_SHAPE_REQUIREMENTS = assertionShapeRequirements();

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
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount)
      implements TemplateDescriptorType {
    /** Validates one journal-line template descriptor payload. */
    public JournalLineTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      side = ContractDescriptorValidation.requireValue(side, "side");
      currencyCode = ContractDescriptorValidation.requireText(currencyCode, "currencyCode");
      amount = ContractDescriptorValidation.requireText(amount, "amount");
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
      validateStepShape(kind, posting, declareAccount, query, assertion, postingId);
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
          && (limit < ProtocolLimits.PAGE_LIMIT_MIN || limit > ProtocolLimits.PAGE_LIMIT_MAX)) {
        throw new IllegalArgumentException(
            "limit must be between "
                + ProtocolLimits.PAGE_LIMIT_MIN
                + " and "
                + ProtocolLimits.PAGE_LIMIT_MAX
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
      @Nullable String currencyCode,
      @Nullable String netAmount,
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
      currencyCode = ContractDescriptorValidation.requireOptionalText(currencyCode, "currencyCode");
      netAmount = ContractDescriptorValidation.requireOptionalText(netAmount, "netAmount");
      postingId = ContractDescriptorValidation.requireOptionalText(postingId, "postingId");
      validateAssertionShape(
          kind,
          accountCode,
          effectiveDateFrom,
          effectiveDateTo,
          currencyCode,
          netAmount,
          balanceSide,
          postingId);
    }
  }

  private static void validateStepShape(
      LedgerStepKind kind,
      @Nullable PostingRequestTemplateDescriptor posting,
      @Nullable DeclareAccountTemplateDescriptor declareAccount,
      @Nullable LedgerPlanQueryTemplateDescriptor query,
      @Nullable LedgerAssertionTemplateDescriptor assertion,
      @Nullable String postingId) {
    StepShapeRequirements requirements =
        Objects.requireNonNull(STEP_SHAPE_REQUIREMENTS.get(kind), kind.toString());
    requireStepShape(kind, posting, declareAccount, query, assertion, postingId, requirements);
    if (requirements.queryAccountCodeRequired()) {
      LedgerPlanQueryTemplateDescriptor accountBalanceQuery =
          Objects.requireNonNull(query, "query");
      if (accountBalanceQuery.accountCode() == null) {
        throw new IllegalArgumentException(
            "query.accountCode is required for account balance template steps.");
      }
    }
  }

  private static void requireStepShape(
      LedgerStepKind kind,
      @Nullable PostingRequestTemplateDescriptor posting,
      @Nullable DeclareAccountTemplateDescriptor declareAccount,
      @Nullable LedgerPlanQueryTemplateDescriptor query,
      @Nullable LedgerAssertionTemplateDescriptor assertion,
      @Nullable String postingId,
      StepShapeRequirements requirements) {
    requirePresence(kind, "posting", posting, requirements.posting());
    requirePresence(kind, "declareAccount", declareAccount, requirements.declareAccount());
    requirePresence(kind, "query", query, requirements.query());
    requirePresence(kind, "assertion", assertion, requirements.assertion());
    requirePresence(kind, "postingId", postingId, requirements.postingId());
  }

  private static void validateAssertionShape(
      LedgerAssertionKind kind,
      @Nullable String accountCode,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      @Nullable String currencyCode,
      @Nullable String netAmount,
      @Nullable BalanceSide balanceSide,
      @Nullable String postingId) {
    AssertionShapeRequirements requirements =
        Objects.requireNonNull(ASSERTION_SHAPE_REQUIREMENTS.get(kind), kind.toString());
    requirePresence(kind, "accountCode", accountCode, requirements.accountCode());
    requirePresence(kind, "effectiveDateFrom", effectiveDateFrom, requirements.effectiveDateFrom());
    requirePresence(kind, "effectiveDateTo", effectiveDateTo, requirements.effectiveDateTo());
    requirePresence(kind, "currencyCode", currencyCode, requirements.currencyCode());
    requirePresence(kind, "netAmount", netAmount, requirements.netAmount());
    requirePresence(kind, "balanceSide", balanceSide, requirements.balanceSide());
    requirePresence(kind, "postingId", postingId, requirements.postingId());
  }

  private static Map<LedgerStepKind, StepShapeRequirements> stepShapeRequirements() {
    return Map.ofEntries(
        Map.entry(
            LedgerStepKind.OPEN_BOOK,
            new StepShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.INSPECT_BOOK,
            new StepShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.DECLARE_ACCOUNT,
            new StepShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.PREFLIGHT_ENTRY,
            new StepShapeRequirements(
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.POST_ENTRY,
            new StepShapeRequirements(
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.LIST_ACCOUNTS,
            new StepShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.OPTIONAL,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.LIST_POSTINGS,
            new StepShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.OPTIONAL,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                false)),
        Map.entry(
            LedgerStepKind.ACCOUNT_BALANCE,
            new StepShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                true)),
        Map.entry(
            LedgerStepKind.GET_POSTING,
            new StepShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.REQUIRED,
                false)),
        Map.entry(
            LedgerStepKind.ASSERT,
            new StepShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN,
                false)));
  }

  private static Map<LedgerAssertionKind, AssertionShapeRequirements> assertionShapeRequirements() {
    return Map.ofEntries(
        Map.entry(
            LedgerAssertionKind.ACCOUNT_DECLARED,
            new AssertionShapeRequirements(
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN)),
        Map.entry(
            LedgerAssertionKind.ACCOUNT_ACTIVE,
            new AssertionShapeRequirements(
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN)),
        Map.entry(
            LedgerAssertionKind.POSTING_EXISTS,
            new AssertionShapeRequirements(
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.REQUIRED)),
        Map.entry(
            LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
            new AssertionShapeRequirements(
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN,
                FieldPresence.FORBIDDEN,
                FieldPresence.REQUIRED,
                FieldPresence.REQUIRED,
                FieldPresence.REQUIRED,
                FieldPresence.FORBIDDEN)));
  }

  private static void requirePresence(
      Enum<?> owner, String fieldName, @Nullable Object value, FieldPresence presence) {
    if (presence == FieldPresence.REQUIRED && value == null) {
      throw new IllegalArgumentException(
          fieldName + " is required for " + owner + " template shapes.");
    }
    if (presence == FieldPresence.FORBIDDEN && value != null) {
      throw new IllegalArgumentException(
          fieldName + " must be absent for " + owner + " template shapes.");
    }
  }

  /** Presence policy for one ledger-plan step template shape. */
  private record StepShapeRequirements(
      FieldPresence posting,
      FieldPresence declareAccount,
      FieldPresence query,
      FieldPresence assertion,
      FieldPresence postingId,
      boolean queryAccountCodeRequired) {}

  /** Presence policy for one ledger-assertion template shape. */
  private record AssertionShapeRequirements(
      FieldPresence accountCode,
      FieldPresence effectiveDateFrom,
      FieldPresence effectiveDateTo,
      FieldPresence currencyCode,
      FieldPresence netAmount,
      FieldPresence balanceSide,
      FieldPresence postingId) {}

  /** Presence classification for one template field inside a shape policy. */
  private enum FieldPresence {
    REQUIRED,
    OPTIONAL,
    FORBIDDEN
  }
}
