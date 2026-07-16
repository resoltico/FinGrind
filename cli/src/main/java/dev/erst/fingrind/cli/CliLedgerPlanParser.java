package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalInt;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.optionalObject;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredArray;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredObject;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolBookRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanRequestFieldSets;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses canonical ledger-plan request documents from the CLI JSON surface. */
final class CliLedgerPlanParser {
  private CliLedgerPlanParser() {}

  static LedgerPlan readLedgerPlan(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolLedgerPlanRequestFieldSets.ledgerPlanFields());
    return new LedgerPlan(
        new LedgerPlanId(requiredText(rootNode, ProtocolLedgerPlanFields.Plan.PLAN_ID)),
        readLedgerSteps(requiredArray(rootNode, ProtocolLedgerPlanFields.Plan.STEPS)));
  }

  private static List<LedgerStep> readLedgerSteps(JsonNode stepsNode) {
    List<LedgerStep> steps = new ArrayList<>();
    int index = 0;
    for (JsonNode stepNode : stepsNode) {
      steps.add(readLedgerStep(requireObjectNode(stepNode, "steps[%d]".formatted(index))));
      index++;
    }
    return steps;
  }

  private static LedgerStep readLedgerStep(ObjectNode stepNode) {
    LedgerStepId stepId =
        new LedgerStepId(requiredText(stepNode, ProtocolLedgerPlanFields.Step.STEP_ID));
    LedgerStepKind kind =
        parseWireValue(
            requiredText(stepNode, ProtocolLedgerPlanFields.Step.KIND),
            ProtocolLedgerPlanFields.Step.KIND,
            LedgerStepKind.wireValues(),
            LedgerStepKind::fromWireValue);
    rejectUnexpectedStepFields(stepNode, kind);
    if (kind.commitsPosting()) {
      return CliLedgerPlanPostingStepParser.readCommittedStep(stepId, kind, stepNode);
    }
    if (kind == LedgerStepKind.PREFLIGHT_ENTRY) {
      return CliLedgerPlanPostingStepParser.readPreflightStep(stepId, stepNode);
    }
    if (kind == LedgerStepKind.ASSERT) {
      return new LedgerStep.Assert(
          stepId,
          readLedgerAssertion(requiredObject(stepNode, ProtocolLedgerPlanFields.Step.ASSERTION)));
    }
    if (isAdministrativeStepKind(kind)) {
      return readAdministrativeStep(stepId, kind, stepNode);
    }
    return readQueryStep(stepId, kind, stepNode);
  }

  private static boolean isAdministrativeStepKind(LedgerStepKind kind) {
    return kind == LedgerStepKind.ENSURE_BOOK
        || kind == LedgerStepKind.DECLARE_ACCOUNT
        || kind == LedgerStepKind.DECLARE_TAX_REGISTRATION;
  }

  private static LedgerStep readAdministrativeStep(
      LedgerStepId stepId, LedgerStepKind kind, ObjectNode stepNode) {
    if (kind == LedgerStepKind.ENSURE_BOOK) {
      return new LedgerStep.EnsureBook(
          stepId,
          CliLedgerPlanEnsureBookParser.read(
              requiredObject(stepNode, ProtocolLedgerPlanFields.Step.ENSURE_BOOK)));
    }
    if (kind == LedgerStepKind.DECLARE_TAX_REGISTRATION) {
      return new LedgerStep.DeclareTaxRegistration(
          stepId,
          CliPostingRequestParser.readDeclareTaxRegistrationCommand(
              requiredObject(stepNode, ProtocolLedgerPlanFields.Step.DECLARE_TAX_REGISTRATION)));
    }
    return new LedgerStep.DeclareAccount(
        stepId,
        CliPostingRequestParser.readDeclareAccountCommand(
            requiredObject(stepNode, ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT)));
  }

  private static LedgerStep readQueryStep(
      LedgerStepId stepId, LedgerStepKind kind, ObjectNode stepNode) {
    if (kind == LedgerStepKind.INSPECT_BOOK) {
      return new LedgerStep.InspectBook(stepId);
    }
    if (kind == LedgerStepKind.LIST_ACCOUNTS) {
      return new LedgerStep.ListAccounts(
          stepId,
          readListAccountsQuery(optionalObject(stepNode, ProtocolLedgerPlanFields.Step.QUERY)));
    }
    if (kind == LedgerStepKind.GET_POSTING) {
      return new LedgerStep.GetPosting(
          stepId, new PostingId(requiredText(stepNode, ProtocolLedgerPlanFields.Step.POSTING_ID)));
    }
    if (kind == LedgerStepKind.LIST_POSTINGS) {
      return new LedgerStep.ListPostings(
          stepId,
          readListPostingsQuery(optionalObject(stepNode, ProtocolLedgerPlanFields.Step.QUERY)));
    }
    return new LedgerStep.AccountBalance(
        stepId,
        readAccountBalanceQuery(requiredObject(stepNode, ProtocolLedgerPlanFields.Step.QUERY)));
  }

  private static void rejectUnexpectedStepFields(ObjectNode stepNode, LedgerStepKind kind) {
    List<String> unexpectedFields =
        CliJsonStructureAccess.unexpectedFields(
            stepNode, null, ProtocolLedgerPlanRequestFieldSets.ledgerStepFields());
    if (unexpectedFields.isEmpty()) {
      return;
    }
    rejectFlattenedNestedStepPayload(
        stepNode,
        kind,
        unexpectedFields,
        ProtocolLedgerPlanFields.Step.ENSURE_BOOK,
        ProtocolBookRequestFieldSets.openBookFields(),
        LedgerStepKind.ENSURE_BOOK);
    rejectFlattenedNestedStepPayload(
        stepNode,
        kind,
        unexpectedFields,
        ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT,
        ProtocolBookRequestFieldSets.declareAccountFields(),
        LedgerStepKind.DECLARE_ACCOUNT);
    rejectFlattenedNestedStepPayload(
        stepNode,
        kind,
        unexpectedFields,
        ProtocolLedgerPlanFields.Step.DECLARE_TAX_REGISTRATION,
        ProtocolBookRequestFieldSets.declareTaxRegistrationFields(),
        LedgerStepKind.DECLARE_TAX_REGISTRATION);
    CliLedgerPlanPostingStepParser.rejectFlattenedPostingPayload(stepNode, kind, unexpectedFields);
    rejectFlattenedNestedStepPayload(
        stepNode,
        kind,
        unexpectedFields,
        ProtocolLedgerPlanFields.Step.QUERY,
        ProtocolLedgerPlanRequestFieldSets.ledgerQueryFields(),
        LedgerStepKind.LIST_ACCOUNTS,
        LedgerStepKind.LIST_POSTINGS,
        LedgerStepKind.ACCOUNT_BALANCE);
    rejectFlattenedNestedStepPayload(
        stepNode,
        kind,
        unexpectedFields,
        ProtocolLedgerPlanFields.Step.ASSERTION,
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(),
        LedgerStepKind.ASSERT);
    throw CliJsonStructureAccess.unexpectedFieldsFailure(unexpectedFields);
  }

  private static void rejectFlattenedNestedStepPayload(
      ObjectNode stepNode,
      LedgerStepKind actualKind,
      List<String> unexpectedFields,
      String nestedFieldName,
      java.util.Set<String> nestedAcceptedFields,
      LedgerStepKind... matchingKinds) {
    if (stepNode.has(nestedFieldName)
        || java.util.Arrays.stream(matchingKinds).noneMatch(actualKind::equals)) {
      return;
    }
    List<String> flattenedFields =
        unexpectedFields.stream().filter(nestedAcceptedFields::contains).toList();
    if (flattenedFields.isEmpty()) {
      return;
    }
    String flattenedFieldLabel =
        flattenedFields.size() == 1
            ? "Field " + flattenedFields.getFirst()
            : "Fields " + String.join(", ", flattenedFields);
    throw new IllegalArgumentException(
        flattenedFieldLabel
            + " must be nested under "
            + nestedFieldName
            + " for "
            + actualKind.wireValue()
            + " ledger plan steps.");
  }

  private static LedgerAssertion readLedgerAssertion(ObjectNode assertionNode) {
    LedgerAssertionKind kind =
        parseWireValue(
            requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.KIND),
            "assertion." + ProtocolLedgerPlanFields.Assertion.KIND,
            LedgerAssertionKind.wireValues(),
            LedgerAssertionKind::fromWireValue);
    rejectUnexpectedFields(
        assertionNode, "assertion", ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(kind));
    return switch (kind) {
      case ACCOUNT_DECLARED ->
          new LedgerAssertion.AccountDeclared(
              new AccountCode(
                  requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE)));
      case ACCOUNT_ACTIVE ->
          new LedgerAssertion.AccountActive(
              new AccountCode(
                  requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE)));
      case POSTING_EXISTS ->
          new LedgerAssertion.PostingExists(
              new PostingId(
                  requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.POSTING_ID)));
      case ACCOUNT_BALANCE_EQUALS ->
          new LedgerAssertion.AccountBalanceEquals(
              new AccountCode(
                  requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE)),
              optionalCanonicalLocalDate(
                  assertionNode,
                  ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM,
                  "assertion." + ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM),
              optionalCanonicalLocalDate(
                  assertionNode,
                  ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO,
                  "assertion." + ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO),
              CliJsonMoneyParser.requiredMoney(
                  assertionNode, ProtocolLedgerPlanFields.Assertion.NET_AMOUNT),
              parseWireValue(
                  requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE),
                  ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE,
                  BalanceSide.wireValues(),
                  BalanceSide::fromWireValue));
    };
  }

  private static ListAccountsQuery readListAccountsQuery(Optional<ObjectNode> queryNode) {
    if (queryNode.isEmpty()) {
      return new ListAccountsQuery(ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT, Optional.empty());
    }
    ObjectNode queryObject = queryNode.orElseThrow();
    rejectUnexpectedFields(
        queryObject, "query", ProtocolLedgerPlanRequestFieldSets.listAccountsQueryFields());
    return new ListAccountsQuery(
        optionalInt(queryObject, ProtocolLedgerPlanFields.Query.LIMIT)
            .orElse(ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT),
        optionalText(queryObject, ProtocolLedgerPlanFields.Query.CURSOR)
            .map(AccountPageCursor::fromWireValue));
  }

  private static ListPostingsQuery readListPostingsQuery(Optional<ObjectNode> queryNode) {
    if (queryNode.isEmpty()) {
      return new ListPostingsQuery(
          Optional.empty(),
          null,
          null,
          ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
          Optional.empty());
    }
    ObjectNode queryObject = queryNode.orElseThrow();
    rejectUnexpectedFields(
        queryObject, "query", ProtocolLedgerPlanRequestFieldSets.listPostingsQueryFields());
    return new ListPostingsQuery(
        optionalText(queryObject, ProtocolLedgerPlanFields.Query.ACCOUNT_CODE)
            .map(AccountCode::new),
        optionalCanonicalLocalDate(
            queryObject,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
            "query." + ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM),
        optionalCanonicalLocalDate(
            queryObject,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
            "query." + ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO),
        optionalInt(queryObject, ProtocolLedgerPlanFields.Query.LIMIT)
            .orElse(ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT),
        optionalText(queryObject, ProtocolLedgerPlanFields.Query.CURSOR)
            .map(PostingPageCursor::fromWireValue));
  }

  private static AccountBalanceQuery readAccountBalanceQuery(ObjectNode query) {
    rejectUnexpectedFields(
        query, "query", ProtocolLedgerPlanRequestFieldSets.accountBalanceQueryFields());
    return new AccountBalanceQuery(
        new AccountCode(requiredText(query, ProtocolLedgerPlanFields.Query.ACCOUNT_CODE)),
        optionalCanonicalLocalDate(
            query,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
            "query." + ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM),
        optionalCanonicalLocalDate(
            query,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
            "query." + ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO),
        PostingCoverage.ALL_POSTING_KINDS);
  }

  private static @Nullable LocalDate optionalCanonicalLocalDate(
      ObjectNode node, String fieldName, String qualifiedFieldName) {
    return optionalText(node, fieldName)
        .map(value -> CanonicalTemporalText.parseLocalDate(value, qualifiedFieldName))
        .orElse(null);
  }
}
