package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import org.jspecify.annotations.Nullable;

/** Resolves the fixed text default for commands that support explicit output selection. */
final class CliOutputModeDefaults {
  private CliOutputModeDefaults() {}

  static OutputMode resolved(@Nullable OutputMode explicitOutputMode, OutputSurface outputSurface) {
    return explicitOutputMode == null ? outputDefault(outputSurface).mode() : explicitOutputMode;
  }

  static OutputDefault outputDefault(OutputSurface outputSurface) {
    return new OutputDefault(outputSurface.builtInDefault());
  }

  /** Declares which CLI surface is requesting the default output mode. */
  enum OutputSurface {
    SELECTABLE(OutputMode.TEXT),
    DISCOVERY(OutputMode.TEXT);

    private final OutputMode builtInDefault;

    OutputSurface(OutputMode builtInDefault) {
      this.builtInDefault = builtInDefault;
    }

    private OutputMode builtInDefault() {
      return builtInDefault;
    }
  }

  record OutputDefault(OutputMode mode) {
    OutputDefault {
      if (mode == OutputMode.CSV) {
        throw new IllegalArgumentException("Default output mode must not be csv.");
      }
    }
  }
}
