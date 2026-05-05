package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliInvocationText}. */
class CliInvocationTextTest {

  @Test
  void launcherCommandFor_returnsPublishedBundleLaunchersPerSurface() {
    assertEquals(
        "./bin/fingrind",
        CliInvocationText.launcherCommandFor(FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION, "Mac OS X"));
    assertEquals(
        ".\\bin\\fingrind.ps1",
        CliInvocationText.launcherCommandFor(
            FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION, "Windows 11"));
    assertEquals(
        ProtocolCatalog.sourceCheckoutLauncherCommand(false),
        CliInvocationText.launcherCommandFor(
            FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION, "Mac OS X"));
    assertEquals(
        ProtocolCatalog.sourceCheckoutLauncherCommand(true),
        CliInvocationText.launcherCommandFor(
            FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION, "Windows 11"));
    assertEquals(
        ProtocolCatalog.directJavaLauncherCommand(false),
        CliInvocationText.launcherCommandFor(
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION, "Linux"));
    assertEquals(
        ProtocolCatalog.directJavaLauncherCommand(true),
        CliInvocationText.launcherCommandFor(
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION, "Windows 11"));
    assertEquals(
        "docker run --rm -v <host-workdir>:/workspace -w /workspace <container-image>",
        CliInvocationText.launcherCommandFor(FinGrindCli.CONTAINER_RUNTIME_DISTRIBUTION, "Linux"));
  }

  @Test
  void rewriteInvocationPrefix_preservesNonCommandTextOutsideSourceCheckout() {
    String originalDistribution =
        System.getProperty(
            FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY,
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION);
    System.setProperty(
        FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION);
    try {
      String expectedBundleLauncher =
          CliInvocationText.launcherCommandFor(
              FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION, System.getProperty("os.name", ""));
      assertEquals("Usage", CliInvocationText.rewriteInvocationPrefix("Usage"));
      assertEquals(
          expectedBundleLauncher + " help",
          CliInvocationText.rewriteInvocationPrefix("fingrind help"));
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
}
