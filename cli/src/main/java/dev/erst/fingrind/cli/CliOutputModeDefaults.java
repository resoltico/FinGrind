package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import org.jspecify.annotations.Nullable;

/** Resolves the default stdout mode for commands that support explicit output selection. */
final class CliOutputModeDefaults {
  private CliOutputModeDefaults() {}

  static OutputMode resolved(@Nullable OutputMode explicitOutputMode) {
    return resolved(explicitOutputMode, interactiveConsoleAvailable());
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
    return interactiveConsoleAvailable ? OutputMode.HUMAN : OutputMode.JSON;
  }

  private static boolean interactiveConsoleAvailable() {
    return java.util.Optional.ofNullable(System.console())
        .map(java.io.Console::isTerminal)
        .orElse(false);
  }
}
