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
                CliFuzzRequestSeedSupport.basicValidRequestBytes()));
    assertDoesNotThrow(
        () ->
            CliRequestFuzzAssertions.readPostEntryCommand(
                CliFuzzRequestSeedSupport.validJpyRequestBytes()));
    assertDoesNotThrow(
        () ->
            CliRequestFuzzAssertions.readPostEntryCommand(
                CliFuzzRequestSeedSupport.validBhdRequestBytes()));
    assertDoesNotThrow(
        () ->
            CliRequestFuzzAssertions.readPostEntryCommand(
                CliFuzzRequestSeedSupport.invalidExponentAmountRequestBytes()));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_valid_and_invalid_requests() {
    CliRequestFuzzTest harness = new CliRequestFuzzTest();
    assertDoesNotThrow(
        () ->
            harness.readPostEntryCommand(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.basicValidRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.readPostEntryCommand(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.validJpyRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.readPostEntryCommand(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.validBhdRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.readPostEntryCommand(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.invalidBlankActorRequestBytes())));
  }
}
