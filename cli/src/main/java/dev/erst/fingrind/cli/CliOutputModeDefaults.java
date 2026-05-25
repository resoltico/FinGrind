package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import org.jspecify.annotations.Nullable;

/** Resolves the default stdout mode for commands that support explicit output selection. */
final class CliOutputModeDefaults {
  private CliOutputModeDefaults() {}

  static OutputMode resolved(@Nullable OutputMode explicitOutputMode) {
    return resolved(explicitOutputMode, interactiveConsoleAvailable());
  }

  static OutputMode resolvedDiscovery(@Nullable OutputMode explicitOutputMode) {
    return explicitOutputMode == null
        ? defaultDiscoveryOutputMode(interactiveConsoleAvailable())
        : explicitOutputMode;
  }

  static OutputMode defaultSelectableOutputMode() {
    return defaultSelectableOutputMode(interactiveConsoleAvailable());
  }

  static OutputMode resolved(
      @Nullable OutputMode explicitOutputMode, boolean interactiveConsoleAvailable) {
    return explicitOutputMode == null
        ? defaultSelectableOutputMode(interactiveConsoleAvailable)
        : explicitOutputMode;
  }

  static OutputMode defaultSelectableOutputMode(boolean interactiveConsoleAvailable) {
    return interactiveConsoleAvailable ? OutputMode.TEXT : OutputMode.JSON;
  }

  static OutputMode defaultDiscoveryOutputMode() {
    return defaultDiscoveryOutputMode(interactiveConsoleAvailable());
  }

  static OutputMode defaultDiscoveryOutputMode(boolean interactiveConsoleAvailable) {
    return interactiveConsoleAvailable ? OutputMode.TEXT : OutputMode.JSON;
  }

  private static boolean interactiveConsoleAvailable() {
    java.io.Console console = System.console();
    return java.util.Optional.ofNullable(console).map(java.io.Console::isTerminal).orElse(false);
  }
}
