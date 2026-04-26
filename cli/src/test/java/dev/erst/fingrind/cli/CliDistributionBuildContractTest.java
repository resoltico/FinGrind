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
    String buildScript = Files.readString(repositoryRoot.resolve("cli/build.gradle.kts"));

    assertFalse(Files.exists(repositoryRoot.resolve("cli/src/bundle/root/bundle-manifest.json")));
    assertTrue(
        buildScript.contains("tasks.register<WriteBundleManifestTask>(\"writeBundleManifest\")"));
    assertTrue(
        buildScript.contains(
            "contractFiles.from(DistributionContractReader.requiredContractFiles(repositoryRootDirectory))"));
    assertTrue(buildScript.contains("generated/bundle/root/bundle-manifest.json"));
    assertTrue(buildScript.contains("writeBundleManifest"));
    assertFalse(buildScript.contains("src/bundle/root/bundle-manifest.json"));
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
    String powerShellSupport =
        Files.readString(repositoryRoot.resolve("scripts/bundle-smoke-support.ps1"));
    String powerShellAcceptance =
        Files.readString(repositoryRoot.resolve("scripts/bundle-smoke-acceptance.ps1"));
    String powerShellContract =
        Files.readString(repositoryRoot.resolve("scripts/bundle-smoke-contract.ps1"));

    assertTrue(bashScript.contains("bundleLayout"));
    assertFalse(bashScript.contains("expected_native_library_name()"));
    assertFalse(bashScript.contains("host_bundle_classifier()"));

    assertTrue(powerShellScript.contains("bundle-smoke-support.ps1"));
    assertTrue(powerShellSupport.contains("bundle-smoke-contract.ps1"));
    assertTrue(powerShellAcceptance.contains("bundleLayout.hostBundleTarget"));
    assertFalse(powerShellContract.contains("windows-x86_64.zip"));
    assertFalse(powerShellContract.contains("sqlite3.dll"));
    assertTrue(bashScript.contains("requiredMinimumSqliteVersion"));
    assertTrue(powerShellContract.contains("requiredSqlite3mcVersion"));
  }

  @Test
  void smokeScripts_delegateSharedOfficeWorkerWorkflowThroughPythonOwner() throws IOException {
    Path repositoryRoot = repositoryRoot();
    String bundleSmokeScript = Files.readString(repositoryRoot.resolve("scripts/bundle-smoke.sh"));
    String dockerSmokeScript = Files.readString(repositoryRoot.resolve("scripts/docker-smoke.sh"));
    String bundleOfficeWorker =
        Files.readString(repositoryRoot.resolve("scripts/bundle-smoke-office-worker.ps1"));
    String bundleAcceptance =
        Files.readString(repositoryRoot.resolve("scripts/bundle-smoke-acceptance.ps1"));
    String bundleCommandBridge =
        Files.readString(repositoryRoot.resolve("scripts/bundle-smoke-command-bridge.ps1"));
    String releaseSmokeSupport =
        Files.readString(repositoryRoot.resolve("scripts/release-smoke-support.sh"));
    String releaseSmokeWorkflow =
        Files.readString(repositoryRoot.resolve("scripts/release-smoke-workflow.sh"));
    String releaseSmokeWorkflowPython =
        Files.readString(repositoryRoot.resolve("scripts/release-smoke-workflow.py"));

    assertTrue(bundleSmokeScript.contains("release-smoke-support.sh"));
    assertTrue(dockerSmokeScript.contains("release-smoke-support.sh"));
    assertTrue(bundleSmokeScript.contains("FINGRIND_RELEASE_SMOKE_WORK_ROOT"));
    assertTrue(bundleSmokeScript.contains("FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE"));
    assertTrue(bundleSmokeScript.contains("FINGRIND_RELEASE_SMOKE_SCENARIO_ID"));
    assertTrue(dockerSmokeScript.contains("FINGRIND_RELEASE_SMOKE_WORK_ROOT"));
    assertTrue(dockerSmokeScript.contains("FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE"));
    assertTrue(dockerSmokeScript.contains("FINGRIND_RELEASE_SMOKE_SCENARIO_ID"));
    assertFalse(bundleSmokeScript.contains("FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG"));
    assertFalse(dockerSmokeScript.contains("FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG"));
    assertTrue(releaseSmokeSupport.contains("release-smoke-common.sh"));
    assertTrue(releaseSmokeSupport.contains("release-smoke-workflow.sh"));
    assertFalse(releaseSmokeSupport.contains("release-smoke-fixtures.sh"));
    assertFalse(releaseSmokeSupport.contains("release-smoke-assertions.sh"));
    assertTrue(releaseSmokeWorkflow.contains("release-smoke-workflow.py"));
    assertTrue(releaseSmokeWorkflowPython.contains("release_smoke_workflow.runner import main"));
    assertTrue(bundleOfficeWorker.contains("FINGRIND_RELEASE_SMOKE_WORK_ROOT"));
    assertTrue(bundleOfficeWorker.contains("FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE"));
    assertTrue(bundleOfficeWorker.contains("FINGRIND_RELEASE_SMOKE_SCENARIO_ID"));
    assertTrue(bundleOfficeWorker.contains("FINGRIND_RELEASE_SMOKE_COMMAND_BRIDGE_PREFIX_JSON"));
    assertFalse(bundleOfficeWorker.contains("FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG"));
    assertTrue(bundleOfficeWorker.contains("bundle-smoke-command-bridge.ps1"));
    assertTrue(
        bundleCommandBridge.contains("Get-Content -LiteralPath $RequestPath -Raw -Encoding UTF8"));
    assertTrue(bundleCommandBridge.contains("FINGRIND_BUNDLE_RETURN_EXIT_CODE"));
    assertTrue(bundleCommandBridge.contains("FINGRIND_BUNDLE_ARGUMENTS_FILE"));
    assertTrue(bundleCommandBridge.contains("FINGRIND_BUNDLE_STDIN_FILE"));
    assertTrue(bundleCommandBridge.contains("& $LauncherPath"));
    assertTrue(bundleAcceptance.contains("Rīga büro"));
  }

  @Test
  void windowsBundleLauncher_usesUnicodeSafeNativeArgumentForwarding() throws IOException {
    String powerShellLauncher =
        Files.readString(repositoryRoot().resolve("cli/src/bundle/bin/fingrind.ps1"));

    assertTrue(powerShellLauncher.contains("ProcessStartInfo"));
    assertTrue(powerShellLauncher.contains("ArgumentList.Add"));
    assertTrue(powerShellLauncher.contains("WorkingDirectory"));
    assertTrue(powerShellLauncher.contains("RedirectStandardInput"));
    assertTrue(powerShellLauncher.contains("FINGRIND_BUNDLE_RETURN_EXIT_CODE"));
    assertTrue(powerShellLauncher.contains("FINGRIND_BUNDLE_ARGUMENTS_FILE"));
    assertTrue(powerShellLauncher.contains("FINGRIND_BUNDLE_STDIN_FILE"));
    assertTrue(powerShellLauncher.contains("$PSScriptRoot"));
    assertTrue(powerShellLauncher.contains("$scriptInvocationArguments = @($args)"));
    assertFalse(powerShellLauncher.contains("$MyInvocation.MyCommand.Path"));
    assertFalse(powerShellLauncher.contains("& $runtimeJava @javaArguments"));
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
