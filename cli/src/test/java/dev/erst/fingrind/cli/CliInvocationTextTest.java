package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliInvocationText}. */
class CliInvocationTextTest {

  @Test
  void launcherCommandFor_returnsRuntimeAwareLauncherCommandsAcrossSurfaces() {
    assertEquals(
        "fingrind",
        CliInvocationText.launcherCommandFor(FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION, "Mac OS X"));
    assertEquals(
        "fingrind",
        CliInvocationText.launcherCommandFor(
            FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION, "Windows 11"));
    assertEquals(
        "./scripts/source-checkout-cli.sh",
        CliInvocationText.launcherCommandFor(
            FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION, "Mac OS X"));
    assertEquals(
        ".\\scripts\\source-checkout-cli.ps1",
        CliInvocationText.launcherCommandFor(
            FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION, "Windows 11"));
    assertEquals(
        "./scripts/direct-java-cli.sh",
        CliInvocationText.launcherCommandFor(
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION, "Linux"));
    assertEquals(
        ".\\scripts\\direct-java-cli.ps1",
        CliInvocationText.launcherCommandFor(
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION, "Windows 11"));
    assertEquals(
        ProtocolCatalog.distribution().containerMountedLauncherPrefix(),
        CliInvocationText.launcherCommandFor(FinGrindCli.CONTAINER_RUNTIME_DISTRIBUTION, "Linux"));
  }

  @Test
  void rewriteInvocationPrefix_rewritesCommandExamplesToTheCurrentRuntimeSurface() {
    String originalDistribution =
        System.getProperty(
            FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY,
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION);
    System.setProperty(
        FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION);
    try {
      assertEquals("Usage", CliInvocationText.rewriteInvocationPrefix("Usage"));
      assertEquals("fingrind help", CliInvocationText.rewriteInvocationPrefix("fingrind help"));
      assertEquals(
          "fingrind help",
          CliInvocationText.rewriteInvocationPrefix("./scripts/direct-java-cli.sh help"));
      assertEquals(
          "cat ./secrets/acme.book-key | fingrind open-book --book-file ./books/acme.sqlite --book-passphrase-stdin",
          CliInvocationText.rewriteInvocationPrefix(
              "cat ./secrets/acme.book-key | fingrind open-book --book-file ./books/acme.sqlite --book-passphrase-stdin"));
    } finally {
      System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, originalDistribution);
    }

    System.setProperty(
        FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION);
    try {
      String directJavaLauncher =
          CliInvocationText.launcherCommandFor(
              FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION, System.getProperty("os.name", ""));
      assertEquals(
          directJavaLauncher + " help", CliInvocationText.rewriteInvocationPrefix("fingrind help"));
      assertEquals(
          "cat ./secrets/acme.book-key | "
              + directJavaLauncher
              + " open-book --book-file ./books/acme.sqlite --book-passphrase-stdin",
          CliInvocationText.rewriteInvocationPrefix(
              "cat ./secrets/acme.book-key | fingrind open-book --book-file ./books/acme.sqlite --book-passphrase-stdin"));
    } finally {
      System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, originalDistribution);
    }
  }

  @Test
  void launcherCommandFor_fallsBackToBareCommandForUnknownRuntimeDistribution() {
    assertEquals("fingrind", CliInvocationText.launcherCommandFor("mystery-runtime", "Linux"));

    String originalDistribution =
        System.getProperty(
            FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY,
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION);
    System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, "mystery-runtime");
    try {
      assertEquals("fingrind help", CliInvocationText.rewriteInvocationPrefix("fingrind help"));
    } finally {
      System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, originalDistribution);
    }
  }

  @Test
  void launcherCommandForCurrentRuntime_usesExplicitRawJarLauncherForUnnamedJarLaunches() {
    Module unnamedModule = Thread.currentThread().getContextClassLoader().getUnnamedModule();

    String launcherCommand =
        CliInvocationText.launcherCommandForCurrentRuntime(
            null, "Linux", unnamedModule, "fingrind.jar");

    assertEquals(
        "java --enable-native-access=dev.erst.fingrind.cli --module-path fingrind.jar --module"
            + " dev.erst.fingrind.cli/dev.erst.fingrind.cli.App",
        launcherCommand);
    assertEquals(
        "java --enable-native-access=dev.erst.fingrind.cli --module-path fingrind.jar --module"
            + " dev.erst.fingrind.cli/dev.erst.fingrind.cli.App help",
        CliInvocationText.rewriteInvocationPrefix("fingrind help", launcherCommand));
    assertEquals(
        "cat ./secrets/acme.book-key | java --enable-native-access=dev.erst.fingrind.cli"
            + " --module-path fingrind.jar --module dev.erst.fingrind.cli/dev.erst.fingrind.cli.App open-book --book-file"
            + " ./books/acme.sqlite --book-passphrase-stdin",
        CliInvocationText.rewriteInvocationPrefix(
            "cat ./secrets/acme.book-key | fingrind open-book --book-file ./books/acme.sqlite --book-passphrase-stdin",
            launcherCommand));
  }

  @Test
  void launcherCommandForCurrentRuntime_prefersConfiguredRuntimeAndOtherwiseFallsBackCleanly() {
    Module unnamedModule = Thread.currentThread().getContextClassLoader().getUnnamedModule();

    assertEquals(
        "./scripts/direct-java-cli.sh",
        CliInvocationText.launcherCommandForCurrentRuntime(
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION,
            "Linux",
            Object.class.getModule(),
            "ignored.jar"));
    assertEquals(
        "fingrind",
        CliInvocationText.launcherCommandForCurrentRuntime(
            "   ", "Linux", Object.class.getModule(), "fingrind.txt"));
    assertEquals(
        "fingrind",
        CliInvocationText.launcherCommandForCurrentRuntime(
            null, "Linux", unnamedModule, "fingrind.txt"));
    assertEquals(
        "fingrind",
        CliInvocationText.launcherCommandForCurrentRuntime(
            null, "Linux", Object.class.getModule(), "fingrind.jar"));
  }

  @Test
  void normalizationHelpers_coverNullBlankAndNonJarCodeSourceCases() throws MalformedURLException {
    assertNull(CliInvocationText.normalizeConfiguredRuntimeDistribution(null));
    assertNull(CliInvocationText.normalizeConfiguredRuntimeDistribution("   "));
    assertEquals(
        FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION,
        CliInvocationText.normalizeConfiguredRuntimeDistribution(
            "  " + FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION + "  "));

    assertNull(CliInvocationText.currentCodeSourceFileName(null));
    assertNull(
        CliInvocationText.currentCodeSourceFileName(new CodeSource(null, (CodeSigner[]) null)));
    assertEquals(
        "fingrind.jar",
        CliInvocationText.currentCodeSourceFileName(
            new CodeSource(new URL("file:/tmp/fingrind.jar"), (CodeSigner[]) null)));
    assertNull(
        CliInvocationText.currentCodeSourceFileName(
            new CodeSource(new URL("file:/"), (CodeSigner[]) null)));
    assertNull(
        CliInvocationText.currentCodeSourceFileName(
            new CodeSource(new URL("http://example.com/fingrind.jar"), (CodeSigner[]) null)));

    assertNull(CliInvocationText.normalizeCodeSourceJarFileName(null));
    assertNull(CliInvocationText.normalizeCodeSourceJarFileName("   "));
    assertNull(CliInvocationText.normalizeCodeSourceJarFileName("fingrind.txt"));
    assertEquals(
        "fingrind.jar", CliInvocationText.normalizeCodeSourceJarFileName("  fingrind.jar  "));
  }
}
