package dev.erst.fingrind.core;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.jazzer.tool.JazzerInventoryCostingMathEntrypoints;

/** Fuzzes weighted-average inventory-costing disposal math from raw byte seeds. */
public class WeightedAverageCostingMathFuzzTest {
  @FuzzTest
  void disposeUsesExactPoolMath(FuzzedDataProvider data) {
    JazzerInventoryCostingMathEntrypoints.disposeUsesExactPoolMath(data);
  }
}
