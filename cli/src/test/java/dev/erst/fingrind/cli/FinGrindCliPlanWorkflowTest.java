package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliPlanWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void run_executesOpenBookPlanThroughDefaultSqliteWorkflow() throws IOException {
    Path planFile = writeNamedRequest("open-plan.json", openOnlyPlanJson());
    Path bookFilePath = tempDirectory.resolve("plans").resolve("new-book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        cli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--result-detail",
              "full",
              "--request-file",
              planFile.toString()
            });
    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
    assertTrue(Files.exists(bookFilePath));
  }

  @Test
  void run_executesNonOpeningPlanAgainstExistingBookThroughDefaultSqliteWorkflow()
      throws IOException {
    Path planFile = writeNamedRequest("declare-plan.json", validPlanJson());
    Path bookFilePath = tempDirectory.resolve("plans").resolve("existing-book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    FinGrindCli openCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(new ByteArrayOutputStream()),
            fixedClock());
    assertEquals(0, openCli.run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli executeCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        executeCli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--result-detail",
              "full",
              "--request-file",
              planFile.toString()
            });
    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }

  @Test
  void run_rejectsPlanWithoutOpenBookAgainstMissingBookThroughDefaultSqliteWorkflow()
      throws IOException {
    Path planFile = writeNamedRequest("declare-plan.json", validPlanJson());
    Path bookFilePath = tempDirectory.resolve("plans").resolve("missing-book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        cli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--result-detail",
              "full",
              "--request-file",
              planFile.toString()
            });
    assertEquals(2, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"failureCode\":\"administration-book-not-initialized\""));
  }

  @Test
  void run_assertionFailedPlanLeavesMissingBookMissingThroughDefaultSqliteWorkflow()
      throws IOException {
    Path planFile = writeNamedRequest("assertion-plan.json", openThenFailAssertionPlanJson());
    Path bookFilePath =
        tempDirectory.resolve("plans").resolve("rollback").resolve("assertion-failure.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream planOutputStream = new ByteArrayOutputStream();
    FinGrindCli executeCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(planOutputStream), fixedClock());
    int exitCode =
        executeCli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--result-detail",
              "full",
              "--request-file",
              planFile.toString()
            });
    assertEquals(3, exitCode);
    JsonNode planResult = new ObjectMapper().readTree(planOutputStream.toByteArray());
    assertEquals("ok", planResult.path("status").stringValue());
    assertEquals("assertion-failed", planResult.path("payload").path("status").stringValue());
    assertFalse(Files.exists(bookFilePath));
    ByteArrayOutputStream inspectOutputStream = new ByteArrayOutputStream();
    FinGrindCli inspectCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(inspectOutputStream),
            fixedClock());
    assertEquals(
        0,
        inspectCli.run(
            new String[] {
              "inspect-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            }));
    JsonNode inspectionResult = new ObjectMapper().readTree(inspectOutputStream.toByteArray());
    assertEquals("missing", inspectionResult.path("payload").path("state").stringValue());
  }

  @Test
  void run_rejectsPreflightAgainstMissingBookThroughDefaultSqliteWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("live-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });
    assertEquals(2, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"posting-book-not-initialized\""));
    assertFalse(Files.exists(bookFilePath));
  }

  @Test
  void run_emitsStructuredListAccountFactsForPlanQueries() throws IOException {
    Path bookFilePath = tempDirectory.resolve("plans").resolve("query-book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path cashRequest =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path revenueRequest =
        writeNamedRequest("declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path planFile = writeNamedRequest("list-accounts-plan.json", listAccountsPlanJson(1));
    FinGrindCli openCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(new ByteArrayOutputStream()),
            fixedClock());
    assertEquals(0, openCli.run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    FinGrindCli declareCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(new ByteArrayOutputStream()),
            fixedClock());
    assertEquals(
        0,
        declareCli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              cashRequest.toString()
            }));
    assertEquals(
        0,
        declareCli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              revenueRequest.toString()
            }));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli executeCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        executeCli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--result-detail",
              "full",
              "--request-file",
              planFile.toString()
            });
    assertEquals(0, exitCode);
    JsonNode facts =
        new ObjectMapper()
            .readTree(outputStream.toByteArray())
            .path("payload")
            .path("journal")
            .path("steps")
            .get(0)
            .path("facts");
    assertEquals("count", facts.get(0).path("name").stringValue());
    assertEquals(1, facts.get(0).path("value").asInt());
    assertEquals("pageLimit", facts.get(1).path("name").stringValue());
    assertEquals(1, facts.get(1).path("value").asInt());
    assertEquals("nextCursor", facts.get(2).path("name").stringValue());
    assertTrue(facts.get(2).path("value").stringValue().length() > 4);
    assertEquals("hasMore", facts.get(3).path("name").stringValue());
    assertTrue(facts.get(3).path("value").asBoolean());
    assertEquals("account", facts.get(4).path("name").stringValue());
    assertEquals("1000", facts.get(4).path("facts").get(0).path("value").stringValue());
    assertEquals("Cash", facts.get(4).path("facts").get(1).path("value").stringValue());
    assertEquals("ASSET", facts.get(4).path("facts").get(2).path("value").stringValue());
    assertEquals("ORDINARY", facts.get(4).path("facts").get(3).path("value").stringValue());
    assertEquals("DEBIT", facts.get(4).path("facts").get(4).path("value").stringValue());
  }

  private static String openThenFailAssertionPlanJson() {
    return """
            {
              "planId": "plan-assertion-failure",
              "steps": [
                {
                  "stepId": "open",
                  "kind": "open-book",
                  "openBook": {
                    "entityName": "Acme Studio",
                    "entityForm": "COMPANY",
                    "ownerModel": "MULTI_OWNER",
                    "reportingObligationStatus": "INTERNAL_MANAGEMENT_ONLY",
                    "taxRegistrationStatus": "UNSPECIFIED",
                    "businessActivityTags": ["translation-services"],
                    "functionalCurrency": "EUR",
                    "fiscalYearStart": "01-01",
                    "accountingBasis": "ACCRUAL"
                  }
                },
                {
                  "stepId": "declare-cash",
                  "kind": "declare-account",
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "accountType": "ASSET",
                    "accountRole": "ORDINARY",
                    "financialPositionLineClassification": "CURRENT_ASSET"
                  }
                },
                {
                  "stepId": "assert-missing-posting",
                  "kind": "assert",
                  "assertion": {
                    "kind": "assert-posting-exists",
                    "postingId": "posting-missing"
                  }
                }
              ]
            }
            """;
  }
}
