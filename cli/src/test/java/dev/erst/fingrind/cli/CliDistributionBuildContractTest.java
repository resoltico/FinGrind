package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Contract-lint tests for repo-owned CLI distribution build assets. */
class CliDistributionBuildContractTest {
  @Test
  void dockerBuild_reusesTheStagedRuntimeModuleList() throws IOException {
    String dockerfile = Files.readString(repositoryRoot().resolve("Dockerfile"));

    assertTrue(
        dockerfile.contains("COPY cli/build/docker/runtime-modules.txt runtime-modules.txt"));
    assertTrue(
        dockerfile.contains(
            "COPY cli/build/docker/docker-entrypoint.sh /opt/fingrind/bin/docker-entrypoint.sh"));
    assertFalse(dockerfile.contains("COPY cli/build/docker/jdeps/ jdeps/"));
    assertFalse(dockerfile.contains("RUN jdeps "));
  }

  @Test
  void cliBuild_stagesSharedRuntimeModulesAndRetainsPdfboxSupportModule() throws IOException {
    String buildScript = Files.readString(repositoryRoot().resolve("cli/build.gradle.kts"));

    assertTrue(buildScript.contains("docker/runtime-modules.txt"));
    assertTrue(buildScript.contains("docker/docker-entrypoint.sh"));
    assertTrue(buildScript.contains("additionalModules.set(listOf(\"jdk.unsupported\"))"));
    assertTrue(
        buildScript.contains(
            "DistributionContractReader.hostBundleTarget(repositoryRootDirectory)"));
    assertFalse(buildScript.contains("docker/jdeps"));
    assertFalse(buildScript.contains("archiveExtensionForOperatingSystemId"));
    assertFalse(buildScript.contains("launcherPathForOperatingSystemId"));
    assertFalse(buildScript.contains("launcherCommandForOperatingSystemId"));
  }

  @Test
  void cliBuild_generatesBundleManifestFromCanonicalContractMetadata() throws IOException {
    Path repositoryRoot = repositoryRoot();
    String bundleManifest =
        Files.readString(repositoryRoot.resolve("cli/src/bundle/root/bundle-manifest.json"));

    assertTrue(Files.exists(repositoryRoot.resolve("cli/src/bundle/root/bundle-manifest.json")));
    assertTrue(bundleManifest.contains("${helpOperation}"));
    assertTrue(bundleManifest.contains("${capabilitiesOperation}"));
    assertTrue(bundleManifest.contains("${requestTemplateOperation}"));
    assertTrue(bundleManifest.contains("${planTemplateOperation}"));
    assertTrue(bundleManifest.contains("${requiredMinimumSqliteVersion}"));
    assertTrue(bundleManifest.contains("${requiredSqlite3mcVersion}"));
    assertFalse(bundleManifest.contains("\"discoveryCommands\""));
    assertFalse(bundleManifest.contains("\"administrationCommands\""));
    assertFalse(bundleManifest.contains("\"queryCommands\""));
    assertFalse(bundleManifest.contains("\"writeCommands\""));
  }

  @Test
  void repoOwnedAgentMetadata_isTrackedButExcludedFromPublicSourceArchives() throws IOException {
    Path repositoryRoot = repositoryRoot();
    Assumptions.assumeTrue(
        Files.exists(repositoryRoot.resolve(".git")),
        "Git checkout required for repo-owned metadata tracking checks.");
    Assumptions.assumeTrue(
        Files.exists(repositoryRoot.resolve("AGENTS.md"))
            && Files.exists(repositoryRoot.resolve(".codex/UNIVERSAL_ENGINEERING_CONTRACT.md")),
        "Tracked agent metadata must exist in the working checkout.");

    assertEquals(
        1, runCommandAllowFailure(repositoryRoot, "git", "check-ignore", "-q", "AGENTS.md"));
    assertEquals(
        1,
        runCommandAllowFailure(
            repositoryRoot,
            "git",
            "check-ignore",
            "-q",
            ".codex/UNIVERSAL_ENGINEERING_CONTRACT.md"));
    runCommand(repositoryRoot, "git", "cat-file", "-e", "HEAD:AGENTS.md");
    runCommand(
        repositoryRoot, "git", "cat-file", "-e", "HEAD:.codex/UNIVERSAL_ENGINEERING_CONTRACT.md");
  }

