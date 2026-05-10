package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** Covers deterministic CLI-request fuzz entry behavior. */
class CliRequestFuzzAssertionsTest {
  @Test
  void helper_accepts_valid_requests_and_ignores_invalid_shapes() {
    assertDoesNotThrow(
        () ->
            CliRequestFuzzAssertions.readPostEntryCommand(
                CliFuzzHarnessTestSupport.basicValidRequestBytes()));
    assertDoesNotThrow(
        () ->
            CliRequestFuzzAssertions.readPostEntryCommand(
                CliFuzzHarnessTestSupport.validJpyRequestBytes()));
    assertDoesNotThrow(
        () ->
            CliRequestFuzzAssertions.readPostEntryCommand(
                CliFuzzHarnessTestSupport.validBhdRequestBytes()));
    assertDoesNotThrow(
        () ->
            CliRequestFuzzAssertions.readPostEntryCommand(
                CliFuzzHarnessTestSupport.invalidExponentAmountRequestBytes()));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_valid_and_invalid_requests() {
    CliRequestFuzzTest harness = new CliRequestFuzzTest();
    assertDoesNotThrow(
        () ->
            harness.readPostEntryCommand(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.basicValidRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.readPostEntryCommand(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.validJpyRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.readPostEntryCommand(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.validBhdRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.readPostEntryCommand(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.invalidBlankActorRequestBytes())));
  }
}
