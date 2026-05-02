package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/** Fuzzes FinGrind CLI request decoding from raw JSON payloads. */
public class CliRequestFuzzTest {
  @FuzzTest
  void readPostEntryCommand(FuzzedDataProvider data) {
    CliRequestFuzzAssertions.readPostEntryCommand(data.consumeRemainingAsBytes());
  }
}
