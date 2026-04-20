package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract-lint tests for repo-owned CLI distribution build assets. */
class CliDistributionBuildContractTest {
  @Test
  void dockerBuild_reusesTheStagedRuntimeModuleList() throws IOException {
    String dockerfile = Files.readString(repositoryRoot().resolve("Dockerfile"));

    assertTrue(
        dockerfile.contains("COPY cli/build/docker/runtime-modules.txt runtime-modules.txt"));
    assertFalse(dockerfile.contains("COPY cli/build/docker/jdeps/ jdeps/"));
    assertFalse(dockerfile.contains("RUN jdeps "));
  }

  @Test
  void cliBuild_stagesSharedRuntimeModulesAndRetainsPdfboxSupportModule() throws IOException {
    String buildScript = Files.readString(repositoryRoot().resolve("cli/build.gradle.kts"));

    assertTrue(buildScript.contains("docker/runtime-modules.txt"));
    assertTrue(buildScript.contains("additionalModules.set(listOf(\"jdk.unsupported\"))"));
    assertFalse(buildScript.contains("docker/jdeps"));
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
    assertFalse(bundleManifest.contains("\"discoveryCommands\""));
    assertFalse(bundleManifest.contains("\"administrationCommands\""));
    assertFalse(bundleManifest.contains("\"queryCommands\""));
    assertFalse(bundleManifest.contains("\"writeCommands\""));
  }

  private static Path repositoryRoot() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (!Files.exists(directory.resolve("settings.gradle.kts"))) {
      directory = directory.getParent();
    }
    return directory;
  }
}
