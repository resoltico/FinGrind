package dev.erst.fingrind.jazzer.tool;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Minimal fuzz harness used to prove successful Jazzer discovery. */
interface SuccessfulFuzzHarnessFixture {
  @FuzzTest
  default void fuzz(FuzzedDataProvider data) {}
}

/** Minimal non-fuzz harness used to prove discovery failure handling. */
interface NonFuzzHarnessFixture {
  @Test
  default void succeeds() {
    assertTrue(true);
  }
}

/** Minimal multi-fuzz harness used to enforce the single-method harness contract. */
interface MultiFuzzHarnessFixture {
  @FuzzTest
  default void alpha(FuzzedDataProvider data) {}

  @FuzzTest
  default void beta(FuzzedDataProvider data) {}
}
