package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.sqlite.SqliteFuzzArtifactFixtures;
import dev.erst.fingrind.sqlite.SqliteFuzzBookAssertions;
import dev.erst.fingrind.sqlite.SqlitePostingSession;
import dev.erst.fingrind.sqlite.SqliteProtectedBookVerificationException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Objects;
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

  @Test
  void outerEnvelopeAssertions_refuseTamperedBooksAndDiagnoseUnexpectedOpenOutcomes() {
    Path workspace =
        assertDoesNotThrow(
            () ->
                SqliteFuzzArtifactFixtures.createOwnerOnlyTemporaryArtifactDirectory(
                    "fingrind-jazzer-outer-envelope-"));
    Path bookPath = workspace.resolve("book.sqlite");
    assertDoesNotThrow(
        () -> {
          try (SqlitePostingSession store = SqliteFuzzBookAssertions.openStore(bookPath)) {
            CliFuzzWorkflowFixtures.openBook(CliFuzzWorkflowFixtures.administrationService(store));
          }

          SqliteOuterEnvelopeFuzzAssertions.exercise(bookPath, workspace.resolve("tampered"));
        });

    IllegalStateException unexpectedFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteOuterEnvelopeFuzzAssertions.requireVerificationRefusal(
                    bookPath,
                    ignored -> {
                      throw new IllegalArgumentException("unexpected storage failure");
                    }));
    assertEquals(
        "Tampered protected-book envelope did not return the typed verification refusal.",
        unexpectedFailure.getMessage());
    assertTrue(unexpectedFailure.getCause() instanceof IllegalArgumentException);

    assertDoesNotThrow(
        () ->
            SqliteOuterEnvelopeFuzzAssertions.requireVerificationRefusal(
                bookPath, ignored -> throwVerificationFailure()));
    assertDoesNotThrow(
        () ->
            SqliteOuterEnvelopeFuzzAssertions.requireVerificationRefusal(
                bookPath, ignored -> closeWithVerificationFailure()));

    IllegalStateException acceptedFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteOuterEnvelopeFuzzAssertions.requireVerificationRefusal(
                    bookPath, SqliteFuzzBookAssertions::openStore));
    assertTrue(
        Objects.requireNonNull(acceptedFailure.getMessage(), "acceptedFailure message")
            .contains("unexpectedly opened"));
  }

  private static SqlitePostingSession throwVerificationFailure() {
    throw new SqliteProtectedBookVerificationException(
        new IllegalStateException("tampered envelope"));
  }

  private static SqlitePostingSession closeWithVerificationFailure() {
    return (SqlitePostingSession)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {SqlitePostingSession.class},
            (ignored, method, arguments) -> {
              if ("close".equals(method.getName())) {
                throwVerificationFailure();
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }
}
