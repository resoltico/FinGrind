package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderProvenanceValidationTest extends CliRequestReaderTestSupport {

  @Test
  void readPostEntryCommand_rejectsMissingProvenanceObject() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": []
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: provenance", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsNonObjectProvenanceField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": [],
                  "provenance": "not-an-object"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field must be an object: provenance", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsExplicitNullProvenanceField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": [],
                  "provenance": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: provenance", exception.getMessage());
  }
}
