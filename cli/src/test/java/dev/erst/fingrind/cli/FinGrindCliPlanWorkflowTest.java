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
            jsonArguments(
                "execute-plan",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--result-detail",
                "full",
                "--request-file",
                planFile.toString()));
    assertEquals(0, exitCode);
    assertJsonContains(outputStream, "\"status\":\"succeeded\"");
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
            jsonArguments(
                "execute-plan",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--result-detail",
                "full",
                "--request-file",
                planFile.toString()));
    assertEquals(0, exitCode);
    assertJsonContains(outputStream, "\"status\":\"succeeded\"");
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
            jsonArguments(
                "execute-plan",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--result-detail",
                "full",
                "--request-file",
                planFile.toString()));
    assertEquals(2, exitCode);
    assertJsonContains(outputStream, "\"status\":\"rejected\"");
    assertJsonContains(outputStream, "\"failureCode\":\"administration-book-not-initialized\"");
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
            jsonArguments(
                "execute-plan",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--result-detail",
                "full",
                "--request-file",
                planFile.toString()));
    assertEquals(3, exitCode);
    JsonNode planResult = new ObjectMapper().readTree(planOutputStream.toByteArray());
    assertEquals("error", planResult.path("status").stringValue());
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
            jsonArguments(
                "inspect-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString())));
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
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));
    assertEquals(2, exitCode);
    assertJsonContains(outputStream, "\"code\":\"posting-book-not-initialized\"");
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
            jsonArguments(
                "execute-plan",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--result-detail",
                "full",
                "--request-file",
                planFile.toString()));
    assertEquals(0, exitCode);
    JsonNode data =
        new ObjectMapper()
            .readTree(outputStream.toByteArray())
            .path("payload")
            .path("journal")
            .path("steps")
            .get(0)
            .path("data");
    assertEquals(1, data.path("count").asInt());
    assertEquals(1, data.path("pageLimit").asInt());
    assertTrue(data.path("nextCursor").stringValue().length() > 4);
    assertTrue(data.path("hasMore").asBoolean());
    assertEquals("1000", data.path("accounts").get(0).path("accountCode").stringValue());
    assertEquals("Cash", data.path("accounts").get(0).path("accountName").stringValue());
    assertEquals("ASSET", data.path("accounts").get(0).path("accountType").stringValue());
    assertEquals("ORDINARY", data.path("accounts").get(0).path("accountRole").stringValue());
    assertEquals("DEBIT", data.path("accounts").get(0).path("normalBalance").stringValue());
  }

  @Test
  void run_rejectsPlaceholderPlanScaffoldBeforeExecution() throws IOException {
    Path planBookFile = tempDirectory.resolve("plans").resolve("placeholder-plan.sqlite");
    Path bookKeyFilePath = writeBookKey(planBookFile);
    ByteArrayOutputStream templateOutput = new ByteArrayOutputStream();
    FinGrindCli templateCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(templateOutput), fixedClock());
    assertEquals(0, templateCli.run(new String[] {"print-plan-template"}));
    Path planFile =
        writeNamedRequest("placeholder-plan.json", templateOutput.toString(StandardCharsets.UTF_8));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli executeCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        executeCli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              planBookFile.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              planFile.toString()
            });

    assertEquals(1, exitCode);
    assertTrue(
        outputStream
            .toString(java.nio.charset.StandardCharsets.UTF_8)
            .contains("Scaffold placeholder must be replaced before submission"));
  }

  private static String openThenFailAssertionPlanJson() {
    return """
            {
              "planId": "plan-assertion-failure",
              "steps": [
                {
                  "stepId": "open",
                  "kind": "ensure-book",
                  "ensureBook": {
                    "entityName": "Acme Studio",
                    "functionalCurrency": "EUR",
                    "fiscalYearStart": "01-01"
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
                    "accountNodeKind": "POSTABLE",
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
