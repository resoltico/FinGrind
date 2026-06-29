package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliQueryWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void run_queryCommandsThroughDefaultSqliteWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("query-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest(
            "query-declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("query-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareCashFile.toString()
                }));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareRevenueFile.toString()
                }));
    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock())
            .run(
                jsonArguments(
                    "record-sale",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString())));
    String postingId =
        new ObjectMapper()
            .readTree(commitOutput.toString(StandardCharsets.UTF_8))
            .path("payload")
            .path("postingId")
            .stringValue();
    ByteArrayOutputStream inspectOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(inspectOutput), fixedClock())
            .run(
                jsonArguments(
                    "inspect-book",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString())));
    var inspectEnvelope = new ObjectMapper().readTree(inspectOutput.toByteArray());
    assertEquals(
        CliPublicPaths.redactedValue(bookFilePath),
        inspectEnvelope.path("payload").path("bookFile").stringValue());
    assertEquals("initialized", inspectEnvelope.path("payload").path("state").stringValue());
    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(getPostingOutput), fixedClock())
            .run(
                jsonArguments(
                    "get-posting",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--posting-id",
                    postingId)));
    assertTrue(getPostingOutput.toString(StandardCharsets.UTF_8).contains(postingId));
    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(listPostingsOutput),
                fixedClock())
            .run(
                jsonArguments(
                    "list-postings",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--limit",
                    "10")));
    assertTrue(listPostingsOutput.toString(StandardCharsets.UTF_8).contains(postingId));
    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(balanceOutput), fixedClock())
            .run(
                jsonArguments(
                    "account-balance",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--account-code",
                    "1000")));
    assertJsonContains(balanceOutput, "\"accountCode\":\"1000\"");
    assertTrue(balanceOutput.toString(StandardCharsets.UTF_8).contains("\"balances\""));
  }

  @Test
  void run_twoLineBankTransferRawJournalThroughEncryptedBookAndReadItBack() throws IOException {
    RawJournalWorkflowContext workflow = openRawJournalWorkflow();
    String postingId = commitRawJournal(workflow, writeRawBankTransferRequest());
    assertPostingReadback(workflow, postingId, "bank-deposit", 2, "operating-bank", "1000");
    assertPostingListingContains(workflow, postingId);
  }

  @Test
  void run_threeLineSplitRawJournalThroughEncryptedBookAndReadItBack() throws IOException {
    RawJournalWorkflowContext workflow = openRawJournalWorkflow();
    String postingId = commitRawJournal(workflow, writeRawSplitRequest());
    assertPostingReadback(workflow, postingId, "cash-receipt", 3, "1000", "5000", "operating-bank");
    assertPostingListingContains(workflow, postingId);
  }

  private RawJournalWorkflowContext openRawJournalWorkflow() throws IOException {
    Path declareCashFile =
        writeNamedRequest("raw-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareBankFile =
        writeNamedRequest(
            "raw-declare-bank.json",
            declareAccountJson("operating-bank", "Operating Bank", "ASSET", "CURRENT_ASSET", null));
    Path declareExpenseFile =
        writeNamedRequest(
            "raw-declare-expense.json",
            declareAccountJson("5000", "Misc Expense", "EXPENSE", null, "OPERATING_EXPENSE"));
    Path bookFilePath = tempDirectory.resolve("raw-journal-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    for (Path declaration :
        java.util.List.of(declareCashFile, declareBankFile, declareExpenseFile)) {
      assertDeclareAccountSucceeds(bookFilePath, bookKeyFilePath, declaration);
    }
    return new RawJournalWorkflowContext(bookFilePath, bookKeyFilePath, new ObjectMapper());
  }

  private void assertDeclareAccountSucceeds(
      Path bookFilePath, Path bookKeyFilePath, Path declarationFile) {
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declarationFile.toString()
                }));
  }

  private Path writeRawBankTransferRequest() throws IOException {
    return writeNamedRequest(
        "raw-bank-transfer-request.json",
        rawJournalRequestJson(
            "2026-04-07",
            "command-transfer",
            "idem-transfer",
            "bank-deposit-1",
            "bank-deposit",
            journalLineJson("operating-bank", "DEBIT", "400"),
            journalLineJson("1000", "CREDIT", "400")));
  }

  private Path writeRawSplitRequest() throws IOException {
    return writeNamedRequest(
        "raw-split-request.json",
        rawJournalRequestJson(
            "2026-04-08",
            "command-split",
            "idem-split",
            "cash-receipt-1",
            "cash-receipt",
            journalLineJson("1000", "DEBIT", "1000"),
            journalLineJson("5000", "DEBIT", "250"),
            journalLineJson("operating-bank", "CREDIT", "1250")));
  }

  private String commitRawJournal(RawJournalWorkflowContext workflow, Path requestFile)
      throws IOException {
    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock())
            .run(
                jsonArguments(
                    "post-entry",
                    "--book-file",
                    workflow.bookFilePath().toString(),
                    "--book-key-file",
                    workflow.bookKeyFilePath().toString(),
                    "--request-file",
                    requestFile.toString()));
    assertEquals(0, exitCode, commitOutput.toString(StandardCharsets.UTF_8));
    return workflow
        .json()
        .readTree(commitOutput.toByteArray())
        .path("payload")
        .path("postingId")
        .stringValue();
  }

  private void assertPostingReadback(
      RawJournalWorkflowContext workflow,
      String postingId,
      String sourceDocumentType,
      int expectedLineCount,
      String... expectedAccountCodes)
      throws IOException {
    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(getPostingOutput), fixedClock())
            .run(
                jsonArguments(
                    "get-posting",
                    "--book-file",
                    workflow.bookFilePath().toString(),
                    "--book-key-file",
                    workflow.bookKeyFilePath().toString(),
                    "--posting-id",
                    postingId)));
    String getPostingJson = getPostingOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(getPostingJson, "\"postingOriginKind\":\"DIRECT_JOURNAL\"");
    assertJsonContains(getPostingJson, "\"sourceDocumentType\":\"" + sourceDocumentType + "\"");
    for (String expectedAccountCode : expectedAccountCodes) {
      assertJsonContains(getPostingJson, "\"accountCode\":\"" + expectedAccountCode + "\"");
    }
    assertEquals(
        expectedLineCount,
        workflow
            .json()
            .readTree(getPostingOutput.toByteArray())
            .path("payload")
            .path("posting")
            .path("lines")
            .size());
  }

  private void assertPostingListingContains(RawJournalWorkflowContext workflow, String postingId) {
    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(listPostingsOutput),
                fixedClock())
            .run(
                jsonArguments(
                    "list-postings",
                    "--book-file",
                    workflow.bookFilePath().toString(),
                    "--book-key-file",
                    workflow.bookKeyFilePath().toString(),
                    "--limit",
                    "10")));
    String listPostingsJson = listPostingsOutput.toString(StandardCharsets.UTF_8);
    assertJsonContains(listPostingsJson, "\"postingOriginKind\":\"DIRECT_JOURNAL\"");
    assertTrue(listPostingsJson.contains(postingId));
  }

  private record RawJournalWorkflowContext(
      Path bookFilePath, Path bookKeyFilePath, ObjectMapper json) {}
}
