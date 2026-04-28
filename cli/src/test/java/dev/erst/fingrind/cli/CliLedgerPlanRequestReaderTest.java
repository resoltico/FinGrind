package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerStep;
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
    assertEquals(CliJsonRequestCodec.ledgerPlanRequestHint(), exception.failure().hint());
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
    assertEquals(CliJsonRequestCodec.ledgerPlanRequestHint(), exception.failure().hint());
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
        CliJsonRequestCodec.requireRootObject(
            CliJsonRequestCodec.configuredObjectMapper()
                .readTree(
                    """
                    {
                      "limit": 25,
                      "explicitNull": null
                    }
                    """));

    assertEquals(OptionalInt.empty(), CliJsonRequestCodec.optionalInt(rootNode, "missing"));
    assertEquals(OptionalInt.empty(), CliJsonRequestCodec.optionalInt(rootNode, "explicitNull"));
    assertEquals(OptionalInt.of(25), CliJsonRequestCodec.optionalInt(rootNode, "limit"));
  }
}
