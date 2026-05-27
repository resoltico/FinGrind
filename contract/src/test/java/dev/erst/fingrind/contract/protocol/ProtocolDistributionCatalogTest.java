package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage tests for the public distribution catalog accessors. */
class ProtocolDistributionCatalogTest {
  @Test
  void distributionCatalogPublishesCanonicalLauncherAndTargetFacts() {
    ProtocolDistributionCatalog distribution = ProtocolCatalog.distribution();

    assertEquals(
        RuntimeDistribution.DIRECT_JAVA_INVOCATION, distribution.directJavaRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.SOURCE_CHECKOUT_GRADLE,
        distribution.sourceCheckoutRuntimeDistribution());
    assertEquals(RuntimeDistribution.CONTAINER_IMAGE, distribution.containerRuntimeDistribution());
    assertEquals(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE, distribution.bundleRuntimeDistribution());
    assertEquals(PublicCliDistribution.SELF_CONTAINED_BUNDLE, distribution.publicCliDistribution());
    assertEquals("26+", distribution.sourceCheckoutJava());
    assertEquals(
        "./scripts/source-checkout-cli.sh", distribution.sourceCheckoutLauncherCommand(false));
    assertEquals(
        ".\\scripts\\source-checkout-cli.ps1", distribution.sourceCheckoutLauncherCommand(true));
    assertEquals("./scripts/direct-java-cli.sh", distribution.directJavaLauncherCommand(false));
    assertEquals(".\\scripts\\direct-java-cli.ps1", distribution.directJavaLauncherCommand(true));
    assertEquals(
        "docker run --rm -i -v <host-workdir>:/workspace -w /workspace <container-image>",
        distribution.containerLauncherCommand());
    assertEquals(
        List.of(
            PublicCliBundleTarget.MACOS_AARCH64,
            PublicCliBundleTarget.MACOS_X86_64,
            PublicCliBundleTarget.LINUX_X86_64,
            PublicCliBundleTarget.LINUX_AARCH64,
            PublicCliBundleTarget.WINDOWS_X86_64),
        distribution.supportedPublicCliBundleTargets());
    assertEquals(
        List.of(PublicCliBundleTarget.WINDOWS_AARCH64),
        distribution.unsupportedPublicCliBundleTargets());
    for (PublicCliBundleTarget target : distribution.supportedPublicCliBundleTargets()) {
      assertTrue(distribution.bundleLauncherCommand(target).contains("fingrind"));
      assertTrue(distribution.bundleLauncherPath(target).contains("fingrind"));
    }
  }
}