  @Test
  void repoOwnedAgentMetadata_isExcludedFromPublicSourceArchives() throws IOException {
    Path repositoryRoot = repositoryRoot();
    Path fixtureRoot = Files.createTempDirectory("fingrind-cli-distribution-contract");
    Path gitRepository = fixtureRoot.resolve("repo");
    Files.createDirectories(gitRepository.resolve(".codex"));

    Files.writeString(
        gitRepository.resolve(".gitignore"),
        Files.readString(repositoryRoot.resolve(".gitignore")),
        StandardCharsets.UTF_8);
    Files.writeString(
        gitRepository.resolve(".gitattributes"),
        Files.readString(repositoryRoot.resolve(".gitattributes")),
        StandardCharsets.UTF_8);
    Files.writeString(gitRepository.resolve("AGENTS.md"), "tracked agent metadata\n");
    Files.writeString(
        gitRepository.resolve(".codex/UNIVERSAL_ENGINEERING_CONTRACT.md"),
        "tracked codex metadata\n");
    Files.writeString(gitRepository.resolve("README.md"), "public archive payload\n");

    runCommand(gitRepository, "git", "init");
    runCommand(gitRepository, "git", "config", "user.name", "FinGrind Test");
    runCommand(gitRepository, "git", "config", "user.email", "fingrind-test@example.com");
    runCommand(gitRepository, "git", "add", ".");
    runCommand(gitRepository, "git", "commit", "-m", "test fixture");

    assertEquals(
        1, runCommandAllowFailure(gitRepository, "git", "check-ignore", "-q", "AGENTS.md"));
    assertEquals(
        1,
        runCommandAllowFailure(
            gitRepository,
            "git",
            "check-ignore",
            "-q",
            ".codex/UNIVERSAL_ENGINEERING_CONTRACT.md"));

    Path archive = fixtureRoot.resolve("source.zip");
    runCommand(
        gitRepository, "git", "archive", "--format=zip", "--output", archive.toString(), "HEAD");
    List<String> archiveEntries = archiveEntries(archive);

    assertTrue(archiveEntries.contains("README.md"));
    assertFalse(archiveEntries.contains("AGENTS.md"));
    assertFalse(
        archiveEntries.stream()
            .anyMatch(entry -> ".codex".equals(entry) || entry.startsWith(".codex/")));
  }

  @Test
  void rootProjectSpotless_noLongerExcludesTrackedCodexMarkdown() throws IOException {
    String buildLogic =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindRootConventionsPlugin.kt"));

    assertTrue(buildLogic.contains("\"**/*.md\""));
    assertFalse(buildLogic.contains("\"**/.codex/**\""));
  }

  @Test
  void bundleAcceptanceScripts_deriveHostFactsFromCanonicalContractReader() throws IOException {
    Path repositoryRoot = repositoryRoot();
    String bashScript = Files.readString(repositoryRoot.resolve("scripts/bundle-smoke.sh"));
    String powerShellScript = Files.readString(repositoryRoot.resolve("scripts/bundle-smoke.ps1"));

    assertTrue(bashScript.contains("bundleLayout"));
    assertFalse(bashScript.contains("expected_native_library_name()"));
    assertFalse(bashScript.contains("host_bundle_classifier()"));

    assertTrue(powerShellScript.contains("bundleLayout.hostBundleTarget"));
    assertFalse(powerShellScript.contains("windows-x86_64.zip"));
    assertFalse(powerShellScript.contains("sqlite3.dll"));
    assertTrue(bashScript.contains("requiredMinimumSqliteVersion"));
    assertTrue(powerShellScript.contains("requiredSqlite3mcVersion"));
  }

  private static Path repositoryRoot() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (!Files.exists(
        Objects.requireNonNull(directory, "directory").resolve("settings.gradle.kts"))) {
      directory = directory.getParent();
    }
    return directory;
  }

  private static List<String> archiveEntries(Path archive) throws IOException {
    List<String> entries = new ArrayList<>();
    try (InputStream inputStream = Files.newInputStream(archive);
        ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
      for (ZipEntry entry = zipInputStream.getNextEntry();
          entry != null;
          entry = zipInputStream.getNextEntry()) {
        entries.add(entry.getName());
      }
    }
    return List.copyOf(entries);
  }

  private static void runCommand(Path workingDirectory, String... command) throws IOException {
    int exitCode = runProcess(workingDirectory, command);
    if (exitCode != 0) {
      throw new IOException(
          "Command failed with exit code " + exitCode + ": " + String.join(" ", command));
    }
  }

  private static int runCommandAllowFailure(Path workingDirectory, String... command)
      throws IOException {
    return runProcess(workingDirectory, command);
  }

  private static int runProcess(Path workingDirectory, String... command) throws IOException {
    Process process =
        new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start();
    try (process;
        InputStream processOutput = process.getInputStream()) {
      processOutput.readAllBytes();
      return process.waitFor();
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new IOException(
          "Interrupted while running command: " + String.join(" ", command), interruptedException);
    }
  }
}
