package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.util.Objects;

/** Renders discovery descriptors in operator-readable CLI text. */
final class CliDiscoveryOutputRenderer {
  private CliDiscoveryOutputRenderer() {}

  static String renderHelpText(HelpDescriptor helpDescriptor) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    return CliDiscoveryHelpTextRenderer.renderHelpText(helpDescriptor);
  }

  static String renderCapabilitiesText(
      dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    return CliDiscoveryCapabilitiesTextRenderer.renderCapabilitiesText(capabilitiesDescriptor);
  }

  static String renderEnvironmentText(EnvironmentDescriptor environmentDescriptor) {
    Objects.requireNonNull(environmentDescriptor, "environmentDescriptor");
    return CliDiscoveryRuntimeTextRenderer.renderEnvironmentText(environmentDescriptor);
  }

  static String renderVersionText(VersionDescriptor versionDescriptor) {
    Objects.requireNonNull(versionDescriptor, "versionDescriptor");
    return CliDiscoveryRuntimeTextRenderer.renderVersionText(versionDescriptor);
  }

  static String renderJsonTemplate(
      Object templateDescriptor, @org.jspecify.annotations.Nullable String shortcutCommand) {
    return CliDiscoveryHelpTextRenderer.renderJsonTemplate(templateDescriptor, shortcutCommand);
  }
}
