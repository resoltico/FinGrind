package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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
        ProtocolCatalog.containerLauncherCommand(),
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
      assertEquals(
          "./scripts/direct-java-cli.sh help",
          CliInvocationText.rewriteInvocationPrefix("fingrind help"));
      assertEquals(
          "cat ./secrets/acme.book-key | ./scripts/direct-java-cli.sh open-book --book-file ./books/acme.sqlite --book-passphrase-stdin",
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
}
