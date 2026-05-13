package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliLedgerPlanRequestReaderTest extends CliRequestReaderTestSupport {

  @Test
  void readLedgerPlan_readsEverySupportedStepKindFromStandardInput() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(validLedgerPlanJson().getBytes(StandardCharsets.UTF_8)));

    LedgerPlan plan = requestReader.readLedgerPlan(Path.of("-"));

    assertEquals("plan-1", plan.planId().value());
    assertEquals(13, plan.steps().size());
    assertEquals(LedgerStep.OpenBook.class, plan.steps().get(0).getClass());
    assertEquals(LedgerStep.DeclareAccount.class, plan.steps().get(1).getClass());
    assertEquals(LedgerStep.PreflightEntry.class, plan.steps().get(2).getClass());
    assertEquals(LedgerStep.PostEntry.class, plan.steps().get(3).getClass());
    assertEquals(LedgerStep.InspectBook.class, plan.steps().get(4).getClass());
    assertEquals(LedgerStep.ListAccounts.class, plan.steps().get(5).getClass());
    assertEquals(LedgerStep.GetPosting.class, plan.steps().get(6).getClass());
    assertEquals(LedgerStep.ListPostings.class, plan.steps().get(7).getClass());
    assertEquals(LedgerStep.AccountBalance.class, plan.steps().get(8).getClass());
    assertEquals(LedgerAssertion.AccountDeclared.class, assertionAt(plan, 9).getClass());
    assertEquals(LedgerAssertion.AccountActive.class, assertionAt(plan, 10).getClass());
    assertEquals(LedgerAssertion.PostingExists.class, assertionAt(plan, 11).getClass());
    assertEquals(LedgerAssertion.AccountBalanceEquals.class, assertionAt(plan, 12).getClass());
  }

  @Test
  void readLedgerPlan_defaultsOptionalQueryObjects() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "list-accounts",
                      "kind": "list-accounts"
                    },
                    {
                      "stepId": "list-postings",
                      "kind": "list-postings"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    LedgerPlan plan = requestReader.readLedgerPlan(Path.of("-"));

    assertEquals(50, ((LedgerStep.ListAccounts) plan.steps().get(0)).query().limit());
    assertTrue(((LedgerStep.ListAccounts) plan.steps().get(0)).query().cursor().isEmpty());
    assertTrue(((LedgerStep.ListPostings) plan.steps().get(1)).query().cursor().isEmpty());
  }

  @Test
  void readLedgerPlan_rejectsExecutionPolicyBecauseExecutionSemanticsAreCoreOwned() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "executionPolicy": {
                    "journalLevel": "VERBOSE"
                  },
                  "steps": [
                    {
                      "stepId": "inspect",
                      "kind": "inspect-book"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Unexpected field: executionPolicy", exception.getMessage());
  }

  @Test
  void readLedgerPlan_rethrowsJsonReadFailures() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "planId": "plan-2",
                  "steps": []
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Request JSON must not contain duplicate object keys.", exception.getMessage());
    assertEquals(CliJsonRequestHints.ledgerPlanRequestHint(), exception.failure().hint());
  }

  @Test
  void readLedgerPlan_guidesFlattenedDeclareAccountPayloadBackUnderDeclareAccount() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "step-1",
                      "kind": "declare-account",
                      "accountCode": "1000",
                      "accountName": "Cash",
                      "accountType": "ASSET",
                      "accountRole": "ORDINARY"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals(
        "Fields accountCode, accountName, accountType, accountRole must be nested under declareAccount for declare-account ledger plan steps.",
        exception.getMessage());
  }

  @Test
  void readLedgerPlan_guidesFlattenedPostingPayloadBackUnderPosting() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "step-1",
                      "kind": "post-entry",
                      "effectiveDate": "2026-04-07"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals(
        "Field effectiveDate must be nested under posting for post-entry ledger plan steps.",
        exception.getMessage());
  }

  @Test
  void readLedgerPlan_guidesFlattenedQueryPayloadBackUnderQuery() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "step-1",
                      "kind": "account-balance",
                      "accountCode": "1000"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals(
        "Field accountCode must be nested under query for account-balance ledger plan steps.",
        exception.getMessage());
  }

  @Test
  void readLedgerPlan_guidesFlattenedAssertionPayloadBackUnderAssertion() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "step-1",
                      "kind": "assert",
                      "balanceSide": "DEBIT"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals(
        "Field balanceSide must be nested under assertion for assert ledger plan steps.",
        exception.getMessage());
  }

  @Test
  void readLedgerPlan_rejectsUnexpectedDeclareAccountStepFieldWhenNoNestedPayloadFieldMatches() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "step-1",
                      "kind": "declare-account",
                      "bogus": true
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Unexpected field: bogus", exception.getMessage());
  }

  @Test
  void
      readLedgerPlan_rejectsUnexpectedFlattenedFieldWhenNestedDeclareAccountPayloadAlreadyExists() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "step-1",
                      "kind": "declare-account",
                      "declareAccount": {
                        "accountCode": "1000",
                        "accountName": "Cash",
                        "accountType": "ASSET",
                        "accountRole": "ORDINARY"
                      },
                      "accountCode": "2000"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Unexpected field: accountCode", exception.getMessage());
  }

  @Test
  void readLedgerPlan_rejectsUnexpectedFieldForUnrelatedStepKinds() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "step-1",
                      "kind": "open-book",
                      "accountCode": "1000"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Unexpected field: accountCode", exception.getMessage());
  }

  @Test
  void readLedgerPlan_reportsInvalidDateValues() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLedgerPlanJson()
                    .replace(
                        "\"effectiveDateFrom\": \"2026-04-01\"",
                        "\"effectiveDateFrom\": \"2026-02-30\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Ledger plan contains an invalid date/time value.", exception.getMessage());
  }

  @Test
  void readLedgerPlan_reportsInvalidShapeValues() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLedgerPlanJson()
                    .replace("\"kind\": \"open-book\"", "\"kind\": \"unsupported-step\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .startsWith("Unsupported value for kind: unsupported-step."));
    assertEquals(CliJsonRequestHints.ledgerPlanRequestHint(), exception.failure().hint());
  }

  @Test
  void readLedgerPlan_rejectsWrongOptionalIntegerType() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLedgerPlanJson()
                    .replace("\"limit\": 25", "\"limit\": \"25\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Field must be an integer when present: limit", exception.getMessage());
  }

  @Test
  void readLedgerPlan_rejectsUnsupportedAssertionKind() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLedgerPlanJson()
                    .replace(
                        "\"kind\": \"assert-account-balance\"", "\"kind\": \"assert-sideways\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .startsWith("Unsupported value for assertion.kind: assert-sideways."));
  }

  @Test
  void readLedgerPlan_treatsExplicitNullOptionalQueryFieldsAsMissing() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "list-accounts",
                      "kind": "list-accounts",
                      "query": {
                        "limit": null,
                        "cursor": null
                      }
                    },
                    {
                      "stepId": "list-postings",
                      "kind": "list-postings",
                      "query": null
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    LedgerPlan plan = requestReader.readLedgerPlan(Path.of("-"));

    assertEquals(50, ((LedgerStep.ListAccounts) plan.steps().get(0)).query().limit());
    assertTrue(((LedgerStep.ListAccounts) plan.steps().get(0)).query().cursor().isEmpty());
    assertTrue(((LedgerStep.ListPostings) plan.steps().get(1)).query().cursor().isEmpty());
  }

  @Test
  void optionalInt_treatsMissingAndNullFieldsAsEmpty() throws IOException {
    var rootNode =
        CliJsonFieldAccess.requireRootObject(
            CliJsonObjectMappers.configuredObjectMapper()
                .readTree(
                    """
                    {
                      "limit": 25,
                      "explicitNull": null
                    }
                    """));

    assertEquals(OptionalInt.empty(), CliJsonFieldAccess.optionalInt(rootNode, "missing"));
    assertEquals(OptionalInt.empty(), CliJsonFieldAccess.optionalInt(rootNode, "explicitNull"));
    assertEquals(OptionalInt.of(25), CliJsonFieldAccess.optionalInt(rootNode, "limit"));
  }

  @Test
  void requiredInt_rejectsMissingField() throws IOException {
    var rootNode =
        CliJsonFieldAccess.requireRootObject(
            CliJsonObjectMappers.configuredObjectMapper().readTree("{\"limit\": 25}"));

    assertEquals(25, CliJsonFieldAccess.requiredInt(rootNode, "limit"));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CliJsonFieldAccess.requiredInt(rootNode, "cursor"));
    assertEquals("Missing required field: cursor", exception.getMessage());
  }
}
