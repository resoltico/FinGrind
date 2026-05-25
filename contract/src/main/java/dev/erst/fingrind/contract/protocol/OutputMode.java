package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Public output-selection vocabulary for FinGrind commands that advertise selectable output. */
public enum OutputMode implements WireValue {
  /** Canonical machine-readable JSON output. */
  JSON("json") {
    @Override
    public void run(Runnable jsonAction, Runnable textAction, Runnable csvAction) {
      Objects.requireNonNull(jsonAction, "jsonAction").run();
    }
  },
  /** Canonical operator-readable text output. */
  TEXT("text") {
    @Override
    public void run(Runnable jsonAction, Runnable textAction, Runnable csvAction) {
      Objects.requireNonNull(textAction, "textAction").run();
    }
  },
  /** Stable CSV table output for spreadsheet import. */
  CSV("csv") {
    @Override
    public void run(Runnable jsonAction, Runnable textAction, Runnable csvAction) {
      Objects.requireNonNull(csvAction, "csvAction").run();
    }
  };

  private final String wireValue;

  OutputMode(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable public wire value for this output mode. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Runs one rendering branch without requiring downstream switch dispatch. */
  public abstract void run(Runnable jsonAction, Runnable textAction, Runnable csvAction);

  /** Returns every stable output-mode wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(OutputMode.class);
  }

  /** Parses one stable public output-mode wire value. */
  public static OutputMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(OutputMode.class, wireValue, "Unsupported output mode");
  }
}
