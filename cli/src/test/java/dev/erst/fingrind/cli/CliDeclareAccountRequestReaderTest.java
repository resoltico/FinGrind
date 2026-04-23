package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.DeclareAccountCommand;
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
              "normalBalance": "DEBIT"
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);

    assertEquals("1000", command.accountCode().value());
    assertEquals("Cash", command.accountName().value());
    assertEquals("DEBIT", command.normalBalance().name());
  }

  @Test
  void readDeclareAccountCommand_rejectsMissingRequiredField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "normalBalance": "DEBIT"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Missing required field: accountName", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsInvalidNormalBalance() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "normalBalance": "SIDEWAYS"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals(
        "Unsupported value for normalBalance: SIDEWAYS. Accepted values: DEBIT, CREDIT.",
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
                  "normalBalance": "DEBIT",
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
                  "normalBalance": "DEBIT",
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
                    "normalBalance": "DEBIT"
                  }
                ]
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Request JSON document must be an object.", exception.getMessage());
  }
}
