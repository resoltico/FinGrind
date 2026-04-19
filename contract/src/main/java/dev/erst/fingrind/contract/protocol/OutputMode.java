package dev.erst.fingrind.contract.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Public output-selection vocabulary for FinGrind commands that advertise selectable output. */
public enum OutputMode {
  /** Canonical machine-readable JSON output. */
  JSON("json") {
    @Override
    public void run(Runnable jsonAction, Runnable humanAction, Runnable csvAction) {
      Objects.requireNonNull(jsonAction, "jsonAction").run();
    }
  },
  /** Human-oriented fixed-width text output. */
  HUMAN("human") {
    @Override
    public void run(Runnable jsonAction, Runnable humanAction, Runnable csvAction) {
      Objects.requireNonNull(humanAction, "humanAction").run();
    }
  },
  /** Stable CSV table output for spreadsheet import. */
  CSV("csv") {
    @Override
    public void run(Runnable jsonAction, Runnable humanAction, Runnable csvAction) {
      Objects.requireNonNull(csvAction, "csvAction").run();
    }
  };

  private final String wireValue;

  OutputMode(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable public wire value for this output mode. */
  public String wireValue() {
    return wireValue;
  }

  /** Runs one rendering branch without requiring downstream switch dispatch. */
  public abstract void run(Runnable jsonAction, Runnable humanAction, Runnable csvAction);

  /** Returns every stable output-mode wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(OutputMode::wireValue).toList();
  }

  /** Parses one stable public output-mode wire value. */
  public static OutputMode fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(mode -> mode.wireValue().equals(wireValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported output mode: " + wireValue));
  }
}
