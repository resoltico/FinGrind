package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Locks the public CLI guide command table to the canonical protocol catalog. */
class ProtocolUserCliDocsContractTest extends ProtocolContractRepositorySupport {
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

  @Test
  void publicGettingStartedDocs_keepBookFilesAndKeyFilesInSeparateTrees() throws IOException {
    String readme = Files.readString(repositoryRoot().resolve("README.md"));
    String quickStart = Files.readString(repositoryRoot().resolve("docs/USER_QUICK_START.md"));
    String examplesGuide = Files.readString(repositoryRoot().resolve("docs/USER_EXAMPLES.md"));

    assertTrue(readme.contains("./books/acme.sqlite"));
    assertTrue(readme.contains("./secrets/acme.book-key"));
    assertFalse(readme.contains("./acme.sqlite"));
    assertFalse(readme.contains("./acme.book-key"));

    assertTrue(quickStart.contains("./books/acme.sqlite"));
    assertTrue(quickStart.contains("./secrets/acme.book-key"));
    assertFalse(quickStart.contains("./acme.sqlite"));
    assertFalse(quickStart.contains("./acme.book-key"));

    assertTrue(examplesGuide.contains("./books/acme.sqlite"));
    assertTrue(examplesGuide.contains("./secrets/acme.book-key"));
    assertTrue(examplesGuide.contains(".\\books\\acme.sqlite"));
    assertTrue(examplesGuide.contains(".\\secrets\\acme.book-key"));
    assertFalse(examplesGuide.contains("./backup/acme.book-key"));
    assertFalse(examplesGuide.contains(".\\backup\\acme.book-key"));
  }

  @Test
  void readmeQuickStartTrialBalanceBlock_matchesCanonicalExampleFixture() throws IOException {
    String readme = Files.readString(repositoryRoot().resolve("README.md")).replace("\r\n", "\n");
    String expected =
        Files.readString(repositoryRoot().resolve("docs/examples/trial-balance-text.txt"))
            .replace("\r\n", "\n");

    assertEquals(expected.stripTrailing(), extractReadmeTrialBalanceBlock(readme).stripTrailing());
  }

  private static String extractReadmeTrialBalanceBlock(String readme) {
    String marker = "\n```\nTrial Balance\n=============\n";
    int markerIndex = readme.indexOf(marker);
    assertTrue(markerIndex >= 0, "README.md must publish the quick-start trial-balance block.");
    int contentStart = markerIndex + "\n```\n".length();
    int contentEnd = readme.indexOf("\n```", contentStart);
    assertTrue(contentEnd >= 0, "README.md quick-start trial-balance block must close cleanly.");
    return readme.substring(contentStart, contentEnd + 1);
  }
}
