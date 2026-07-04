package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** End-to-end CLI coverage for declared tax registrations and tax-obligation workflows. */
class FinGrindCliTaxWorkflowTest extends FinGrindCliTestSupport {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void run_taxRegistrationLifecycleCoversEmptyScopePaginationAndMutationOutcomes()
      throws IOException {
    TaxWorkflowContext workflow = openTaxWorkflow("tax-registration-books");

    JsonNode emptyListEnvelope =
        runJson(
            0,
            "list-tax-registrations",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--limit",
            "1");
    assertEquals("ok", emptyListEnvelope.path("status").stringValue());
    assertEquals(1, emptyListEnvelope.path("payload").path("limit").intValue());
    assertEquals(0, emptyListEnvelope.path("payload").path("registrations").size());
    assertTrue(emptyListEnvelope.path("payload").path("nextCursor").isMissingNode());

    String emptyListText =
        runText(
            0,
            "list-tax-registrations",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--limit",
            "1",
            "--output",
            "text");
    assertTrue(
        emptyListText.contains("No tax registrations matched the selected scope."), emptyListText);

    String emptyListCsv =
        runText(
            0,
            "list-tax-registrations",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--limit",
            "1",
            "--output",
            "csv");
    assertTrue(emptyListCsv.contains("exportFamily,rowId,recordKind"), emptyListCsv);
    assertTrue(
        emptyListCsv.contains("No tax registrations matched the selected scope."), emptyListCsv);

    Path latviaRegistrationRequest =
        writeNamedRequest(
            "declare-tax-registration-vat-lv.json", latviaVatRegistrationJson(null, 20));
    JsonNode declaredEnvelope =
        runJson(
            0,
            "declare-tax-registration",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--request-file",
            latviaRegistrationRequest.toString());
    assertEquals("declared", declaredEnvelope.path("payload").path("outcome").stringValue());
    assertEquals(
        "vat-lv",
        declaredEnvelope
            .path("payload")
            .path("registration")
            .path("taxRegistrationId")
            .stringValue());

    String unchangedText =
        runText(
            0,
            "declare-tax-registration",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--request-file",
            latviaRegistrationRequest.toString(),
            "--output",
            "text");
    assertTrue(unchangedText.contains("Tax Registration Unchanged"), unchangedText);
    assertTrue(unchangedText.contains("Outcome"), unchangedText);
    assertTrue(unchangedText.contains("unchanged"), unchangedText);

    Path updatedLatviaRegistrationRequest =
        writeNamedRequest(
            "declare-tax-registration-vat-lv-updated.json",
            latviaVatRegistrationJson("LV40001234567", 21));
    String updatedText =
        runText(
            0,
            "declare-tax-registration",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--request-file",
            updatedLatviaRegistrationRequest.toString(),
            "--output",
            "text");
    assertTrue(updatedText.contains("Tax Registration Updated"), updatedText);
    assertTrue(updatedText.contains("Registration number"), updatedText);
    assertTrue(updatedText.contains("LV40001234567"), updatedText);
    assertTrue(updatedText.contains("Due days after period end : 21"), updatedText);

    Path estoniaRegistrationRequest =
        writeNamedRequest("declare-tax-registration-vat-ee.json", estoniaVatRegistrationJson());
    JsonNode estoniaDeclaredEnvelope =
        runJson(
            0,
            "declare-tax-registration",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--request-file",
            estoniaRegistrationRequest.toString());
    assertEquals(
        "vat-ee",
        estoniaDeclaredEnvelope
            .path("payload")
            .path("registration")
            .path("taxRegistrationId")
            .stringValue());

    JsonNode firstPageEnvelope =
        runJson(
            0,
            "list-tax-registrations",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--limit",
            "1");
    assertEquals(
        "vat-ee",
        firstPageEnvelope
            .path("payload")
            .path("registrations")
            .get(0)
            .path("taxRegistrationId")
            .stringValue());
    String nextCursor = firstPageEnvelope.path("payload").path("nextCursor").stringValue();
    assertTrue(!nextCursor.isBlank(), firstPageEnvelope.toPrettyString());

    String secondPageText =
        runText(
            0,
            "list-tax-registrations",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--limit",
            "1",
            "--cursor",
            nextCursor,
            "--output",
            "text");
    assertTrue(secondPageText.contains("Tax Registrations"), secondPageText);
    assertTrue(secondPageText.contains("vat-lv"), secondPageText);
    assertTrue(secondPageText.contains("Latvia VAT"), secondPageText);

    String fullListCsv =
        runText(
            0,
            "list-tax-registrations",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--limit",
            "10",
            "--output",
            "csv");
    assertTrue(fullListCsv.contains("tax-registration:vat-ee"), fullListCsv);
    assertTrue(fullListCsv.contains("tax-registration:vat-lv"), fullListCsv);
    assertTrue(fullListCsv.contains("taxCodeIds"), fullListCsv);
  }

