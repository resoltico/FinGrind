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

    assertTrue(dockerfile.contains("COPY source-root/ /build/source-root/"));
    assertTrue(
        dockerfile.contains(
            "COPY Dockerfile docker-build-context-manifest.json docker-entrypoint.sh fingrind.jar managed-sqlite-contract.json runtime-modules.txt /build/"));
    assertTrue(
        dockerfile.contains(
            "COPY source-root/scripts/render-managed-sqlite-compiler-flags.py scripts/render-managed-sqlite-compiler-flags.py"));
    assertTrue(
        dockerfile.contains(
            "COPY source-root/scripts/verify-docker-build-context.py scripts/verify-docker-build-context.py"));
    assertTrue(
        dockerfile.contains(
            "COPY source-root/third_party/sqlite/sqlite3mc-amalgamation-2.3.4-sqlite-3530001/ sqlite-source/"));
    assertTrue(dockerfile.contains("contract = json.loads(Path(\"managed-sqlite-contract.json\")"));
    assertTrue(dockerfile.contains("expected_files = contract.get(\"vendoredReleaseFiles\")"));
    assertTrue(dockerfile.contains("source_dir = Path(\"sqlite-source\")"));
    assertTrue(dockerfile.contains("vendored SQLite release manifest drift"));
    assertTrue(
        dockerfile.contains(
            "python3 scripts/verify-docker-build-context.py --context-dir /build --source-root /build/source-root"));
    assertTrue(
        dockerfile.contains(
            "python3 scripts/render-managed-sqlite-compiler-flags.py /build/managed-sqlite-contract.json"));
    assertTrue(dockerfile.contains("sqlite-source/sqlite3mc_amalgamation.c"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/docker-entrypoint.sh /opt/fingrind/bin/docker-entrypoint.sh"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/fingrind.jar /opt/fingrind/lib/app/fingrind.jar"));
    assertTrue(
        dockerfile.contains(
            "COPY source-root/LICENSE source-root/LICENSE-APACHE-2.0 source-root/LICENSE-SIL-OFL-1.1 source-root/LICENSE-SQLITE3MULTIPLECIPHERS source-root/NOTICE source-root/PATENTS.md /opt/fingrind/doc/"));
    assertTrue(dockerfile.contains("sha256sum libsqlite3.so.0 > libsqlite3.so.0.sha256"));
    assertTrue(dockerfile.contains("sha256sum libsqlite3.so.0 > libsqlite3.so.0.trusted.sha256"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/libsqlite3.so.0.sha256 /opt/fingrind/lib/native/libsqlite3.so.0.sha256"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/libsqlite3.so.0.trusted.sha256 /opt/fingrind/lib/native/libsqlite3.so.0.trusted.sha256"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/libsqlite3.so.0 /opt/fingrind/lib/native/libsqlite3.so.0"));
    assertFalse(dockerfile.contains("ENV FINGRIND_SQLITE_LIBRARY="));
    assertFalse(dockerfile.contains("COPY cli/build/docker-context/ /build/docker-context/"));
    assertFalse(dockerfile.contains("COPY gradle.properties /build/source-root/gradle.properties"));
    assertFalse(dockerfile.contains("RUN jdeps "));
  }

  @Test
  void dockerBuildContext_isExposedToDockerThroughDockerignore() throws IOException {
    String dockerignore = Files.readString(repositoryRoot().resolve(".dockerignore"));

    assertTrue(dockerignore.contains("`./gradlew :cli:stageDockerBuildContext`"));
    assertTrue(
        dockerignore.contains("Repository-root `docker build .` is intentionally unsupported"));
    assertTrue(dockerignore.contains("**"));
    assertFalse(dockerignore.contains("!cli/build/docker-context/"));
    assertFalse(dockerignore.contains("!gradle/build-logic/**"));
  }

  @Test
  void cliAndSqliteBuilds_stageManagedRuntimeIdentityFromOneCanonicalOwner() throws IOException {
    String buildScript = Files.readString(repositoryRoot().resolve("cli/build.gradle.kts"));
    String sqliteBuildScript =
        Files.readString(repositoryRoot().resolve("sqlite/build.gradle.kts"));

    assertTrue(buildScript.contains("stageDockerBuildContext"));
    assertTrue(buildScript.contains("docker-context"));
    assertTrue(buildScript.contains("docker-build-context-manifest.json"));
    assertTrue(buildScript.contains("dockerBuildContextSourceIncludePatterns"));
    assertTrue(buildScript.contains("dockerManagedSqliteContractSource"));
    assertTrue(buildScript.contains("dockerBuildContextSourceInputs"));
    assertTrue(buildScript.contains("repositoryDockerBuildContextDirectory"));
    assertTrue(
        buildScript.contains("\"bundleHomeSystemProperty\" to sqliteBundleHomeSystemProperty"));
    assertTrue(buildScript.contains("managedSqliteLibrarySha256Path"));
    assertTrue(buildScript.contains("managedSqliteLibraryTrustedSha256Path"));
    assertTrue(buildScript.contains("managedSqliteToolchainFingerprintPath"));
    assertTrue(buildScript.contains("repositoryRootPath.set(repositoryRootDirectory.toString())"));
    assertTrue(buildScript.contains("sourceFiles.from(dockerBuildContextSourceInputs)"));
    assertTrue(
        buildScript.contains("from(rootProject.layout.projectDirectory.file(\"Dockerfile\"))"));
    assertTrue(buildScript.contains("into(\"source-root\")"));
    assertTrue(buildScript.contains("tasks.register<Sync>(\"syncRepositoryDockerBuildContext\")"));
    assertTrue(buildScript.contains("finalizedBy(syncRepositoryDockerBuildContext)"));
    assertTrue(buildScript.contains("tasks.named<ProcessResources>(\"processResources\")"));
    assertTrue(
        buildScript.contains("dependsOn(rootProject.tasks.named(\"prepareManagedSqlite\"))"));
    assertTrue(buildScript.contains("additionalModules.set(listOf(\"jdk.unsupported\"))"));
    assertTrue(
        buildScript.contains(
            "DistributionContractReader.hostBundleTarget(repositoryRootDirectory)"));
    assertTrue(
        buildScript.contains(
            "rootProject.layout.projectDirectory.file(\"gradle/build-logic/build.gradle.kts\")"));
    assertTrue(
        buildScript.contains(
            "rootProject.layout.projectDirectory.dir(\"gradle/build-logic/src/main\")"));
    assertFalse(
        buildScript.contains(
            "inputs.dir(rootProject.layout.projectDirectory.dir(\"gradle/build-logic\"))"));
    assertFalse(buildScript.contains("stageDockerRuntimeInputs"));
    assertFalse(buildScript.contains("docker/jdeps"));
    assertFalse(buildScript.contains("archiveExtensionForOperatingSystemId"));
    assertFalse(buildScript.contains("launcherPathForOperatingSystemId"));
    assertFalse(buildScript.contains("launcherCommandForOperatingSystemId"));

    assertTrue(sqliteBuildScript.contains("tasks.named<ProcessResources>(\"processResources\")"));
    assertTrue(sqliteBuildScript.contains("META-INF/fingrind"));
    assertTrue(sqliteBuildScript.contains("managed-sqlite-toolchain.json"));
    assertFalse(sqliteBuildScript.contains("managed-sqlite.sha256"));
  }

  @Test
  void cliBuild_configuresSourceCheckoutLauncherWithManagedRuntimeDefaults() throws IOException {
    String buildScript = Files.readString(repositoryRoot().resolve("cli/build.gradle.kts"));

    assertTrue(
        buildScript.indexOf("val sourceCheckoutRuntimeDistribution =")
            < buildScript.indexOf("application {"));
    assertTrue(buildScript.contains("applicationDefaultJvmArgs"));
    assertTrue(
        buildScript.contains(
            "\"-Dfingrind.runtime.distribution=${sourceCheckoutRuntimeDistribution}\""));
    assertTrue(
        buildScript.contains("\"-Dfingrind.source-checkout.root=${repositoryRootDirectory}\""));
    assertTrue(
        buildScript.contains(
            "\"-Dfingrind.source-checkout.build-root=${sourceCheckoutBuildRootDirectory}\""));
    assertTrue(buildScript.contains("tasks.named<JavaExec>(\"run\")"));
    assertFalse(buildScript.contains("jvmArgs(\"-Dfingrind.runtime.distribution="));
  }

  @Test
  void cliBuild_stampsTheDeveloperJarWithAutomaticModuleNameAndCheckoutRootMetadata()
      throws IOException {
    String buildScript = Files.readString(repositoryRoot().resolve("cli/build.gradle.kts"));

    assertTrue(buildScript.contains("\"Automatic-Module-Name\" to \"fingrind\""));
    assertFalse(buildScript.contains("\"Enable-Native-Access\" to \"ALL-UNNAMED\""));
    assertTrue(
        buildScript.contains(
            "\"FinGrind-Source-Checkout-Root\" to repositoryRootDirectory.toString()"));
    assertTrue(
        buildScript.contains(
            "\"FinGrind-Source-Checkout-Build-Root\" to sourceCheckoutBuildRootDirectory.toString()"));
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
  void cliBuild_prunesStaleVersionedBundleRootsArchivesAndChecksumsBeforeStagingTheCurrentBundle()
      throws IOException {
    Path repositoryRoot = repositoryRoot();
    String buildScript = Files.readString(repositoryRoot.resolve("cli/build.gradle.kts"));
    String pruneTask =
        Files.readString(
            repositoryRoot.resolve(
                "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/PruneBundleOutputsTask.kt"));
    String reportTask =
        Files.readString(
            repositoryRoot.resolve(
                "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/ReportBundleArchiveOutputsTask.kt"));

    assertTrue(
        buildScript.contains("tasks.register<PruneBundleOutputsTask>(\"cleanBundleOutputs\")"));
    assertTrue(
        buildScript.contains(
            "Deletes staged self-contained FinGrind CLI bundle directories plus prior bundle archives and checksum files."));
    assertTrue(buildScript.contains("artifactPrefix.set(\"fingrind-\")"));
    assertTrue(
        buildScript.contains(
            "legacyBundleWorkspaceDirectory.set(layout.projectDirectory.dir(\"build/bundle\"))"));
    assertTrue(
        buildScript.contains(
            "legacyDistributionDirectory.set(layout.projectDirectory.dir(\"build/distributions\"))"));
    assertTrue(
        pruneTask.contains(
            "deletePrefixedEntries(bundleWorkspaceDirectory.asFile.orNull, prefix)"));
    assertTrue(
        pruneTask.contains("deletePrefixedEntries(distributionDirectory.asFile.orNull, prefix)"));
    assertTrue(
        pruneTask.contains(
            "deletePrefixedEntries(legacyBundleWorkspaceDirectory.asFile.orNull, prefix)"));
    assertTrue(
        pruneTask.contains(
            "deletePrefixedEntries(legacyDistributionDirectory.asFile.orNull, prefix)"));
    assertTrue(
        pruneTask.contains(".filter { entry -> entry.fileName.toString().startsWith(prefix) }"));
    assertTrue(buildScript.contains("dependsOn(cleanBundleOutputs)"));
    assertTrue(
        buildScript.contains(
            "tasks.register<ReportBundleArchiveOutputsTask>(\"bundleCliArchive\")"));
    assertTrue(reportTask.contains("FINGRIND_BUNDLE_ARCHIVE="));
    assertTrue(reportTask.contains("FINGRIND_BUNDLE_CHECKSUM="));
    assertFalse(buildScript.contains("cleanBundleRoot"));
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

    assertTrue(bashScript.contains("bundleLayout"));
    assertFalse(bashScript.contains("expected_native_library_name()"));
    assertFalse(bashScript.contains("host_bundle_classifier()"));

    assertTrue(powerShellScript.contains("bundle-smoke-support.ps1"));
    assertFalse(powerShellSupport.contains("bundle-smoke-contract.ps1"));
    assertTrue(powerShellAcceptance.contains("bundleLayout.hostBundleTarget"));
    assertTrue(bashScript.contains("verify-bundle-archive-contract.py"));
    assertTrue(powerShellAcceptance.contains("verify-bundle-archive-contract.py"));
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
    String releaseSmokeCommon =
        Files.readString(repositoryRoot.resolve("scripts/release-smoke-common.sh"));
    String releaseSmokeWorkflowSupport =
        Files.readString(repositoryRoot.resolve("scripts/release-smoke-workflow-support.sh"));
    String releaseSmokeWorkflowPython =
        Files.readString(repositoryRoot.resolve("scripts/release-smoke-workflow.py"));

    assertTrue(bundleSmokeScript.contains("release-smoke-support.sh"));
    assertTrue(dockerSmokeScript.contains("release-smoke-support.sh"));
    assertTrue(bundleSmokeScript.contains("FINGRIND_RELEASE_SMOKE_WORK_ROOT"));
    assertTrue(bundleSmokeScript.contains("FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE"));
    assertTrue(bundleSmokeScript.contains("FINGRIND_RELEASE_SMOKE_SCENARIO_ID"));
    assertTrue(bundleSmokeScript.contains("verify-bundle-archive-contract.py"));
    assertTrue(dockerSmokeScript.contains("FINGRIND_RELEASE_SMOKE_WORK_ROOT"));
    assertTrue(dockerSmokeScript.contains("FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE"));
    assertTrue(dockerSmokeScript.contains("FINGRIND_RELEASE_SMOKE_SCENARIO_ID"));
    assertTrue(dockerSmokeScript.contains("verify-docker-build-context.py"));
    assertTrue(dockerSmokeScript.contains("--source-root \"${repo_root}\""));
    assertTrue(dockerSmokeScript.contains(":cli:stageDockerBuildContext"));
    assertTrue(
        dockerSmokeScript.contains(
            "docker_with_repo_config buildx build --load -t \"${image_tag}\" \"${cli_docker_context_dir}\""));
    assertFalse(
        dockerSmokeScript.contains(
            "staging relocated Docker build context into repository context"));
    assertFalse(
        dockerSmokeScript.contains(
            "cp -R \"${cli_docker_context_dir}\" \"${repo_cli_docker_context_dir}\""));
    assertFalse(bundleSmokeScript.contains("FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG"));
    assertFalse(dockerSmokeScript.contains("FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG"));
    assertFalse(dockerSmokeScript.contains(":cli:shadowJar"));
    assertFalse(dockerSmokeScript.contains("\"${repo_root}\" >/dev/null"));
    assertTrue(releaseSmokeSupport.contains("release-smoke-common.sh"));
    assertTrue(releaseSmokeSupport.contains("release-smoke-workflow-support.sh"));
    assertFalse(releaseSmokeSupport.contains("release-smoke-fixtures.sh"));
    assertFalse(releaseSmokeSupport.contains("release-smoke-assertions.sh"));
    assertTrue(releaseSmokeCommon.contains("must be sourced"));
    assertTrue(releaseSmokeSupport.contains("must be sourced"));
    assertTrue(releaseSmokeWorkflowSupport.contains("release-smoke-workflow.py"));
    assertTrue(releaseSmokeWorkflowSupport.contains("must be sourced"));
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
    assertTrue(powerShellLauncher.contains("FINGRIND_LAUNCHER_ARGUMENTS_FILE"));
    assertTrue(powerShellLauncher.contains("FINGRIND_BUNDLE_STDIN_FILE"));
    assertTrue(powerShellLauncher.contains("$PSScriptRoot"));
    assertTrue(powerShellLauncher.contains("$scriptInvocationArguments = @($args)"));
    assertFalse(powerShellLauncher.contains("$MyInvocation.MyCommand.Path"));
    assertFalse(powerShellLauncher.contains("& $runtimeJava @javaArguments"));
    assertFalse(powerShellLauncher.contains("ConvertFrom-Json"));
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
