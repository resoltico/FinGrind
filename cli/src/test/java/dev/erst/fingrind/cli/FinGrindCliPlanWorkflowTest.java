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
  void run_executesNonOpeningPlanAgainstExistingBookThroughDefaultSqliteWorkflow()
      throws IOException {
    Path planFile = writeNamedRequest("two-declaration-plan.json", twoAccountPlanJson());
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
    assertEquals(0, exitCode, () -> outputStream.toString(StandardCharsets.UTF_8));
    assertJsonContains(outputStream, "\"status\":\"succeeded\"");
    ByteArrayOutputStream verificationOutput = new ByteArrayOutputStream();
    FinGrindCli verifyCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(verificationOutput),
            fixedClock());
    assertEquals(
        0,
        verifyCli.run(
            new String[] {
              "verify-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--output",
              "json"
            }));
    assertEquals(
        "1",
        new ObjectMapper()
            .readTree(verificationOutput.toByteArray())
            .path("payload")
            .path("headOrder")
            .stringValue());
  }

  @Test
  void run_rejectsMutatingPlanWithoutAnAttestationCredential() throws IOException {
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
                planFile.toString(),
                "--output",
                "json"
            });
    assertEquals(6, exitCode, () -> outputStream.toString(StandardCharsets.UTF_8));
    assertJsonContains(outputStream, "\"status\":\"error\"");
    assertJsonContains(outputStream, "\"code\":\"invalid-attestation-credential\"");
  }

  @Test
  void run_rejectsLegacyPlanGenesisBeforeOpeningABook() throws IOException {
    Path planFile = writeNamedRequest("legacy-genesis-plan.json", openOnlyPlanJson());
    Path bookFilePath =
        tempDirectory.resolve("plans").resolve("legacy").resolve("rejected-genesis.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
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
                planFile.toString(),
                "--output",
                "json"
            });
    assertEquals(1, exitCode, () -> outputStream.toString(StandardCharsets.UTF_8));
    assertJsonContains(outputStream, "ensure-book");
    assertFalse(Files.exists(bookFilePath));
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
            attestedArguments(
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              cashRequest.toString()
            )));
    assertEquals(
        0,
        declareCli.run(
            attestedArguments(
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              revenueRequest.toString()
            )));
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
                planFile.toString(),
                "--output",
                "json"
            });
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

  private static String twoAccountPlanJson() {
    return """
        {
          "planId": "two-account-plan",
          "steps": [
            {
              "stepId": "declare-cash",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "accountType": "ASSET",
                "accountNodeKind": "POSTABLE",
                "financialPositionLineClassification": "CURRENT_ASSET",
                "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
              }
            },
            {
              "stepId": "declare-revenue",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "2000",
                "accountName": "Revenue",
                "accountType": "REVENUE",
                "accountNodeKind": "POSTABLE",
                "profitAndLossLineClassification": "OPERATING_REVENUE"
              }
            }
          ]
        }
        """;
  }

}
