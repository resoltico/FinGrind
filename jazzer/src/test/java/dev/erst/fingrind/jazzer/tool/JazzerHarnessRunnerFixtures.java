package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Test;

/** Minimal fuzz harness used to prove successful Jazzer discovery. */
interface SuccessfulFuzzHarnessFixture {
  @FuzzTest
  default void fuzz(FuzzedDataProvider data) {}
}

/** Minimal concrete fuzz harness that the official Jazzer runner can execute in one step. */
class SingleExecutionFuzzHarnessFixture {
  @FuzzTest(maxExecutions = 1)
  void fuzz(FuzzedDataProvider data) {}
}

/** Minimal non-fuzz harness used to prove discovery failure handling. */
interface NonFuzzHarnessFixture {
  @Test
  default void succeeds() {
    assertDoesNotThrow(() -> {});
  }
}

/** Minimal multi-fuzz harness used to enforce the single-method harness contract. */
interface MultiFuzzHarnessFixture {
  @FuzzTest
  default void alpha(FuzzedDataProvider data) {}

  @FuzzTest
  default void beta(FuzzedDataProvider data) {}
}
