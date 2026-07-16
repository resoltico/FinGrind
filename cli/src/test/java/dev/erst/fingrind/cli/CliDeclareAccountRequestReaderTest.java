package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliDeclareAccountRequestReaderTest extends CliRequestReaderTestSupport {

  @Test
  void readDeclareAccountCommand_readsFromFile() throws IOException {
    Path requestFile =
        writeNamedRequest(
            "declare-account.json",
            """
            {
              "accountCode": "1000",
              "accountName": "Cash",
              "accountType": "ASSET",
              "accountNodeKind": "POSTABLE",
              "financialPositionLineClassification": "CURRENT_ASSET",
              "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT",
              "profitAndLossLineClassification": null
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);

    assertEquals("1000", command.accountCode().value());
    assertEquals("Cash", command.accountName().value());
    assertEquals("ASSET", command.accountType().wireValue());
  }

  @Test
  void readDeclareAccountCommand_readsInventoryUnitOfMeasure() throws IOException {
    Path requestFile =
        writeNamedRequest(
            "declare-inventory-account.json",
            """
            {
              "accountCode": "1400",
              "accountName": "Inventory",
              "accountType": "ASSET",
              "accountNodeKind": "POSTABLE",
              "financialPositionLineClassification": "INVENTORY",
              "cashFlowAssetClassification": "NON_CASH",
              "unitOfMeasure": {
                "token": "kg",
                "quantityScale": 3
              }
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);

    assertEquals(new UnitOfMeasure("kg", 3), command.unitOfMeasure());
  }

  @Test
  void readDeclareAccountCommand_rejectsMissingRequiredField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountType": "ASSET",
                  "financialPositionLineClassification": "CURRENT_ASSET",
                  "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT",
                  "profitAndLossLineClassification": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Missing required field: accountName", exception.getMessage());
    assertEquals(
        "Add accountName to the request document, then rerun. If you need a starter file, run '"
            + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
            + " declare-account'.",
        exception.failure().hint());
  }

  @Test
  void readDeclareAccountCommand_rejectsObsoleteAccountRoleField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "accountType": "ASSET",
                  "accountRole": "SIDEWAYS",
                  "accountNodeKind": "POSTABLE",
                  "financialPositionLineClassification": "CURRENT_ASSET",
                  "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT",
                  "profitAndLossLineClassification": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));
    assertEquals("Unexpected field: accountRole", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsDerivedCurrentPeriodResultClassification() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "3000",
                  "accountName": "Derived Result Placeholder",
                  "accountType": "EQUITY",
                  "accountNodeKind": "POSTABLE",
                  "financialPositionLineClassification": "CURRENT_PERIOD_RESULT",
                  "profitAndLossLineClassification": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals(
        "Unsupported value for financialPositionLineClassification: CURRENT_PERIOD_RESULT. Accepted values: CURRENT_ASSET, INVENTORY, PREPAID_EXPENSE, NONCURRENT_ASSET, TRADE_RECEIVABLE, CURRENT_LIABILITY, NONCURRENT_LIABILITY, TRADE_PAYABLE, DEFERRED_REVENUE, ACCRUED_EXPENSE, EQUITY_CONTRIBUTION, EQUITY_WITHDRAWAL, RESULT_HOLDING, RETAINED_ACCUMULATED, RESERVE, OTHER_EQUITY.",
        exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsUnexpectedTopLevelField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "accountType": "ASSET",
                  "accountNodeKind": "POSTABLE",
                  "financialPositionLineClassification": "CURRENT_ASSET",
                  "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT",
                  "profitAndLossLineClassification": null,
                  "ignored": true
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Unexpected field: ignored", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_reportsEveryUnexpectedTopLevelFieldTogether() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "accountType": "ASSET",
                  "accountNodeKind": "POSTABLE",
                  "financialPositionLineClassification": "CURRENT_ASSET",
                  "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT",
                  "profitAndLossLineClassification": null,
                  "ignored": true,
                  "alsoIgnored": true
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Unexpected fields: ignored, alsoIgnored", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsNonObjectRootDocument() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                [
                  {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "accountType": "ASSET",
                    "accountNodeKind": "POSTABLE",
                    "financialPositionLineClassification": "CURRENT_ASSET",
                    "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT",
                    "profitAndLossLineClassification": null
                  }
                ]
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Request JSON document must be an object.", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsWrappedDeclareAccountPayload() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "accountType": "ASSET",
                    "accountNodeKind": "POSTABLE",
                    "financialPositionLineClassification": "CURRENT_ASSET",
                    "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT",
                    "profitAndLossLineClassification": null
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals(
        "Declare-account request fields must be top-level for direct request files; remove the declareAccount wrapper.",
        exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_prefersUnexpectedFieldWhenWrapperAndTopLevelFieldsAreMixed() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "accountType": "ASSET",
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "accountType": "ASSET"
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Unexpected field: declareAccount", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsOversizedFileAndStandardInput() throws IOException {
    String oversizedPayload =
        "{\"padding\":\"" + "a".repeat(ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES) + "\"}";
    Path oversizedRequest = writeNamedRequest("oversized-declare-account.json", oversizedPayload);

    CliRequestReader fileReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));
    CliRequestException fileException =
        assertThrows(
            CliRequestException.class,
            () -> fileReader.readDeclareAccountCommand(oversizedRequest));
    assertEquals(
        "Request file exceeded the supported "
            + ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES
            + "-byte UTF-8 limit.",
        fileException.getMessage());
    assertEquals(oversizedRequest, fileException.failure().path());

    CliRequestReader stdinReader =
        new CliRequestReader(
            new ByteArrayInputStream(oversizedPayload.getBytes(StandardCharsets.UTF_8)));
    CliRequestException stdinException =
        assertThrows(
            CliRequestException.class, () -> stdinReader.readDeclareAccountCommand(Path.of("-")));
    assertEquals(
        "Request JSON from standard input exceeded the supported "
            + ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES
            + "-byte UTF-8 limit.",
        stdinException.getMessage());
  }
}
