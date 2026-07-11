package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import dev.erst.fingrind.jazzer.support.FuzzHarnessInvocationSupport;

public final class CliFuzzHarnessInvocationSupport {
  private CliFuzzHarnessInvocationSupport() {}

  static FuzzedDataProvider fuzzedBytes(byte[] input) {
    return FuzzHarnessInvocationSupport.fuzzedBytes(input);
  }
}