  @Test
  void run_taxPostingQueriesAndObligationCommandsCoverResolvedFactsAndRejections()
      throws IOException {
    TaxWorkflowContext workflow = openTaxWorkflow("tax-obligation-books");
    declareTaxRegistrations(workflow);
    TaxPostingIds postingIds = commitTaxedPostings(workflow);

    assertPostingQueriesExposeResolvedTaxFacts(workflow, postingIds);
    assertTaxObligationOutputs(workflow);
    assertTaxObligationRejections(workflow);
  }

  private TaxPostingIds commitTaxedPostings(TaxWorkflowContext workflow) throws IOException {
    String salePostingId =
        commitPosting(
            workflow,
            "record-sale-settled",
            writeNamedRequest(
                "record-sale-settled-taxed.json",
                saleRequestJson(
                    "2026-04-05",
                    "command-sale-taxed",
                    "idem-sale-taxed",
                    "document-sale-taxed",
                    "1000",
                    "4000",
                    "10000",
                    "vat-lv",
                    "vat-standard-sale")));
    commitPosting(
        workflow,
        "record-sale-settled",
        writeNamedRequest(
            "record-sale-settled-taxed-second.json",
            saleRequestJson(
                "2026-04-06",
                "command-sale-taxed-second",
                "idem-sale-taxed-second",
                "document-sale-taxed-second",
                "1000",
                "4000",
                "5000",
                "vat-lv",
                "vat-standard-sale")));
    String recoverableExpensePostingId =
        commitPosting(
            workflow,
            "record-expense-settled",
            writeNamedRequest(
                "record-expense-settled-taxed.json",
                expenseRequestJson(
                    "2026-04-07",
                    "command-expense-taxed",
                    "idem-expense-taxed",
                    "document-expense-taxed",
                    "5000",
                    "1000",
                    "6050",
                    "vat-lv",
                    "vat-standard-expense")));
    String nonrecoverableExpensePostingId =
        commitPosting(
            workflow,
            "record-expense-settled",
            writeNamedRequest(
                "record-expense-settled-taxed-nonrecoverable.json",
                expenseRequestJson(
                    "2026-04-07",
                    "command-expense-taxed-nonrecoverable",
                    "idem-expense-taxed-nonrecoverable",
                    "document-expense-taxed-nonrecoverable",
                    "5000",
                    "1000",
                    "5600",
                    "vat-lv",
                    "vat-nonrecoverable-expense")));
    return new TaxPostingIds(
        salePostingId, recoverableExpensePostingId, nonrecoverableExpensePostingId);
  }

