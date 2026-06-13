package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** Covers deterministic SQLite round-trip fuzz entry behavior. */
class SqliteBookRoundTripFuzzAssertionsTest {
  @Test
  void helper_round_trips_valid_and_rejected_requests_and_ignores_invalid_shapes() {
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripParsedCommand(
                SqliteRoundTripWorkflowTestSupport.basicValidCommand(),
                CliFuzzRequestSeedSupport.basicValidRequestBytes()));
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripSingleBook(
                CliFuzzRequestSeedSupport.validJpyRequestBytes()));
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripSingleBook(
                CliFuzzRequestSeedSupport.validBhdRequestBytes()));
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripParsedCommand(
                CliFuzzFixtures.readPostEntryCommand(
                    CliFuzzRequestSeedSupport.reversalTargetMissingRequest().getBytes(UTF_8)),
                CliFuzzRequestSeedSupport.reversalTargetMissingRequestBytes()));
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripSingleBook(
                CliFuzzRequestSeedSupport.invalidWrongTypeRequestBytes()));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_valid_and_invalid_requests() {
    SqliteBookRoundTripFuzzTest harness = new SqliteBookRoundTripFuzzTest();
    assertDoesNotThrow(
        () ->
            harness.roundTripSingleBook(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.basicValidRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.roundTripSingleBook(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.validJpyRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.roundTripSingleBook(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.validBhdRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.roundTripSingleBook(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzRequestSeedSupport.invalidWrongTypeRequestBytes())));
  }
}
