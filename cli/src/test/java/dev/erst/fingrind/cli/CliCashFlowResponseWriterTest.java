package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Focused response-writer coverage for the cash receipts/payments statement. */
class CliCashFlowResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeCashFlowStatementResult_supportsJsonTextAndCsvModes() throws IOException {
    CashFlowStatementResult.Reported reported =
        new CashFlowStatementResult.Reported(CliFixtureSupport.sampleCashFlowStatementReport());

    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    reportWriter(jsonOutput).writeCashFlowStatementResult(reported, OutputMode.JSON, null);
    JsonNode json = readJson(jsonOutput);
    assertEquals("ok", json.path("status").stringValue());
    assertEquals("2026-04-01", json.path("payload").path("effectiveDateFrom").stringValue());
    assertEquals(
        "OPERATING",
        json.path("payload").path("sections").get(0).path("sectionKind").stringValue());

    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    reportWriter(textOutput).writeCashFlowStatementResult(reported, OutputMode.TEXT, null);
    String renderedText = textOutput.toString(StandardCharsets.UTF_8);
    assertTrue(renderedText.contains("Cash Receipts And Payments"));
    assertTrue(renderedText.contains("Comparative Cash Receipts And Payments"));
    assertTrue(renderedText.contains("Operating"));

    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    reportWriter(csvOutput).writeCashFlowStatementResult(reported, OutputMode.CSV, null);
    String renderedCsv = csvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(renderedCsv.startsWith("exportFamily,rowId,parentRowId,relationKind,reportBasis"));
    assertTrue(renderedCsv.contains("cash-flow-statement-row:current:OPERATING:2000"));
  }

  @Test
  void writeCashFlowStatementRejection_usesQueryRejectionEnvelope() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    reportWriter(output)
        .writeCashFlowStatementResult(
            new CashFlowStatementResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            OutputMode.JSON,
            null);

    JsonNode json = readJson(output);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("query-book-not-initialized", json.path("code").stringValue());
  }
}
