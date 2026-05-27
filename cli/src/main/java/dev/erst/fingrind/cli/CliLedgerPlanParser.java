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
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolBookRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolOpenBookFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.InteractionLimits;
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
    return switch (kind) {
      case OPEN_BOOK ->
          new LedgerStep.OpenBook(
              stepId,
              readOpenBookCommand(
                  requiredObject(stepNode, ProtocolLedgerPlanFields.Step.OPEN_BOOK)));
      case DECLARE_ACCOUNT ->
          new LedgerStep.DeclareAccount(
              stepId,
              CliPostingRequestParser.readDeclareAccountCommand(
                  requiredObject(stepNode, ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT)));
      case PREFLIGHT_ENTRY ->
          new LedgerStep.PreflightEntry(
              stepId,
              CliPostingRequestParser.readPostEntryCommand(
                  requiredObject(stepNode, ProtocolLedgerPlanFields.Step.POSTING)));
      case POST_ENTRY ->
          new LedgerStep.PostEntry(
              stepId,
              CliPostingRequestParser.readPostEntryCommand(
                  requiredObject(stepNode, ProtocolLedgerPlanFields.Step.POSTING)));
      case INSPECT_BOOK -> new LedgerStep.InspectBook(stepId);
      case LIST_ACCOUNTS ->
          new LedgerStep.ListAccounts(
              stepId,
              readListAccountsQuery(optionalObject(stepNode, ProtocolLedgerPlanFields.Step.QUERY)));
      case GET_POSTING ->
          new LedgerStep.GetPosting(
              stepId,
              new PostingId(requiredText(stepNode, ProtocolLedgerPlanFields.Step.POSTING_ID)));
      case LIST_POSTINGS ->
          new LedgerStep.ListPostings(
              stepId,
              readListPostingsQuery(optionalObject(stepNode, ProtocolLedgerPlanFields.Step.QUERY)));
      case ACCOUNT_BALANCE ->
          new LedgerStep.AccountBalance(
              stepId,
              readAccountBalanceQuery(
                  requiredObject(stepNode, ProtocolLedgerPlanFields.Step.QUERY)));
      case ASSERT ->
          new LedgerStep.Assert(
              stepId,
              readLedgerAssertion(
                  requiredObject(stepNode, ProtocolLedgerPlanFields.Step.ASSERTION)));
    };
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
        ProtocolLedgerPlanFields.Step.OPEN_BOOK,
        ProtocolBookRequestFieldSets.openBookFields(),
        LedgerStepKind.OPEN_BOOK);
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
        ProtocolLedgerPlanFields.Step.POSTING,
        ProtocolPostingRequestFieldSets.postEntryTopLevelFields(),
        LedgerStepKind.PREFLIGHT_ENTRY,
        LedgerStepKind.POST_ENTRY);
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

  private static OpenBookCommand readOpenBookCommand(ObjectNode openBookNode) {
    rejectUnexpectedFields(openBookNode, "openBook", ProtocolBookRequestFieldSets.openBookFields());
    return new OpenBookCommand(
        new BookIdentity(
            new EntityProfile(
                CliOptionValues.parseBookEntityNameOption(
                    requiredText(openBookNode, ProtocolOpenBookFields.ENTITY_NAME),
                    "openBook." + ProtocolOpenBookFields.ENTITY_NAME),
                requiredBusinessActivityTags(openBookNode)),
            CliOptionValues.parseCurrencyUnitOption(
                requiredText(openBookNode, ProtocolOpenBookFields.FUNCTIONAL_CURRENCY),
                "openBook." + ProtocolOpenBookFields.FUNCTIONAL_CURRENCY),
            CliOptionValues.parseFiscalYearStartOption(
                requiredText(openBookNode, ProtocolOpenBookFields.FISCAL_YEAR_START),
                "openBook." + ProtocolOpenBookFields.FISCAL_YEAR_START)));
  }

  private static List<BusinessActivityTag> requiredBusinessActivityTags(ObjectNode openBookNode) {
    JsonNode rawNode = requiredArray(openBookNode, ProtocolOpenBookFields.BUSINESS_ACTIVITY_TAGS);
    List<BusinessActivityTag> tags = new ArrayList<>();
    int index = 0;
    for (JsonNode tagNode : rawNode) {
      if (!tagNode.isString()) {
        throw new IllegalArgumentException(
            "Field must contain only strings: "
                + ProtocolOpenBookFields.BUSINESS_ACTIVITY_TAGS
                + "["
                + index
                + "]");
      }
      tags.add(
          CliOptionValues.parseBusinessActivityTagOption(
              tagNode.stringValue(),
              ProtocolOpenBookFields.BUSINESS_ACTIVITY_TAGS + "[" + index + "]"));
      index++;
    }
    if (tags.isEmpty()) {
      throw new IllegalArgumentException(
          "Field must contain at least one value: "
              + ProtocolOpenBookFields.BUSINESS_ACTIVITY_TAGS);
    }
    return List.copyOf(tags);
  }

  private static LedgerAssertion readLedgerAssertion(ObjectNode assertionNode) {
    rejectUnexpectedFields(
        assertionNode, "assertion", ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields());
    LedgerAssertionKind kind =
        parseWireValue(
            requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.KIND),
            "assertion." + ProtocolLedgerPlanFields.Assertion.KIND,
            LedgerAssertionKind.wireValues(),
            LedgerAssertionKind::fromWireValue);
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
      return new ListAccountsQuery(InteractionLimits.DEFAULT_PAGE_LIMIT, Optional.empty());
    }
    ObjectNode queryObject = queryNode.orElseThrow();
    rejectUnexpectedFields(
        queryObject, "query", ProtocolLedgerPlanRequestFieldSets.ledgerQueryFields());
    return new ListAccountsQuery(
        optionalInt(queryObject, ProtocolLedgerPlanFields.Query.LIMIT)
            .orElse(InteractionLimits.DEFAULT_PAGE_LIMIT),
        optionalText(queryObject, ProtocolLedgerPlanFields.Query.CURSOR)
            .map(AccountPageCursor::fromWireValue));
  }

  private static ListPostingsQuery readListPostingsQuery(Optional<ObjectNode> queryNode) {
    if (queryNode.isEmpty()) {
      return new ListPostingsQuery(
          Optional.empty(), null, null, InteractionLimits.DEFAULT_PAGE_LIMIT, Optional.empty());
    }
    ObjectNode queryObject = queryNode.orElseThrow();
    rejectUnexpectedFields(
        queryObject, "query", ProtocolLedgerPlanRequestFieldSets.ledgerQueryFields());
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
            .orElse(InteractionLimits.DEFAULT_PAGE_LIMIT),
        optionalText(queryObject, ProtocolLedgerPlanFields.Query.CURSOR)
            .map(PostingPageCursor::fromWireValue));
  }

  private static AccountBalanceQuery readAccountBalanceQuery(ObjectNode query) {
    rejectUnexpectedFields(query, "query", ProtocolLedgerPlanRequestFieldSets.ledgerQueryFields());
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
        optionalText(query, ProtocolLedgerPlanFields.Query.POSTING_COVERAGE)
            .map(PostingCoverage::fromWireValue)
            .orElse(PostingCoverage.ALL_POSTING_KINDS));
  }

  private static @Nullable LocalDate optionalCanonicalLocalDate(
      ObjectNode node, String fieldName, String qualifiedFieldName) {
    return optionalText(node, fieldName)
        .map(value -> CanonicalTemporalText.parseLocalDate(value, qualifiedFieldName))
        .orElse(null);
  }
}
