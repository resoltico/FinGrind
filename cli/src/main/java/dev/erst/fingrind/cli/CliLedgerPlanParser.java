package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonRequestSupport.optionalInt;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.optionalObject;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.optionalText;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.parseAmount;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.requiredArray;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.requiredObject;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.requiredText;

import dev.erst.fingrind.contract.*;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import dev.erst.fingrind.core.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses canonical ledger-plan request documents from the CLI JSON surface. */
final class CliLedgerPlanParser {
  private CliLedgerPlanParser() {}

  static LedgerPlan readLedgerPlan(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSupport.LEDGER_PLAN_FIELDS);
    return new LedgerPlan(
        requiredText(rootNode, ProtocolLedgerPlanFields.Plan.PLAN_ID),
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
    rejectUnexpectedFields(stepNode, null, CliJsonRequestSupport.LEDGER_STEP_FIELDS);
    String stepId = requiredText(stepNode, ProtocolLedgerPlanFields.Step.STEP_ID);
    LedgerStepKind kind =
        parseWireValue(
            requiredText(stepNode, ProtocolLedgerPlanFields.Step.KIND),
            ProtocolLedgerPlanFields.Step.KIND,
            LedgerStepKind.wireValues(),
            LedgerStepKind::fromWireValue);
    return switch (kind) {
      case OPEN_BOOK -> new LedgerStep.OpenBook(stepId);
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

  private static LedgerAssertion readLedgerAssertion(ObjectNode assertionNode) {
    rejectUnexpectedFields(
        assertionNode, "assertion", CliJsonRequestSupport.LEDGER_ASSERTION_FIELDS);
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
              optionalText(assertionNode, ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM)
                  .map(LocalDate::parse),
              optionalText(assertionNode, ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO)
                  .map(LocalDate::parse),
              new Money(
                  new CurrencyCode(
                      requiredText(
                          assertionNode, ProtocolLedgerPlanFields.Assertion.CURRENCY_CODE)),
                  parseAmount(
                      requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.NET_AMOUNT))),
              parseWireValue(
                  requiredText(assertionNode, ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE),
                  ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE,
                  NormalBalance.wireValues(),
                  NormalBalance::fromWireValue));
    };
  }

  private static ListAccountsQuery readListAccountsQuery(Optional<ObjectNode> queryNode) {
    if (queryNode.isEmpty()) {
      return new ListAccountsQuery(
          ProtocolLimits.DEFAULT_PAGE_LIMIT, ProtocolLimits.DEFAULT_PAGE_OFFSET);
    }
    ObjectNode query = queryNode.orElseThrow();
    rejectUnexpectedFields(query, "query", CliJsonRequestSupport.LEDGER_QUERY_FIELDS);
    return new ListAccountsQuery(
        optionalInt(query, ProtocolLedgerPlanFields.Query.LIMIT)
            .orElse(ProtocolLimits.DEFAULT_PAGE_LIMIT),
        optionalInt(query, ProtocolLedgerPlanFields.Query.OFFSET)
            .orElse(ProtocolLimits.DEFAULT_PAGE_OFFSET));
  }

  private static ListPostingsQuery readListPostingsQuery(Optional<ObjectNode> queryNode) {
    if (queryNode.isEmpty()) {
      return new ListPostingsQuery(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          ProtocolLimits.DEFAULT_PAGE_LIMIT,
          ProtocolLimits.DEFAULT_PAGE_OFFSET);
    }
    ObjectNode query = queryNode.orElseThrow();
    rejectUnexpectedFields(query, "query", CliJsonRequestSupport.LEDGER_QUERY_FIELDS);
    return new ListPostingsQuery(
        optionalText(query, ProtocolLedgerPlanFields.Query.ACCOUNT_CODE).map(AccountCode::new),
        optionalText(query, ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM)
            .map(LocalDate::parse),
        optionalText(query, ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO).map(LocalDate::parse),
        optionalInt(query, ProtocolLedgerPlanFields.Query.LIMIT)
            .orElse(ProtocolLimits.DEFAULT_PAGE_LIMIT),
        optionalInt(query, ProtocolLedgerPlanFields.Query.OFFSET)
            .orElse(ProtocolLimits.DEFAULT_PAGE_OFFSET));
  }

  private static AccountBalanceQuery readAccountBalanceQuery(ObjectNode query) {
    rejectUnexpectedFields(query, "query", CliJsonRequestSupport.LEDGER_QUERY_FIELDS);
    return new AccountBalanceQuery(
        new AccountCode(requiredText(query, ProtocolLedgerPlanFields.Query.ACCOUNT_CODE)),
        optionalText(query, ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM)
            .map(LocalDate::parse),
        optionalText(query, ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO)
            .map(LocalDate::parse));
  }
}
