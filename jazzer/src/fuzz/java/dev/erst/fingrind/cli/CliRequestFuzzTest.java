package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.application.PostEntryCommand;
import org.junit.jupiter.api.Tag;

/** Fuzzes FinGrind CLI request decoding from raw JSON payloads. */
@Tag("jazzer")
public class CliRequestFuzzTest {
  @FuzzTest
  void readPostEntryCommand(FuzzedDataProvider data) {
    byte[] input = data.consumeRemainingAsBytes();
    try {
      PostEntryCommand command = CliFuzzSupport.readPostEntryCommand(input);
      if (command == null) {
        throw new IllegalStateException("readPostEntryCommand returned null");
      }
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }
}
