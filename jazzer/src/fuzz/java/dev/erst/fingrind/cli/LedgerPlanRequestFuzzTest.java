package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/** Fuzzes ledger-plan CLI request decoding from raw JSON payloads. */
public class LedgerPlanRequestFuzzTest {
  @FuzzTest
  void readLedgerPlan(FuzzedDataProvider data) {
    LedgerPlanRequestFuzzAssertions.readLedgerPlan(data.consumeRemainingAsBytes());
  }
}
