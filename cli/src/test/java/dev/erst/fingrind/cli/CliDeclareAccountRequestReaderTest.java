package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
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
              "accountRole": "ORDINARY",
              "financialPositionLineClassification": "CURRENT_ASSET",
              "profitAndLossLineClassification": null
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);

    assertEquals("1000", command.accountCode().value());
    assertEquals("Cash", command.accountName().value());
    assertEquals("ASSET", command.accountType().wireValue());
    assertEquals("ORDINARY", command.accountRole().wireValue());
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
                  "accountRole": "ORDINARY",
                  "financialPositionLineClassification": "CURRENT_ASSET",
                  "profitAndLossLineClassification": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Missing required field: accountName", exception.getMessage());
    assertEquals(CliJsonRequestHints.declareAccountRequestHint(), exception.failure().hint());
  }

  @Test
  void readDeclareAccountCommand_rejectsInvalidAccountRole() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "accountType": "ASSET",
                  "accountRole": "SIDEWAYS",
                  "financialPositionLineClassification": "CURRENT_ASSET",
                  "profitAndLossLineClassification": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals(
        "Unsupported value for accountRole: SIDEWAYS. Accepted values: ORDINARY, CONTRA.",
        exception.getMessage());
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
                  "accountRole": "ORDINARY",
                  "financialPositionLineClassification": "CURRENT_PERIOD_RESULT",
                  "profitAndLossLineClassification": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals(
        "Unsupported value for financialPositionLineClassification: CURRENT_PERIOD_RESULT. Accepted values: CURRENT_ASSET, NONCURRENT_ASSET, CURRENT_LIABILITY, NONCURRENT_LIABILITY, OWNER_CAPITAL, OWNER_DRAWINGS, PARTNER_CAPITAL, PARTNER_CURRENT, SHARE_CAPITAL, RETAINED_EARNINGS, ACCUMULATED_SURPLUS, RESERVE, OTHER_EQUITY.",
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
                  "accountRole": "ORDINARY",
                  "financialPositionLineClassification": "CURRENT_ASSET",
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
                  "accountRole": "ORDINARY",
                  "financialPositionLineClassification": "CURRENT_ASSET",
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
                    "accountRole": "ORDINARY",
                    "financialPositionLineClassification": "CURRENT_ASSET",
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
                    "accountRole": "ORDINARY",
                    "financialPositionLineClassification": "CURRENT_ASSET",
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
                  "accountRole": "ORDINARY",
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "accountType": "ASSET",
                    "accountRole": "ORDINARY"
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Unexpected field: declareAccount", exception.getMessage());
  }
}
