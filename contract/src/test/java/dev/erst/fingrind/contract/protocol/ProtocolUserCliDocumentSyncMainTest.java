package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for the USER_CLI document-sync launcher. */
class ProtocolUserCliDocumentSyncMainTest {
  @Test
  void main_requiresExactlyOneArgument() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolUserCliDocumentSyncMain.main(new String[0]));

    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("Expected exactly one argument"));
  }

  @Test
  void main_synchronizesTheRequestedDocument(@TempDir Path tempDir) throws IOException {
    Path document = tempDir.resolve("USER_CLI.md");
    Files.writeString(
        document,
        """
        Header

        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
        old block
        <!-- END GENERATED USER_CLI COMMAND TABLE -->
        """);

    ProtocolUserCliDocumentSyncMain.main(new String[] {document.toString()});

    assertEquals(
        ProtocolUserCliDocumentSync.updatedDocument(Files.readString(document)),
        Files.readString(document));
  }
}
