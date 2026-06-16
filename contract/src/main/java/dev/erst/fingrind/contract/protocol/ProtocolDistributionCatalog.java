package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Public distribution and launcher catalog for FinGrind delivery surfaces. */
public final class ProtocolDistributionCatalog {
  static final ProtocolDistributionCatalog INSTANCE = new ProtocolDistributionCatalog();

  private ProtocolDistributionCatalog() {}

  /** Returns the canonical direct-Java runtime-distribution identifier. */
  public RuntimeDistribution directJavaRuntimeDistribution() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().directJavaRuntimeDistribution();
  }

  /** Returns the canonical source-checkout runtime-distribution identifier. */
  public RuntimeDistribution sourceCheckoutRuntimeDistribution() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().sourceCheckoutRuntimeDistribution();
  }

  /** Returns the canonical container-image runtime-distribution identifier. */
  public RuntimeDistribution containerRuntimeDistribution() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().containerRuntimeDistribution();
  }

  /** Returns the canonical logical launcher command for container-backed execution. */
  public String containerLauncherCommand() {
    return "fingrind";
  }

  /** Returns the canonical mounted container launcher prefix for host execution. */
  public String containerMountedLauncherPrefix() {
    return "docker run --rm -i -v <host-workdir>:/workspace -w /workspace <container-image>";
  }

  /** Returns the canonical bundle runtime-distribution identifier. */
  public RuntimeDistribution bundleRuntimeDistribution() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().bundleRuntimeDistribution();
  }

  /** Returns the canonical public CLI distribution identifier. */
  public PublicCliDistribution publicCliDistribution() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().publicCliDistribution();
  }

  /** Returns supported self-contained public CLI bundle targets. */
  public List<PublicCliBundleTarget> supportedPublicCliBundleTargets() {
    return ProtocolCatalogFacts.bundleLayoutContract().supportedPublicCliBundleTargets();
  }

  /** Returns bundle targets outside the current self-contained public CLI contract. */
  public List<PublicCliBundleTarget> unsupportedPublicCliBundleTargets() {
    return ProtocolCatalogFacts.bundleLayoutContract().unsupportedPublicCliBundleTargets();
  }

  /** Returns the canonical launcher command for one public bundle target. */
  public String bundleLauncherCommand(PublicCliBundleTarget target) {
    return ProtocolCatalogFacts.bundleLayoutContract().bundleTarget(target).launcherCommand();
  }

  /** Returns the canonical launcher path for one public bundle target. */
  public String bundleLauncherPath(PublicCliBundleTarget target) {
    return ProtocolCatalogFacts.bundleLayoutContract().bundleTarget(target).launcherPath();
  }

  /** Returns the canonical source-checkout launcher command for one host shell family. */
  public String sourceCheckoutLauncherCommand(boolean windows) {
    return windows ? ".\\scripts\\source-checkout-cli.ps1" : "./scripts/source-checkout-cli.sh";
  }

  /** Returns the canonical minimum Java line for source-checkout execution. */
  public String sourceCheckoutJava() {
    return ProtocolCatalogFacts.runtimeEnvironmentContract().sourceCheckoutJava();
  }

  /** Returns the canonical direct-Java launcher command for one host shell family. */
  public String directJavaLauncherCommand(boolean windows) {
    return windows ? ".\\scripts\\direct-java-cli.ps1" : "./scripts/direct-java-cli.sh";
  }
}
