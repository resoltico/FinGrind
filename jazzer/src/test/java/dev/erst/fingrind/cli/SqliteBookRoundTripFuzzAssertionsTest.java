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
                CliFuzzHarnessTestSupport.basicValidRequestBytes()));
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripSingleBook(
                CliFuzzHarnessTestSupport.validJpyRequestBytes()));
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripSingleBook(
                CliFuzzHarnessTestSupport.validBhdRequestBytes()));
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripParsedCommand(
                CliFuzzFixtures.readPostEntryCommand(
                    CliFuzzHarnessTestSupport.reversalTargetMissingRequest().getBytes(UTF_8)),
                CliFuzzHarnessTestSupport.reversalTargetMissingRequestBytes()));
    assertDoesNotThrow(
        () ->
            SqliteBookRoundTripFuzzAssertions.roundTripSingleBook(
                CliFuzzHarnessTestSupport.invalidWrongTypeRequestBytes()));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_valid_and_invalid_requests() {
    SqliteBookRoundTripFuzzTest harness = new SqliteBookRoundTripFuzzTest();
    assertDoesNotThrow(
        () ->
            harness.roundTripSingleBook(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.basicValidRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.roundTripSingleBook(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.validJpyRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.roundTripSingleBook(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.validBhdRequestBytes())));
    assertDoesNotThrow(
        () ->
            harness.roundTripSingleBook(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.invalidWrongTypeRequestBytes())));
  }
}
