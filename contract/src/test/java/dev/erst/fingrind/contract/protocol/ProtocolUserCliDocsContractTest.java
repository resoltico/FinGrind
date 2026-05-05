package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Locks the public CLI guide command table to the canonical protocol catalog. */
class ProtocolUserCliDocsContractTest extends ProtocolContractLintSupport {
  private static final Pattern RELEASE_NUMBERED_LAUNCHER_PATH_PATTERN =
      Pattern.compile(
          "fingrind-\\d+\\.\\d+\\.\\d+-[^\\s`/\\\\]+(?:/bin/fingrind|\\\\bin\\\\fingrind\\.ps1)");

  @Test
  void userCliCommandTable_matchesCanonicalGeneratedDocumentSync() throws IOException {
    String document =
        Files.readString(repositoryRoot().resolve("docs/USER_CLI.md")).replace("\r\n", "\n");

    assertEquals(
        document,
        ProtocolUserCliDocumentSync.updatedDocument(document),
        "docs/USER_CLI.md must stay synchronized with the canonical protocol-owned command-table renderer.");
  }

  @Test
  void publishedBundleExamples_avoidLiteralReleaseNumberedLauncherPaths() throws IOException {
    String cliGuide = Files.readString(repositoryRoot().resolve("docs/USER_CLI.md"));
    String examplesGuide = Files.readString(repositoryRoot().resolve("docs/USER_EXAMPLES.md"));

    assertFalse(
        RELEASE_NUMBERED_LAUNCHER_PATH_PATTERN.matcher(cliGuide).find(),
        "docs/USER_CLI.md must derive the extracted bundle launcher path instead of pinning one release-numbered root.");
    assertFalse(
        RELEASE_NUMBERED_LAUNCHER_PATH_PATTERN.matcher(examplesGuide).find(),
        "docs/USER_EXAMPLES.md must describe the extracted bundle launcher generically instead of pinning one release-numbered root.");
  }
}