  private void assertPostingQueriesExposeResolvedTaxFacts(
      TaxWorkflowContext workflow, TaxPostingIds postingIds) throws IOException {
    JsonNode salePostingEnvelope =
        runJson(
            0,
            "get-posting",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--posting-id",
            postingIds.salePostingId());
    JsonNode saleEntry = salePostingEnvelope.path("payload").path("posting").path("entry");
    assertEquals("SALE_SETTLED", saleEntry.path("entryKind").stringValue());
    assertEquals("vat-lv", saleEntry.path("taxSelection").path("taxRegistrationId").stringValue());
    assertEquals("vat-standard-sale", saleEntry.path("taxSelection").path("taxCode").stringValue());
    assertEquals(
        "VAT Standard Sale", saleEntry.path("appliedTax").path("taxCodeName").stringValue());
    assertEquals(
        "2100", saleEntry.path("appliedTax").path("taxAmount").path("minorUnits").stringValue());
    assertEquals("2100", saleEntry.path("appliedTax").path("taxAccountCode").stringValue());

    String recoverableExpensePostingText =
        runText(
            0,
            "get-posting",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--posting-id",
            postingIds.recoverableExpensePostingId(),
            "--output",
            "text");
    assertTrue(
        recoverableExpensePostingText.contains("Entry facts"), recoverableExpensePostingText);
    assertTrue(
        recoverableExpensePostingText.contains("Resolved tax code name"),
        recoverableExpensePostingText);
    assertTrue(
        recoverableExpensePostingText.contains("VAT Standard Expense"),
        recoverableExpensePostingText);
    assertTrue(
        recoverableExpensePostingText.contains("Tax account"), recoverableExpensePostingText);
    assertTrue(recoverableExpensePostingText.contains("1300"), recoverableExpensePostingText);

    JsonNode nonrecoverableExpensePostingEnvelope =
        runJson(
            0,
            "get-posting",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--posting-id",
            postingIds.nonrecoverableExpensePostingId());
    JsonNode nonrecoverableAppliedTax =
        nonrecoverableExpensePostingEnvelope
            .path("payload")
            .path("posting")
            .path("entry")
            .path("appliedTax");
    assertEquals(
        "INPUT_EXPENSE_NONRECOVERABLE",
        nonrecoverableAppliedTax.path("applicationKind").stringValue());
    assertTrue(
        nonrecoverableAppliedTax.path("taxAccountCode").isMissingNode()
            || nonrecoverableAppliedTax.path("taxAccountCode").isNull(),
        nonrecoverableExpensePostingEnvelope.toPrettyString());
  }

