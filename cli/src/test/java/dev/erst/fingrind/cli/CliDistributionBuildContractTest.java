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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Contract-lint tests for repo-owned CLI distribution build assets. */
class CliDistributionBuildContractTest {
  @Test
  void dockerBuild_reusesTheStagedRuntimeModuleList() throws IOException {
    String dockerEnvironment =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/DockerManagedSqliteBuildEnvironment.kt"));
    String dockerfile = Files.readString(repositoryRoot().resolve("Dockerfile"));
    String builderImage = kotlinStringConstant(dockerEnvironment, "builderImage");
    String runtimeImage = kotlinStringConstant(dockerEnvironment, "runtimeImage");
    String builderBinutilsPackage =
        kotlinStringConstant(dockerEnvironment, "builderBinutilsPackage");
    String builderPythonPackage = kotlinStringConstant(dockerEnvironment, "pythonPackage");
    String runtimeLibStdCppPackage =
        kotlinStringConstant(dockerEnvironment, "runtimeLibStdCppPackage");

    assertTrue(dockerfile.contains("FROM " + builderImage + " AS builder"));
    assertTrue(
        dockerfile.contains(
            "RUN apk add --no-cache " + builderPythonPackage + " " + builderBinutilsPackage));
    assertTrue(dockerfile.contains("COPY source-root/ /build/source-root/"));
    assertTrue(
        dockerfile.contains(
            "COPY Dockerfile docker-build-context-manifest.json docker-entrypoint.sh fingrind.jar native-sqlite-format-boundary-probe.jar runtime-modules.txt /build/"));
    assertTrue(
        dockerfile.contains(
            "COPY libsqlite3.so.0 libsqlite3.so.0.sha256 toolchain-fingerprint.json build-contract.json /build/"));
    assertTrue(
        dockerfile.contains(
            "COPY source-root/scripts/verify-docker-build-context.py scripts/verify-docker-build-context.py"));
    assertTrue(
        dockerfile.contains(
            "python3 scripts/verify-docker-build-context.py --context-dir /build --source-root /build/source-root"));
    assertTrue(dockerfile.contains("missing staged managed SQLite artifact"));
    assertTrue(dockerfile.contains("managed SQLite checksum file declared"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/docker-entrypoint.sh /opt/fingrind/bin/docker-entrypoint.sh"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/fingrind.jar /opt/fingrind/lib/app/fingrind.jar"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/native-sqlite-format-boundary-probe.jar /opt/fingrind/lib/release-smoke/native-sqlite-format-boundary-probe.jar"));
    assertTrue(
        dockerfile.contains(
            "COPY source-root/LICENSE source-root/LICENSE-APACHE-2.0 source-root/LICENSE-SIL-OFL-1.1 source-root/LICENSE-SQLITE3MULTIPLECIPHERS source-root/NOTICE source-root/PATENTS.md /opt/fingrind/doc/"));
    assertFalse(dockerfile.contains("sha256sum libsqlite3.so.0 > libsqlite3.so.0.sha256"));
    assertTrue(dockerfile.contains("FROM " + runtimeImage));
    assertTrue(dockerfile.contains("RUN apk add --no-cache " + runtimeLibStdCppPackage));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/libsqlite3.so.0.sha256 /opt/fingrind/lib/native/libsqlite3.so.0.sha256"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/libsqlite3.so.0 /opt/fingrind/lib/native/libsqlite3.so.0"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/toolchain-fingerprint.json /opt/fingrind/lib/native/toolchain-fingerprint.json"));
    assertTrue(
        dockerfile.contains(
            "COPY --from=builder /build/build-contract.json /opt/fingrind/lib/native/build-contract.json"));
    assertFalse(dockerfile.contains("ENV FINGRIND_SQLITE_LIBRARY="));
    assertFalse(dockerfile.contains("COPY cli/build/docker-context/ /build/docker-context/"));
    assertFalse(dockerfile.contains("managed-sqlite-contract.json"));
    assertFalse(dockerfile.contains("render-managed-sqlite-compiler-flags.py"));
    assertFalse(dockerfile.contains("sqlite-source/"));
    assertFalse(dockerfile.contains("COPY gradle.properties /build/source-root/gradle.properties"));
    assertFalse(dockerfile.contains("RUN cc "));
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
    String distributionPlugin =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindCliDistributionPlugin.kt"));
    String runtimeModuleDiscoveryContract =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-module-discovery-contract.json"));
    String executionSurfaceConventions =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindCliExecutionSurfaceConventions.kt"));
    String dockerContextRegistration =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/CliDistributionDockerContextRegistration.kt"));
    String pruneLegacyDockerBuildContextTask =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/PruneLegacyDockerBuildContextTask.kt"));
    String sqliteBuildScript =
        Files.readString(repositoryRoot().resolve("sqlite/build.gradle.kts"));
    String managedSqliteProvisioning =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/ManagedSqliteProvisioningLogic.kt"));
    String managedSqliteConsumerPlugin =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindManagedSqliteConsumerPlugin.kt"));
    String rootConventionsPlugin =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindRootConventionsPlugin.kt"));

    assertCliBuildScriptDelegatesDistribution(buildScript);
    assertDistributionPluginOwnsDistributionContracts(
        distributionPlugin,
        runtimeModuleDiscoveryContract,
        executionSurfaceConventions,
        dockerContextRegistration,
        pruneLegacyDockerBuildContextTask);
    assertManagedSqliteProvisioningUsesNamedModuleAccess(
        managedSqliteProvisioning, managedSqliteConsumerPlugin, rootConventionsPlugin);
    assertSqliteBuildScriptOwnsWhiteBoxModulePatch(sqliteBuildScript);
  }

  private static void assertCliBuildScriptDelegatesDistribution(String buildScript) {
    assertTrue(buildScript.contains("id(\"dev.erst.fingrind.cli-distribution\")"));
    assertTrue(buildScript.contains("id(\"dev.erst.fingrind.managed-sqlite-consumer\")"));
    assertTrue(buildScript.contains("duplicatesStrategy = DuplicatesStrategy.INCLUDE"));
    assertTrue(buildScript.contains("mergeServiceFiles()"));
    assertFalse(buildScript.contains("stageDockerBuildContext"));
    assertFalse(buildScript.contains("docker-build-context-manifest.json"));
    assertFalse(buildScript.contains("source-checkout-artifact-manifest.tsv"));
  }

  private static void assertDistributionPluginOwnsDistributionContracts(
      String distributionPlugin,
      String runtimeModuleDiscoveryContract,
      String executionSurfaceConventions,
      String dockerContextRegistration,
      String pruneLegacyDockerBuildContextTask) {
    assertTrue(
        distributionPlugin.contains(
            "configureCliExecutionSurfaceConventions(cliContractBuildLogicInputs)"));
    assertTrue(distributionPlugin.contains("stageDockerBuildContext"));
    assertTrue(distributionPlugin.contains("docker-context"));
    assertTrue(distributionPlugin.contains("docker-build-context-manifest.json"));
    assertFalse(distributionPlugin.contains("source-checkout-artifact-manifest.tsv"));
    assertTrue(distributionPlugin.contains("source-checkout-runtime-manifest.tsv"));
    assertTrue(distributionPlugin.contains("JavaToolchainService"));
    assertTrue(distributionPlugin.contains("launcherFor"));
    assertTrue(distributionPlugin.contains("metadata.installationPath"));
    assertTrue(distributionPlugin.contains("writeSourceCheckoutRuntimeManifest"));
    assertTrue(
        distributionPlugin.contains(
            "CliDistributionSourceInventory.dockerBuildContextSourceFiles"));
    assertTrue(
        distributionPlugin.contains("CliDistributionSourceInventory.dockerBuildContextFiles"));
    assertTrue(distributionPlugin.contains("dockerManagedSqliteContractSource"));
    assertTrue(distributionPlugin.contains("dockerBuildContextSourceInputs"));
    assertTrue(
        distributionPlugin.contains("ManagedSqliteProvisioningRegistry.require(rootProject)"));
    assertTrue(
        distributionPlugin.contains("ManagedSqliteProvisioningLogic.registerDockerContextTarget"));
    assertTrue(distributionPlugin.contains("managedSqliteProvisioning = dockerManagedSqlite"));
    assertTrue(distributionPlugin.contains("bundleArchiveManifestOutputFile"));
    assertTrue(distributionPlugin.contains("bundleArchiveTasks.manifestTask"));
    assertTrue(
        distributionPlugin.contains(
            "\"sqliteBundleHomeSystemProperty\" to sqliteBundleHomeSystemProperty"));
    assertTrue(distributionPlugin.contains("\"tokens\" to bundleTemplateProperties"));
    assertTrue(distributionPlugin.contains("dependsOn(shadowJarTask)"));
    assertTrue(
        distributionPlugin.contains(
            "exclude(\"org/apache/commons/logging/impl/ServletContextCleaner.class\")"));
    assertTrue(
        distributionPlugin.contains(
            "exclude(\"org/apache/commons/logging/jakarta/ServletContextCleaner.class\")"));
    assertFalse(runtimeModuleDiscoveryContract.contains("javax.servlet."));
    assertFalse(runtimeModuleDiscoveryContract.contains("jakarta.servlet."));
    assertTrue(distributionPlugin.contains("finalizedBy(writeSourceCheckoutRuntimeManifest)"));
    assertTrue(distributionPlugin.contains("javaExecutable.set(sourceCheckoutJavaLauncher.map"));
    assertTrue(distributionPlugin.contains("javaInstallationDirectory.set("));
    assertTrue(distributionPlugin.contains("additionalModules.set(listOf(\"jdk.unsupported\"))"));
    assertTrue(
        distributionPlugin.contains(
            "DistributionBundleTargetReader.hostBundleTarget(repositoryRootDirectory)"));
    assertTrue(
        distributionPlugin.contains(
            "rootProject.layout.projectDirectory.file(\"gradle/build-logic/build.gradle.kts\")"));
    assertTrue(
        distributionPlugin.contains(
            "rootProject.layout.projectDirectory.dir(\"gradle/build-logic/src/main\")"));
    assertFalse(
        distributionPlugin.contains(
            "inputs.dir(rootProject.layout.projectDirectory.dir(\"gradle/build-logic\"))"));
    assertFalse(distributionPlugin.contains("dockerBuildContextSourceIncludePatterns"));
    assertFalse(distributionPlugin.contains("stageDockerRuntimeInputs"));
    assertFalse(distributionPlugin.contains("docker/jdeps"));
    assertFalse(distributionPlugin.contains("archiveExtensionForOperatingSystemId"));
    assertFalse(distributionPlugin.contains("System.getProperty(\"java.home\")"));
    assertFalse(distributionPlugin.contains("launcherPathForOperatingSystemId"));
    assertFalse(distributionPlugin.contains("launcherCommandForOperatingSystemId"));
    assertFalse(distributionPlugin.contains("managedSqliteHostClassifier("));
    assertFalse(distributionPlugin.contains("managedSqliteLibraryFileNameForHost("));
    assertTrue(
        executionSurfaceConventions.contains(
            "tasks.named<ProcessResources>(\"processResources\")"));
    assertTrue(
        executionSurfaceConventions.contains(
            "dependsOn(rootProject.tasks.named(\"prepareManagedSqlite\"))"));
    assertTrue(executionSurfaceConventions.contains("disableLegacyCliDistributionTasks()"));
    assertTrue(
        dockerContextRegistration.contains("sourceFiles.from(dockerBuildContextSourceInputs)"));
    assertTrue(
        dockerContextRegistration.contains("dependsOn(managedSqliteProvisioning.prepareTask)"));
    assertTrue(dockerContextRegistration.contains("pruneLegacyCheckoutDockerContext"));
    assertTrue(dockerContextRegistration.contains("dependsOn(pruneLegacyCheckoutDockerContext)"));
    assertTrue(
        dockerContextRegistration.contains(
            "layout.projectDirectory.dir(\"build/docker-context\")"));
    assertTrue(pruneLegacyDockerBuildContextTask.contains("legacy-do-not-use-"));
    assertTrue(
        pruneLegacyDockerBuildContextTask.contains(
            "failed to quarantine stale legacy Docker build context"));
    assertTrue(
        dockerContextRegistration.contains(
            "from(rootProject.layout.projectDirectory.file(\"Dockerfile\"))"));
    assertTrue(dockerContextRegistration.contains("into(\"source-root\")"));
    assertTrue(dockerContextRegistration.contains("from(managedSqliteProvisioning.libraryPath)"));
    assertTrue(dockerContextRegistration.contains("from(managedSqliteProvisioning.checksumPath)"));
    assertTrue(
        dockerContextRegistration.contains(
            "from(managedSqliteProvisioning.toolchainFingerprintPath)"));
    assertTrue(
        dockerContextRegistration.contains("from(managedSqliteProvisioning.buildContractPath)"));
  }

  private static String kotlinStringConstant(String kotlinSource, String constantName) {
    Matcher matcher =
        Pattern.compile(
                "const val " + Pattern.quote(constantName) + "\\s*=\\s*\"([^\"]+)\"",
                Pattern.MULTILINE)
            .matcher(kotlinSource);
    assertTrue(matcher.find(), "Expected Kotlin constant " + constantName + " to exist.");
    return matcher.group(1);
  }

  private static void assertManagedSqliteProvisioningUsesNamedModuleAccess(
      String managedSqliteProvisioning,
      String managedSqliteConsumerPlugin,
      String rootConventionsPlugin) {
    assertFalse(managedSqliteProvisioning.contains("enableUnnamedNativeAccess()"));
    assertTrue(managedSqliteProvisioning.contains("enableSqliteNamedNativeAccess()"));
    assertTrue(managedSqliteProvisioning.contains("useModulePath(mainRuntimeModulePath)"));
    assertTrue(managedSqliteProvisioning.contains("addSqliteNamedModule()"));
    assertTrue(managedSqliteProvisioning.contains("if (mainModule.orNull == null)"));
    assertTrue(
        managedSqliteProvisioning.contains(
            "systemProperty(\"fingrind.runtime.distribution\", sourceCheckoutRuntimeDistribution)"));
    assertTrue(
        managedSqliteProvisioning.contains(
            "systemProperty(\"fingrind.source-checkout.root\", repositoryRoot.toString())"));
    assertTrue(
        managedSqliteProvisioning.contains(
            "systemProperty(\"fingrind.source-checkout.build-root\", sourceCheckoutBuildRoot.toString())"));
    assertTrue(managedSqliteConsumerPlugin.contains("ManagedSqliteProvisioningRegistry.require"));
    assertTrue(
        managedSqliteConsumerPlugin.contains("ManagedSqliteProvisioningLogic.configureConsumers"));
    assertTrue(
        rootConventionsPlugin.contains(
            "ManagedSqliteProvisioningRegistry.publish(this, managedSqlite)"));
    assertFalse(rootConventionsPlugin.contains("requiresManagedSqliteRuntime()"));
  }

  private static void assertSqliteBuildScriptOwnsWhiteBoxModulePatch(String sqliteBuildScript) {
    assertTrue(sqliteBuildScript.contains("id(\"dev.erst.fingrind.managed-sqlite-consumer\")"));
    assertTrue(sqliteBuildScript.contains("tasks.named<ProcessResources>(\"processResources\")"));
    assertTrue(
        sqliteBuildScript.contains(
            "patchModule(\"dev.erst.fingrind.sqlite\", sqliteWhiteBoxTestPatchPath)"));
    assertTrue(
        sqliteBuildScript.contains("addReads(\"dev.erst.fingrind.sqlite\", \"ALL-UNNAMED\")"));
    assertTrue(
        sqliteBuildScript.contains(
            "addOpens(\"dev.erst.fingrind.sqlite\", \"dev.erst.fingrind.sqlite\", \"ALL-UNNAMED\")"));
    assertTrue(sqliteBuildScript.contains("META-INF/fingrind"));
    assertTrue(sqliteBuildScript.contains("managed-sqlite-toolchain.json"));
    assertTrue(sqliteBuildScript.contains("managed-sqlite-build-contract.json"));
    assertFalse(sqliteBuildScript.contains("managed-sqlite.sha256"));
  }

  @Test
  void cliBuild_configuresSourceCheckoutLauncherWithManagedRuntimeDefaults() throws IOException {
    String buildScript = Files.readString(repositoryRoot().resolve("cli/build.gradle.kts"));
    String managedSqliteProvisioning =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/ManagedSqliteProvisioningLogic.kt"));

    assertFalse(buildScript.contains("tasks.named<JavaExec>(\"run\")"));
    assertFalse(buildScript.contains("applicationDefaultJvmArgs"));
    assertFalse(buildScript.contains("fingrind.runtime.distribution"));
    assertFalse(buildScript.contains("fingrind.source-checkout.root"));
    assertFalse(buildScript.contains("fingrind.source-checkout.build-root"));
    assertTrue(managedSqliteProvisioning.contains("if (mainModule.orNull == null)"));
    assertTrue(
        managedSqliteProvisioning.contains(
            "systemProperty(\"fingrind.runtime.distribution\", sourceCheckoutRuntimeDistribution)"));
  }

  @Test
  void sourceCheckoutWrappers_prepareAndLaunchOnePreparedRuntimeSurface() throws IOException {
    Path repositoryRoot = repositoryRoot();
    String shellSupport =
        Files.readString(repositoryRoot.resolve("scripts/gradle-wrapper-support.sh"));
    String powerShellSupport =
        Files.readString(repositoryRoot.resolve("scripts/gradle-wrapper-support.ps1"));
    String sourceCheckoutShell =
        Files.readString(repositoryRoot.resolve("scripts/source-checkout-cli.sh"));
    String directJavaShell = Files.readString(repositoryRoot.resolve("scripts/direct-java-cli.sh"));
    String shellWrapperEntrypoint =
        Files.readString(repositoryRoot.resolve("scripts/source-checkout-cli-entrypoint.sh"));
    String shellWrapperCommon =
        Files.readString(repositoryRoot.resolve("scripts/source-checkout-cli-common.sh"));
    String powerShellWrapperCommon =
        Files.readString(repositoryRoot.resolve("scripts/source-checkout-cli-common.ps1"));
    String sourceCheckoutPowerShell =
        Files.readString(repositoryRoot.resolve("scripts/source-checkout-cli.ps1"));
    String directJavaPowerShell =
        Files.readString(repositoryRoot.resolve("scripts/direct-java-cli.ps1"));

    assertTrue(shellSupport.contains("fg_gradle_source_checkout_runtime_manifest_path()"));
    assertTrue(powerShellSupport.contains("Get-FinGrindSourceCheckoutRuntimeManifestPath"));
    assertTrue(sourceCheckoutShell.contains("source-checkout-cli-entrypoint.sh"));
    assertTrue(directJavaShell.contains("source-checkout-cli-entrypoint.sh"));
    assertTrue(shellWrapperEntrypoint.contains("source-checkout-cli-common.sh"));
    assertTrue(shellWrapperCommon.contains("fg_gradle_source_checkout_runtime_manifest_path"));
    assertTrue(shellWrapperCommon.contains("fg_cli_wrapper_load_runtime_manifest"));
    assertTrue(shellWrapperCommon.contains("fg_cli_wrapper_prepare_runtime_if_needed"));
    assertTrue(shellWrapperCommon.contains("fg_cli_wrapper_runtime_inputs_are_fresh"));
    assertTrue(shellWrapperCommon.contains("runtimeInputPath"));
    assertTrue(
        shellWrapperCommon.contains(
            "--add-opens=java.base/java.nio=${fg_cli_wrapper_application_module%%/*}"));
    assertTrue(
        shellWrapperCommon.contains(
            "--add-exports=java.base/sun.nio=${fg_cli_wrapper_application_module%%/*}"));
    assertTrue(shellWrapperCommon.contains("./gradlew :cli:prepareSourceCheckoutCliRuntime"));
    assertTrue(
        shellWrapperCommon.contains(
            "./gradlew :cli:prepareSourceCheckoutCliRuntime --quiet >/dev/null 2>&1"));
    assertTrue(
        shellWrapperCommon.contains(
            "./gradlew :cli:prepareSourceCheckoutCliRuntime --rerun-tasks --quiet >/dev/null 2>&1"));
    assertTrue(powerShellWrapperCommon.contains("Invoke-FinGrindEnsureCliWrapperRuntime"));
    assertTrue(powerShellWrapperCommon.contains("Read-FinGrindSourceCheckoutRuntimeManifest"));
    assertTrue(powerShellWrapperCommon.contains("Test-FinGrindCliWrapperRuntimeFreshness"));
    assertTrue(powerShellWrapperCommon.contains("runtimeInputPath"));
    assertTrue(
        powerShellWrapperCommon.contains(
            "\"--add-opens=java.base/java.nio=$($RuntimeManifest.ApplicationModule.Split('/', 2)[0])\""));
    assertTrue(
        powerShellWrapperCommon.contains(
            "\"--add-exports=java.base/sun.nio=$($RuntimeManifest.ApplicationModule.Split('/', 2)[0])\""));
    assertTrue(powerShellWrapperCommon.contains("repo-locks/cli-runtime-prepare.lock"));
    assertTrue(powerShellWrapperCommon.contains("New-Item -ItemType Directory"));
    assertTrue(sourceCheckoutPowerShell.contains("source-checkout-cli-common.ps1"));
    assertTrue(directJavaPowerShell.contains("source-checkout-cli-common.ps1"));
  }

  @Test
  void cliBuild_stampsTheDeveloperJarWithAutomaticModuleNameAndCheckoutRootMetadata()
      throws IOException {
    String buildScript = Files.readString(repositoryRoot().resolve("cli/build.gradle.kts"));

    assertTrue(buildScript.contains("\"Automatic-Module-Name\" to \"dev.erst.fingrind.cli\""));
    assertFalse(buildScript.contains("\"Enable-Native-Access\" to \"ALL-UNNAMED\""));
    assertFalse(buildScript.contains("FinGrind-Source-Checkout-Root"));
    assertFalse(buildScript.contains("FinGrind-Source-Checkout-Build-Root"));
  }

  @Test
  void publishedLaunchers_targetTheCanonicalAutomaticModuleIdentity() throws IOException {
    Path repositoryRoot = repositoryRoot();
    String buildScript = Files.readString(repositoryRoot.resolve("cli/build.gradle.kts"));
    String posixBundleLauncher =
        Files.readString(repositoryRoot.resolve("cli/src/bundle/bin/fingrind"));
    String powerShellBundleLauncher =
        Files.readString(repositoryRoot.resolve("cli/src/bundle/bin/fingrind.ps1"));
    String dockerEntrypoint =
        Files.readString(repositoryRoot.resolve("cli/src/docker/docker-entrypoint.sh"));

    assertTrue(buildScript.contains("\"Automatic-Module-Name\" to \"dev.erst.fingrind.cli\""));
    assertTrue(
        posixBundleLauncher.contains(
            "application_module='dev.erst.fingrind.cli/dev.erst.fingrind.cli.App'"));
    assertTrue(posixBundleLauncher.contains("--enable-native-access=dev.erst.fingrind.cli"));
    assertTrue(
        posixBundleLauncher.contains("--add-opens=java.base/java.nio=dev.erst.fingrind.cli"));
    assertTrue(
        posixBundleLauncher.contains("--add-exports=java.base/sun.nio=dev.erst.fingrind.cli"));
    assertTrue(
        posixBundleLauncher.contains("-D{{sqliteBundleHomeSystemProperty}}=\"${app_home}\""));
    assertTrue(
        posixBundleLauncher.contains("-Dfingrind.runtime.bundle-target={{bundleClassifier}}"));
    assertTrue(posixBundleLauncher.contains("--module \"${application_module}\""));
    assertTrue(
        powerShellBundleLauncher.contains(
            "$applicationModule = \"dev.erst.fingrind.cli/dev.erst.fingrind.cli.App\""));
    assertTrue(powerShellBundleLauncher.contains("--enable-native-access=dev.erst.fingrind.cli"));
    assertTrue(
        powerShellBundleLauncher.contains("--add-opens=java.base/java.nio=dev.erst.fingrind.cli"));
    assertTrue(
        powerShellBundleLauncher.contains("--add-exports=java.base/sun.nio=dev.erst.fingrind.cli"));
    assertTrue(powerShellBundleLauncher.contains("-D{{sqliteBundleHomeSystemProperty}}=$appHome"));
    assertTrue(
        powerShellBundleLauncher.contains("-Dfingrind.runtime.bundle-target={{bundleClassifier}}"));
    assertTrue(powerShellBundleLauncher.contains("\"--module\","));
    assertTrue(powerShellBundleLauncher.contains("$applicationModule"));
    assertTrue(
        dockerEntrypoint.contains(
            "application_module=\"dev.erst.fingrind.cli/dev.erst.fingrind.cli.App\""));
    assertTrue(dockerEntrypoint.contains("--enable-native-access=dev.erst.fingrind.cli"));
    assertTrue(dockerEntrypoint.contains("--add-opens=java.base/java.nio=dev.erst.fingrind.cli"));
    assertTrue(dockerEntrypoint.contains("--add-exports=java.base/sun.nio=dev.erst.fingrind.cli"));
    assertTrue(dockerEntrypoint.contains("-D{{sqliteBundleHomeSystemProperty}}=\"${app_home}\""));
    assertTrue(dockerEntrypoint.contains("--module \"${application_module}\""));
  }

  @Test
  void cliBuild_generatesBundleManifestFromCanonicalContractMetadata() throws IOException {
    Path repositoryRoot = repositoryRoot();
    String distributionPlugin =
        Files.readString(
            repositoryRoot.resolve(
                "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindCliDistributionPlugin.kt"));
    String bundleReadme = Files.readString(repositoryRoot.resolve("cli/src/bundle/root/README.md"));
    String quickStartRequest =
        Files.readString(repositoryRoot.resolve("cli/src/bundle/root/quick-start-request.json"));

    assertFalse(Files.exists(repositoryRoot.resolve("cli/src/bundle/root/bundle-manifest.json")));
    assertTrue(
        distributionPlugin.contains(
            "tasks.register<WriteBundleManifestTask>(\"writeBundleManifest\")"));
    assertTrue(
        distributionPlugin.contains(
            "contractFiles.from(DistributionContractReader.requiredContractFiles(repositoryRootDirectory))"));
    assertTrue(distributionPlugin.contains("generated/bundle/root/bundle-manifest.json"));
    assertTrue(distributionPlugin.contains("writeBundleManifest"));
    assertFalse(distributionPlugin.contains("src/bundle/root/bundle-manifest.json"));
    assertTrue(bundleReadme.contains("quick-start-request.json"));
    assertTrue(quickStartRequest.contains("\"entryKind\": \"SALE_SETTLED\""));
    assertTrue(quickStartRequest.contains("\"cashAccountCode\": \"cash\""));
    assertTrue(quickStartRequest.contains("\"revenueAccountCode\": \"service-revenue\""));
    assertFalse(quickStartRequest.contains("\"recipeKind\": \"CASH_REVENUE\""));
  }

  @Test
  void cliBuild_prunesStaleVersionedBundleRootsArchivesAndChecksumsBeforeStagingTheCurrentBundle()
      throws IOException {
    Path repositoryRoot = repositoryRoot();
    String distributionPlugin =
        Files.readString(
            repositoryRoot.resolve(
                "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindCliDistributionPlugin.kt"));
    String pruneTask =
        Files.readString(
            repositoryRoot.resolve(
                "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/PruneBundleOutputsTask.kt"));
    String manifestTask =
        Files.readString(
            repositoryRoot.resolve(
                "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/WriteBundleArchiveManifestTask.kt"));
    String bundleArchiveTasks =
        Files.readString(
            repositoryRoot.resolve(
                "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/CliBundleArchiveTasks.kt"));

    assertTrue(
        distributionPlugin.contains(
            "tasks.register<PruneBundleOutputsTask>(\"cleanBundleOutputs\")"));
    assertTrue(
        distributionPlugin.contains(
            "Deletes staged self-contained FinGrind CLI bundle directories plus prior bundle archives and checksum files."));
    assertTrue(distributionPlugin.contains("artifactPrefix.set(\"fingrind-\")"));
    assertTrue(
        distributionPlugin.contains(
            "legacyBundleWorkspaceDirectory.set(layout.projectDirectory.dir(\"build/bundle\"))"));
    assertTrue(
        distributionPlugin.contains(
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
    assertTrue(distributionPlugin.contains("dependsOn(cleanBundleOutputs)"));
    assertTrue(
        bundleArchiveTasks.contains(
            "tasks.register<WriteBundleArchiveManifestTask>(\"bundleCliArchive\")"));
    assertTrue(bundleArchiveTasks.contains("bundleArchiveManifestFile"));
    assertTrue(manifestTask.contains("archivePath"));
    assertTrue(manifestTask.contains("checksumPath"));
    assertFalse(distributionPlugin.contains("cleanBundleRoot"));
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
  void rootFormattingConventions_includeTrackedMarkdownAndAvoidCodexExclusion() throws IOException {
    String buildLogic =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "gradle/build-logic/src/main/kotlin/dev/erst/fingrind/buildlogic/FinGrindRootFormattingConventions.kt"));

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
    assertTrue(bundleCommandBridge.contains("Get-Command pwsh"));
    assertTrue(bundleCommandBridge.contains("ProcessStartInfo"));
    assertTrue(bundleCommandBridge.contains("RedirectStandardInput"));
    assertTrue(bundleCommandBridge.contains("FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE"));
    assertTrue(bundleCommandBridge.contains("ConvertTo-Json -Compress $arguments"));
    assertTrue(bundleCommandBridge.contains("\"-ExecutionPolicy\""));
    assertTrue(bundleCommandBridge.contains("\"-File\", $LauncherPath"));
    assertFalse(bundleCommandBridge.contains("FINGRIND_BUNDLE_RETURN_EXIT_CODE"));
    assertFalse(bundleCommandBridge.contains("FINGRIND_BUNDLE_ARGUMENTS_FILE"));
    assertFalse(bundleCommandBridge.contains("FINGRIND_BUNDLE_STDIN_FILE"));
    assertFalse(bundleCommandBridge.contains("& $LauncherPath"));
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
    assertTrue(powerShellLauncher.contains("[Console]::IsInputRedirected"));
    assertTrue(powerShellLauncher.contains("OpenStandardInput().CopyTo"));
    assertTrue(powerShellLauncher.contains("Remove(\"FINGRIND_SQLITE_LIBRARY\")"));
    assertTrue(powerShellLauncher.contains("FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE"));
    assertTrue(powerShellLauncher.contains("New-StagedCliArgumentsFile"));
    assertTrue(powerShellLauncher.contains("$PSScriptRoot"));
    assertTrue(powerShellLauncher.contains("$scriptInvocationArguments = @($args)"));
    assertTrue(powerShellLauncher.contains("$PSCommandPath"));
    assertTrue(powerShellLauncher.contains("-Ddev.erst.fingrind.invocation=$invocationLabel"));
    assertFalse(powerShellLauncher.contains("$MyInvocation.MyCommand.Path"));
    assertFalse(powerShellLauncher.contains("& $runtimeJava @javaArguments"));
    assertFalse(powerShellLauncher.contains("ConvertFrom-Json"));
    assertFalse(powerShellLauncher.contains("FINGRIND_LAUNCHER_ARGUMENTS_FILE"));
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
