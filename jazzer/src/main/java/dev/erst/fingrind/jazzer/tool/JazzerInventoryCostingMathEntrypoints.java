package dev.erst.fingrind.jazzer.tool;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import java.util.Objects;

/** Shared `FuzzedDataProvider` entrypoints for weighted-average inventory-costing fuzzing. */
public final class JazzerInventoryCostingMathEntrypoints {
  private JazzerInventoryCostingMathEntrypoints() {}

  /** Consumes one raw-byte fuzz shape and asserts the exact weighted-average pool invariants. */
  public static void disposeUsesExactPoolMath(FuzzedDataProvider data) {
    Objects.requireNonNull(data, "data");
    JazzerInventoryCostingMathSupport.assertExactPoolMath(data.consumeRemainingAsBytes());
  }
}