  private void assertTaxObligationOutputs(TaxWorkflowContext workflow) throws IOException {
    JsonNode obligationEnvelope =
        runJson(
            0,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "vat-lv",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30");
    JsonNode payload = obligationEnvelope.path("payload");
    assertEquals("tax-obligation", payload.path("family").stringValue());
    assertEquals("EUR 31.50", payload.path("verdicts").get(0).path("value").stringValue());
    assertEquals("EUR 10.50", payload.path("verdicts").get(1).path("value").stringValue());
    assertEquals("EUR 6.00", payload.path("verdicts").get(2).path("value").stringValue());
    assertEquals("EUR 21.00", payload.path("verdicts").get(3).path("value").stringValue());
    JsonNode codeSummaries = payload.path("sections").get(0);
    assertEquals("codeSummaries", codeSummaries.path("key").stringValue());
    assertEquals(3, codeSummaries.path("rows").size());
    assertEquals(
        "EUR 21.00",
        codeSummaries.path("totals").get(0).path("rows").get(3).path("cells").get(1).stringValue());

    String obligationText =
        runText(
            0,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "vat-lv",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30",
            "--output",
            "text");
    assertTrue(obligationText.contains("Tax Obligation"), obligationText);
    assertTrue(obligationText.contains("Code summaries"), obligationText);
    assertTrue(obligationText.contains("Net payable"), obligationText);
    assertTrue(obligationText.contains("EUR 21.00"), obligationText);

    String obligationCsv =
        runText(
            0,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "vat-lv",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30",
            "--output",
            "csv");
    assertTrue(
        obligationCsv.contains(
            "exportFamily,rowId,parentRowId,relationKind,recordKind,taxRegistrationId,taxRegistrationName,taxJurisdiction,taxRegistrationNumber,effectiveDateFrom,effectiveDateTo,dueDate,taxCode,taxCodeName,applicationKind,postingCount,currencyCode,taxableAmount,taxAmount,grossAmount,outputTax,recoverableInputTax,nonrecoverableInputTax,netPayable,netReceivable,message"),
        obligationCsv);
    assertTrue(
        obligationCsv.contains(
            "tax-obligation-row:vat-standard-sale:OUTPUT_SALE,,line,tax-obligation,vat-lv"),
        obligationCsv);
    assertTrue(
        obligationCsv.contains(",vat-standard-sale,VAT Standard Sale,OUTPUT_SALE,"), obligationCsv);
    assertTrue(
        obligationCsv.contains("tax-obligation-total:EUR,,report-total,tax-obligation,vat-lv"),
        obligationCsv);
    assertTrue(obligationCsv.contains("31.50"), obligationCsv);
    assertTrue(obligationCsv.contains("10.50"), obligationCsv);
    assertTrue(obligationCsv.contains("6.00"), obligationCsv);
    assertTrue(obligationCsv.contains("21.00"), obligationCsv);
    assertTrue(obligationCsv.contains("0.00"), obligationCsv);

    String emptyObligationText =
        runText(
            0,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "vat-ee",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30",
            "--output",
            "text");
    assertTrue(
        emptyObligationText.contains(
            "No tax obligation code summaries matched the selected scope."),
        emptyObligationText);

    String emptyObligationCsv =
        runText(
            0,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "vat-ee",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30",
            "--output",
            "csv");
    assertTrue(
        emptyObligationCsv.contains("tax-obligation-total:EUR,,report-empty,tax-obligation,vat-ee"),
        emptyObligationCsv);
    assertTrue(
        emptyObligationCsv.contains("No tax obligation code summaries matched the selected scope."),
        emptyObligationCsv);

    Path textPdfOut = tempDirectory.resolve("tax-obligation-text.pdf");
    String textPdfOutput =
        runText(
            0,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "vat-lv",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30",
            "--output",
            "text",
            "--pdf-out",
            textPdfOut.toString());
    assertEquals(
        CliArtifactOutputRenderer.renderPdfArtifact(textPdfOut) + System.lineSeparator(),
        textPdfOutput);
    assertTrue(Files.exists(textPdfOut), textPdfOut.toString());

    Path jsonPdfOut = tempDirectory.resolve("tax-obligation-json.pdf");
    JsonNode pdfEnvelope =
        runJson(
            0,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "vat-lv",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30",
            "--output",
            "json",
            "--pdf-out",
            jsonPdfOut.toString());
    assertEquals(1, pdfEnvelope.path("artifacts").size());
    assertEquals("pdf", pdfEnvelope.path("artifacts").get(0).path("format").stringValue());
    assertEquals(
        CliPublicPaths.redactedValue(jsonPdfOut),
        pdfEnvelope.path("artifacts").get(0).path("path").stringValue());
    assertTrue(Files.exists(jsonPdfOut), jsonPdfOut.toString());
  }

  private void assertTaxObligationRejections(TaxWorkflowContext workflow) throws IOException {
    JsonNode unknownRegistrationEnvelope =
        runJson(
            2,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "missing-tax",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30");
    assertEquals(
        "unknown-tax-registration", unknownRegistrationEnvelope.path("code").stringValue());
    assertEquals(
        "missing-tax",
        unknownRegistrationEnvelope.path("details").path("taxRegistrationId").stringValue());

    JsonNode cadenceMismatchEnvelope =
        runJson(
            2,
            "tax-obligation",
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--tax-registration-id",
            "vat-lv",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-06-30");
    assertEquals(
        "tax-obligation-period-mismatch", cadenceMismatchEnvelope.path("code").stringValue());
    assertTrue(
        cadenceMismatchEnvelope.path("message").stringValue().contains("declared filing cadence"),
        cadenceMismatchEnvelope.toPrettyString());
    assertEquals(
        "MONTHLY",
        cadenceMismatchEnvelope.path("details").path("obligationFrequency").stringValue());
  }

  private TaxWorkflowContext openTaxWorkflow(String directoryName) throws IOException {
    Path bookFilePath = tempDirectory.resolve(directoryName).resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    TaxWorkflowContext workflow = new TaxWorkflowContext(bookFilePath, bookKeyFilePath);
    declareTaxAccounts(workflow);
    return workflow;
  }

  private void declareTaxAccounts(TaxWorkflowContext workflow) throws IOException {
    declareAccount(
        workflow,
        "declare-cash.json",
        declareAccountJson("1000", "Cash", "ASSET", "CURRENT_ASSET", null));
    declareAccount(
        workflow,
        "declare-revenue.json",
        declareAccountJson("4000", "Service Revenue", "REVENUE", null, "OPERATING_REVENUE"));
    declareAccount(
        workflow,
        "declare-expense.json",
        declareAccountJson("5000", "Operating Expense", "EXPENSE", null, "OPERATING_EXPENSE"));
    declareAccount(
        workflow,
        "declare-tax-payable.json",
        declareAccountJson("2100", "VAT Payable", "LIABILITY", "CURRENT_LIABILITY", null));
    declareAccount(
        workflow,
        "declare-tax-recoverable.json",
        declareAssetAccountJson("1300", "VAT Recoverable", "CURRENT_ASSET", "NON_CASH"));
  }

  private void declareTaxRegistrations(TaxWorkflowContext workflow) throws IOException {
    runJson(
        0,
        "declare-tax-registration",
        "--book-file",
        workflow.bookFilePath().toString(),
        "--book-key-file",
        workflow.bookKeyFilePath().toString(),
        "--request-file",
        writeNamedRequest(
                "declare-tax-registration-vat-lv.json", latviaVatRegistrationJson(null, 20))
            .toString());
    runJson(
        0,
        "declare-tax-registration",
        "--book-file",
        workflow.bookFilePath().toString(),
        "--book-key-file",
        workflow.bookKeyFilePath().toString(),
        "--request-file",
        writeNamedRequest("declare-tax-registration-vat-ee.json", estoniaVatRegistrationJson())
            .toString());
  }

  private void declareAccount(TaxWorkflowContext workflow, String fileName, String requestJson)
      throws IOException {
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                jsonArguments(
                    "declare-account",
                    "--book-file",
                    workflow.bookFilePath().toString(),
                    "--book-key-file",
                    workflow.bookKeyFilePath().toString(),
                    "--request-file",
                    writeNamedRequest(fileName, requestJson).toString())));
  }

  private String commitPosting(TaxWorkflowContext workflow, String commandName, Path requestFile)
      throws IOException {
    JsonNode envelope =
        runJson(
            0,
            commandName,
            "--book-file",
            workflow.bookFilePath().toString(),
            "--book-key-file",
            workflow.bookKeyFilePath().toString(),
            "--request-file",
            requestFile.toString());
    return envelope.path("payload").path("postingId").stringValue();
  }

  private JsonNode runJson(int expectedExitCode, String... arguments) throws IOException {
    return JSON.readTree(runOutput(expectedExitCode, jsonArguments(arguments)));
  }

  private String runText(int expectedExitCode, String... arguments) {
    return runOutput(expectedExitCode, arguments);
  }

  private String runOutput(int expectedExitCode, String... arguments) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock())
            .run(arguments);
    String rendered = output.toString(StandardCharsets.UTF_8);
    assertEquals(expectedExitCode, exitCode, rendered);
    return rendered;
  }

  private static String latviaVatRegistrationJson(
      @org.jspecify.annotations.Nullable String registrationNumber, int dueDaysAfterPeriodEnd) {
    return "{\n"
        + "  \"taxRegistrationId\": \"vat-lv\",\n"
        + "  \"taxRegistrationName\": \"Latvia VAT\",\n"
        + "  \"jurisdiction\": \"LV\",\n"
        + (registrationNumber == null
            ? ""
            : "  \"registrationNumber\": \"" + registrationNumber + "\",\n")
        + "  \"payableAccountCode\": \"2100\",\n"
        + "  \"recoverableAccountCode\": \"1300\",\n"
        + "  \"obligationFrequency\": \"MONTHLY\",\n"
        + "  \"dueDaysAfterPeriodEnd\": "
        + dueDaysAfterPeriodEnd
        + ",\n"
        + "  \"taxCodes\": [\n"
        + "    {\n"
        + "      \"taxCode\": \"vat-nonrecoverable-expense\",\n"
        + "      \"taxCodeName\": \"VAT Nonrecoverable Expense\",\n"
        + "      \"ratePartsPerMillion\": 120000,\n"
        + "      \"inclusionMode\": \"INCLUSIVE\",\n"
        + "      \"applicationKind\": \"INPUT_EXPENSE_NONRECOVERABLE\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"taxCode\": \"vat-standard-expense\",\n"
        + "      \"taxCodeName\": \"VAT Standard Expense\",\n"
        + "      \"ratePartsPerMillion\": 210000,\n"
        + "      \"inclusionMode\": \"INCLUSIVE\",\n"
        + "      \"applicationKind\": \"INPUT_EXPENSE_RECOVERABLE\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"taxCode\": \"vat-standard-sale\",\n"
        + "      \"taxCodeName\": \"VAT Standard Sale\",\n"
        + "      \"ratePartsPerMillion\": 210000,\n"
        + "      \"inclusionMode\": \"EXCLUSIVE\",\n"
        + "      \"applicationKind\": \"OUTPUT_SALE\"\n"
        + "    }\n"
        + "  ]\n"
        + "}\n";
  }

  private static String estoniaVatRegistrationJson() {
    return """
        {
          "taxRegistrationId": "vat-ee",
          "taxRegistrationName": "Estonia VAT",
          "jurisdiction": "EE",
          "payableAccountCode": "2100",
          "recoverableAccountCode": "1300",
          "obligationFrequency": "MONTHLY",
          "dueDaysAfterPeriodEnd": 20,
          "taxCodes": [
            {
              "taxCode": "vat-standard-sale",
              "taxCodeName": "VAT Standard Sale",
              "ratePartsPerMillion": 220000,
              "inclusionMode": "EXCLUSIVE",
              "applicationKind": "OUTPUT_SALE"
            }
          ]
        }
        """;
  }

  private static String declareAssetAccountJson(
      String accountCode,
      String accountName,
      String financialPositionLineClassification,
      String cashFlowAssetClassification) {
    return """
        {
          "accountCode": "%s",
          "accountName": "%s",
          "accountType": "ASSET",
          "accountNodeKind": "POSTABLE",
          "financialPositionLineClassification": "%s",
          "cashFlowAssetClassification": "%s",
          "profitAndLossLineClassification": null
        }
        """
        .formatted(
            accountCode,
            accountName,
            financialPositionLineClassification,
            cashFlowAssetClassification);
  }

  private static String saleRequestJson(
      String effectiveDate,
      String commandId,
      String idempotencyKey,
      String sourceDocumentId,
      String cashAccountCode,
      String revenueAccountCode,
      String amountMinorUnits,
      String taxRegistrationId,
      String taxCode) {
    return """
        {
          "entryKind": "SALE_SETTLED",
          "effectiveDate": "%s",
          "cashAccountCode": "%s",
          "revenueAccountCode": "%s",
          "amount": {
            "currencyCode": "EUR",
            "minorUnits": "%s"
          },
          "tax": {
            "taxRegistrationId": "%s",
            "taxCode": "%s"
          },
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "%s",
                "sourceDocumentType": "cash-receipt",
                "documentDate": "%s"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "actorId": "actor-%s",
            "actorType": "AGENT",
            "commandId": "%s",
            "idempotencyKey": "%s",
            "causationId": "cause-%s"
          }
        }
        """
        .formatted(
            effectiveDate,
            cashAccountCode,
            revenueAccountCode,
            amountMinorUnits,
            taxRegistrationId,
            taxCode,
            sourceDocumentId,
            effectiveDate,
            commandId,
            commandId,
            idempotencyKey,
            commandId);
  }

  private static String expenseRequestJson(
      String effectiveDate,
      String commandId,
      String idempotencyKey,
      String sourceDocumentId,
      String expenseAccountCode,
      String cashAccountCode,
      String amountMinorUnits,
      String taxRegistrationId,
      String taxCode) {
    return """
        {
          "entryKind": "EXPENSE_SETTLED",
          "effectiveDate": "%s",
          "expenseAccountCode": "%s",
          "cashAccountCode": "%s",
          "amount": {
            "currencyCode": "EUR",
            "minorUnits": "%s"
          },
          "tax": {
            "taxRegistrationId": "%s",
            "taxCode": "%s"
          },
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "%s",
                "sourceDocumentType": "expense-receipt",
                "documentDate": "%s"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "actorId": "actor-%s",
            "actorType": "AGENT",
            "commandId": "%s",
            "idempotencyKey": "%s",
            "causationId": "cause-%s"
          }
        }
        """
        .formatted(
            effectiveDate,
            expenseAccountCode,
            cashAccountCode,
            amountMinorUnits,
            taxRegistrationId,
            taxCode,
            sourceDocumentId,
            effectiveDate,
            commandId,
            commandId,
            idempotencyKey,
            commandId);
  }

  private record TaxWorkflowContext(Path bookFilePath, Path bookKeyFilePath) {}

  private record TaxPostingIds(
      String salePostingId,
      String recoverableExpensePostingId,
      String nonrecoverableExpensePostingId) {}
}
