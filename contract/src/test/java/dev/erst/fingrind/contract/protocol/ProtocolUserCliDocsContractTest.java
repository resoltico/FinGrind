package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** Locks the public CLI guide command table to the canonical protocol catalog. */
class ProtocolUserCliDocsContractTest extends ProtocolContractLintSupport {
  @Test
  void userCliCommandTable_matchesCanonicalGeneratedDocumentSync() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/USER_CLI.md")).replace("\r\n", "\n");

    assertEquals(
        document,
        ProtocolUserCliDocumentSync.updatedDocument(document),
        "docs/USER_CLI.md must stay synchronized with the canonical protocol-owned command-table renderer.");
  }
}
