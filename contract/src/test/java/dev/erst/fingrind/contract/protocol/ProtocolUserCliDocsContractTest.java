package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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

  @Test
  void readmeQuickStart_isBundleSafeAndFixtureFree() throws IOException {
    String readme = Files.readString(repositoryRoot().resolve("README.md"));

    assertTrue(readme.contains("fingrind print-request-template > ./request.json"));
    assertTrue(readme.contains("fingrind preflight-entry --book-file ./books/acme.sqlite"));
    assertTrue(readme.contains("--request-file ./request.json"));
    assertFalse(readme.contains("cp ./quick-start-request.json ./request.json"));
    assertFalse(readme.contains("./docs/examples/basic-posting-request.json"));
  }

  @Test
  void readmeQuickStartCommands_matchCanonicalPathPosixWorkflow() throws IOException {
    String readme = Files.readString(repositoryRoot().resolve("README.md")).replace("\r\n", "\n");
    List<String> expectedCommands =
        MachineContract.quickStart(WorkflowSurface.PATH_POSIX_SHELL).steps().stream()
            .filter(WorkflowStepDescriptor.Command.class::isInstance)
            .map(WorkflowStepDescriptor.Command.class::cast)
            .map(WorkflowStepDescriptor.Command::text)
            .toList();

    assertEquals(expectedCommands, extractReadmeQuickStartCommands(readme));
  }

  @Test
  void publicDocsDoNotUseLegacyStarterChartTerm() throws IOException {
    List<String> documents =
        List.of(
            Files.readString(repositoryRoot().resolve("CHANGELOG.md")),
            Files.readString(repositoryRoot().resolve("README.md")),
            Files.readString(repositoryRoot().resolve("docs/DOC_01_Core.md")),
            Files.readString(repositoryRoot().resolve("docs/DOC_02_AdministrationAndReports.md")),
            Files.readString(repositoryRoot().resolve("docs/USER_CLI.md")),
            Files.readString(repositoryRoot().resolve("docs/USER_EXAMPLES.md")),
            Files.readString(repositoryRoot().resolve("docs/USER_QUICK_START.md")),
            Files.readString(repositoryRoot().resolve("docs/USER_RESPONSES.md")),
            Files.readString(repositoryRoot().resolve("cli/src/bundle/root/README.md")));

    for (String document : documents) {
      assertFalse(document.contains("starter chart"));
      assertFalse(document.contains("Starter chart"));
      assertFalse(document.contains("starter-chart"));
      assertFalse(document.contains("Starter-chart"));
      assertFalse(document.contains("starterChart"));
    }
  }

  private static List<String> extractReadmeQuickStartCommands(String readme) {
    String quickStartMarker = "\n## Quick Start\n";
    int quickStartIndex = readme.indexOf(quickStartMarker);
    assertTrue(quickStartIndex >= 0, "README.md must publish a Quick Start section.");
    int blockStart = readme.indexOf("\n```bash\n", quickStartIndex);
    assertTrue(blockStart >= 0, "README.md Quick Start must publish a bash command block.");
    int contentStart = blockStart + "\n```bash\n".length();
    int blockEnd = readme.indexOf("\n```", contentStart);
    assertTrue(blockEnd >= 0, "README.md Quick Start command block must close cleanly.");
    return normalizeShellCommands(readme.substring(contentStart, blockEnd));
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

  private static List<String> normalizeShellCommands(String block) {
    List<String> commands = new ArrayList<>();
    StringBuilder currentCommand = new StringBuilder();
    for (String rawLine : block.lines().toList()) {
      String trimmed = rawLine.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      boolean continued = trimmed.endsWith("\\");
      String commandPart =
          continued ? trimmed.substring(0, trimmed.length() - 1).stripTrailing() : trimmed;
      if (!currentCommand.isEmpty()) {
        currentCommand.append(' ');
      }
      currentCommand.append(commandPart);
      if (!continued) {
        commands.add(currentCommand.toString().replaceAll("\\s+", " ").trim());
        currentCommand.setLength(0);
      }
    }
    assertTrue(
        currentCommand.isEmpty(),
        "README.md Quick Start must not end with a dangling continuation.");
    return commands;
  }
}
