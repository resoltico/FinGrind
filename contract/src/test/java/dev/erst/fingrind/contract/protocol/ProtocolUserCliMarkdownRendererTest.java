package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for the USER_CLI command-table renderer and synchronizer. */
class ProtocolUserCliMarkdownRendererTest {
  @Test
  void commandTableBlock_preservesCanonicalOptionSyntaxWithoutLossyNormalization() {
    String block = ProtocolUserCliMarkdownRenderer.commandTableBlock();

    assertTrue(block.contains("<code>[--output &lt;json|text&gt;]</code>"));
    assertTrue(block.contains("<code>--request-file &lt;path|-&gt;</code>"));
    assertFalse(block.contains("<json or text>"));
    assertFalse(block.contains("<path or ->"));
  }

  @Test
  void commandTableBlock_marksEveryQuorumAuthorizedMaintenanceCommandAsRepeatable() {
    String block = ProtocolUserCliMarkdownRenderer.commandTableBlock();
    String credentialSyntax =
        "<code>"
            + ProtocolOptionSyntax.Attestation.requiredCredentialSyntax()
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            + "</code>";

    assertMaintenanceCommandUsesCredentialSyntax(block, "rekey-book", credentialSyntax);
    assertMaintenanceCommandUsesCredentialSyntax(block, "backup-book", credentialSyntax);
    assertMaintenanceCommandUsesCredentialSyntax(block, "restore-book", credentialSyntax);
  }

  @Test
  void commandTableBlock_marksExecutePlanCredentialsConditionalOnTheDecodedPlan() {
    String block = ProtocolUserCliMarkdownRenderer.commandTableBlock();
    String credentialSyntax =
        "<code>"
            + ProtocolOptionSyntax.Attestation.conditionalExecutePlanCredentialSyntax()
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            + "</code>";

    assertMaintenanceCommandUsesCredentialSyntax(block, "execute-plan", credentialSyntax);
  }

  @Test
  void updatedDocument_replacesOnlyTheGeneratedCommandTableBlock() {
    String original =
        """
        # Header

        Before

        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
        old block
        <!-- END GENERATED USER_CLI COMMAND TABLE -->

        After
        """;

    String updated = ProtocolUserCliDocumentSync.updatedDocument(original);

    assertTrue(updated.contains(ProtocolUserCliMarkdownRenderer.commandTableBlock()));
    assertTrue(updated.startsWith("# Header\n\nBefore\n\n"));
    assertTrue(updated.endsWith("\n\nAfter\n"));
    assertEquals(updated, ProtocolUserCliDocumentSync.updatedDocument(updated));
  }

  @Test
  void updatedDocument_returnsOneTrailingNewlineWhenGeneratedBlockEndsTheDocument() {
    String original =
        """
        Intro

        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
        old block
        <!-- END GENERATED USER_CLI COMMAND TABLE -->
        """;

    String updated = ProtocolUserCliDocumentSync.updatedDocument(original);

    assertTrue(updated.endsWith(ProtocolUserCliMarkdownRenderer.COMMAND_TABLE_END + "\n"));
    assertFalse(updated.endsWith("\n\n"));
  }

  @Test
  void updatedDocument_rejectsMissingBeginMarker() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolUserCliDocumentSync.updatedDocument("no generated markers here"));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("begin marker"));
  }

  @Test
  void updatedDocument_rejectsDuplicateBeginMarker() {
    String document =
        """
        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
        one
        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
        two
        <!-- END GENERATED USER_CLI COMMAND TABLE -->
        """;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolUserCliDocumentSync.updatedDocument(document));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("only one generated command-table begin marker"));
  }

  @Test
  void updatedDocument_rejectsMissingEndMarker() {
    String document =
        """
        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
        old block
        """;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolUserCliDocumentSync.updatedDocument(document));

    assertTrue(Objects.requireNonNull(exception.getMessage()).contains("end marker"));
  }

  @Test
  void updatedDocument_rejectsDuplicateEndMarker() {
    String document =
        """
        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
        old block
        <!-- END GENERATED USER_CLI COMMAND TABLE -->
        <!-- END GENERATED USER_CLI COMMAND TABLE -->
        """;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolUserCliDocumentSync.updatedDocument(document));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("only one generated command-table end marker"));
  }

  @Test
  void updatedDocument_rejectsEndMarkerBeforeBeginMarker() {
    String document =
        """
        <!-- END GENERATED USER_CLI COMMAND TABLE -->
        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
        old block
        """;

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolUserCliDocumentSync.updatedDocument(document));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("must appear after the begin marker"));
  }

  @Test
  void sync_rewritesUnsynchronizedDocumentsAndNormalizesLineEndings(@TempDir Path tempDir)
      throws IOException {
    Path document = tempDir.resolve("USER_CLI.md");
    Files.writeString(
        document,
"""
        Header\r
\r
        <!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->\r
        old block\r
        <!-- END GENERATED USER_CLI COMMAND TABLE -->\r
        """);

    ProtocolUserCliDocumentSync.sync(document);

    String updated = Files.readString(document);
    assertEquals(ProtocolUserCliDocumentSync.updatedDocument(updated), updated);
    assertFalse(updated.contains("\r"));
  }

  @Test
  void sync_leavesAlreadySynchronizedDocumentsUnchanged(@TempDir Path tempDir) throws IOException {
    Path document = tempDir.resolve("USER_CLI.md");
    String synchronizedDocument =
        """
        Header

        """
            + ProtocolUserCliMarkdownRenderer.commandTableBlock()
            + "\n";
    Files.writeString(document, synchronizedDocument);

    ProtocolUserCliDocumentSync.sync(document);

    assertEquals(synchronizedDocument, Files.readString(document));
  }

  private static void assertMaintenanceCommandUsesCredentialSyntax(
      String block, String command, String credentialSyntax) {
    int commandStart = block.indexOf("<code>" + command + "</code>");
    assertTrue(commandStart >= 0, "missing command row for " + command);
    int commandEnd = block.indexOf("</tr>", commandStart);
    assertTrue(commandEnd >= 0, "unterminated command row for " + command);
    assertTrue(
        block.substring(commandStart, commandEnd).contains(credentialSyntax),
        command
            + " must publish repeatable aligned attestation credential triplets under explicit custody.");
  }
}
