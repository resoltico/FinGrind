package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

/** Unit tests for {@link CliJsonRequestCodec}. */
class CliJsonRequestCodecTest {
  @Test
  void hasDuplicateObjectKeys_returnsFalseWhenObjectKeysAreDistinct() throws Exception {
    assertFalse(
        CliJsonRequestCodec.hasDuplicateObjectKeys(
            """
            {
              "effectiveDate": "2026-04-07",
              "provenance": {
                "actorId": "actor-1",
                "commandId": "command-1"
              }
            }
            """
                .getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void hasDuplicateObjectKeys_returnsTrueWhenObjectKeysRepeat() throws Exception {
    assertTrue(
        CliJsonRequestCodec.hasDuplicateObjectKeys(
            """
            {
              "provenance": {
                "actorId": "actor-1",
                "commandId": "command-1",
                "commandId": "command-2"
              }
            }
            """
                .getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void requestReadFailure_reportsMissingRequestFilesWithPathAwareDiagnostics() {
    Path requestFile = Path.of("missing-request.json");

    CliRequestException exception =
        CliJsonRequestCodec.requestReadFailure(
            requestFile, new NoSuchFileException(requestFile.toString()), "unused hint");

    assertEquals(
        "Request file does not exist: " + requestFile.toAbsolutePath().normalize() + ".",
        exception.getMessage());
    assertTrue(
        Objects.requireNonNull(exception.failure().hint())
            .contains("Verify that the selected --request-file exists and is readable"));
  }

  @Test
  void requestReadFailure_reportsUnreadableRequestFilesWithPathAwareDiagnostics() {
    Path requestFile = Path.of("private-request.json");

    CliRequestException exception =
        CliJsonRequestCodec.requestReadFailure(
            requestFile, new AccessDeniedException(requestFile.toString()), "unused hint");

    assertEquals(
        "Request file is not readable: " + requestFile.toAbsolutePath().normalize() + ".",
        exception.getMessage());
  }

  @Test
  void requestReadFailure_reportsGenericRequestFileIoFailures() {
    Path requestFile = Path.of("broken-request.json");

    CliRequestException exception =
        CliJsonRequestCodec.requestReadFailure(requestFile, new IOException("boom"), "unused hint");

    assertEquals(
        "Failed to read request file: " + requestFile.toAbsolutePath().normalize() + ".",
        exception.getMessage());
  }

  @Test
  void requestReadFailure_reportsStandardInputTransportFailuresSeparately() {
    CliRequestException exception =
        CliJsonRequestCodec.requestReadFailure(
            Path.of("-"), new IOException("boom"), "unused hint");

    assertEquals("Failed to read request JSON from standard input.", exception.getMessage());
    assertTrue(
        Objects.requireNonNull(exception.failure().hint())
            .contains("Provide one readable JSON document on standard input"));
  }

  @Test
  void requestReadFailure_preservesInvalidJsonDiagnosticsForJsonParsingFailures() throws Exception {
    JacksonException parseFailure =
        (JacksonException)
            assertThrows(
                JacksonException.class,
                () ->
                    CliJsonRequestCodec.configuredObjectMapper()
                        .readTree("{".getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        CliJsonRequestCodec.requestReadFailure(
            Path.of("request.json"), parseFailure, "schema hint");

    assertEquals("Failed to read request JSON.", exception.getMessage());
    assertEquals("schema hint", exception.failure().hint());
  }

  @Test
  void requestHints_followTheBundleLauncherWhenRunningFromTheBundleSurface() {
    String priorDistribution =
        System.getProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, "__missing__");
    try {
      System.setProperty(
          FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION);

      assertTrue(
          CliJsonRequestCodec.postEntryRequestHint()
              .contains("./bin/fingrind print-request-template"));
      assertTrue(
          CliJsonRequestCodec.ledgerPlanRequestHint()
              .contains("./bin/fingrind print-plan-template"));
      assertTrue(
          CliJsonRequestCodec.declareAccountRequestHint().contains("./bin/fingrind capabilities"));
    } finally {
      if ("__missing__".equals(priorDistribution)) {
        System.clearProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
      } else {
        System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, priorDistribution);
      }
    }
  }
}
