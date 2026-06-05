package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import org.jspecify.annotations.Nullable;

/** Resolves the default stdout mode for commands that support explicit output selection. */
final class CliOutputModeDefaults {
  private CliOutputModeDefaults() {}

  static OutputMode resolved(@Nullable OutputMode explicitOutputMode) {
    return resolved(explicitOutputMode, true);
  }

  static OutputMode resolvedDiscovery(@Nullable OutputMode explicitOutputMode) {
    return explicitOutputMode == null ? defaultDiscoveryOutputMode(true) : explicitOutputMode;
  }

  static OutputMode defaultSelectableOutputMode() {
    return defaultSelectableOutputMode(true);
  }

  static OutputMode resolved(
      @Nullable OutputMode explicitOutputMode, boolean interactiveConsoleAvailable) {
    return explicitOutputMode == null
        ? defaultSelectableOutputMode(interactiveConsoleAvailable)
        : explicitOutputMode;
  }

  static OutputMode defaultSelectableOutputMode(boolean interactiveConsoleAvailable) {
    return OutputMode.TEXT;
  }

  static OutputMode defaultDiscoveryOutputMode() {
    return defaultDiscoveryOutputMode(true);
  }

  static OutputMode defaultDiscoveryOutputMode(boolean interactiveConsoleAvailable) {
    return OutputMode.TEXT;
  }
}
