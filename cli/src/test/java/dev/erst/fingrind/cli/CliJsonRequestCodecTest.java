package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.io.ContentReference;

/** Unit tests for {@link CliJsonRequestCodec}. */
class CliJsonRequestCodecTest {
  @Test
  void requestPlaceholderValues_rejectNestedScaffoldValuesAndKeepRealValues() throws Exception {
    var mapper = CliJsonObjectMappers.configuredObjectMapper();
    var realProvenance =
        (tools.jackson.databind.node.ObjectNode) mapper.readTree("{\"actorId\":\"operator-1\"}");
    assertEquals(
        "operator-1",
        CliRequestPlaceholderValues.requiredRealProvenanceText(
            realProvenance, "actorId", ScaffoldPlaceholders.ACTOR_ID));

    var reservedProvenance =
        (tools.jackson.databind.node.ObjectNode)
            mapper.readTree("{\"actorId\":\"%s\"}".formatted(ScaffoldPlaceholders.ACTOR_ID));
    IllegalArgumentException provenanceFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliRequestPlaceholderValues.requiredRealProvenanceText(
                    reservedProvenance, "actorId", ScaffoldPlaceholders.ACTOR_ID));
    assertEquals(
        "Scaffold placeholder must be replaced before submission: provenance.actorId",
        provenanceFailure.getMessage());

    IllegalArgumentException topLevelFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliRequestPlaceholderValues.requiredRealText(
                    reservedProvenance, "actorId", ScaffoldPlaceholders.ACTOR_ID, null));
    assertEquals(
        "Scaffold placeholder must be replaced before submission: actorId",
        topLevelFailure.getMessage());

    var requestWithReservedSourceDocument =
        mapper.readTree(
            """
            {"evidence":{"sourceDocuments":[{"sourceDocumentId":"%s"}]}}
            """
                .formatted(ScaffoldPlaceholders.SOURCE_DOCUMENT_ID));
    IllegalArgumentException requestFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliRequestPlaceholderValues.rejectReservedScaffoldValues(
                    requestWithReservedSourceDocument));
    assertEquals(
        "Scaffold placeholder must be replaced before submission: sourceDocuments[0].sourceDocumentId",
        requestFailure.getMessage());
  }

  @Test
  void hasDuplicateObjectKeys_returnsFalseWhenObjectKeysAreDistinct() throws Exception {
    assertFalse(
        CliJsonObjectMappers.hasDuplicateObjectKeys(
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
        CliJsonObjectMappers.hasDuplicateObjectKeys(
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
        CliJsonRequestFailures.requestReadFailure(
            requestFile, new NoSuchFileException(requestFile.toString()), "unused hint");

    assertEquals("Request file does not exist.", exception.getMessage());
    assertEquals(requestFile, exception.failure().path());
    assertTrue(
        Objects.requireNonNull(exception.failure().hint())
            .contains("Verify that the selected --request-file exists and is readable"));
  }

  @Test
  void requestReadFailure_reportsUnreadableRequestFilesWithPathAwareDiagnostics() {
    Path requestFile = Path.of("private-request.json");

    CliRequestException exception =
        CliJsonRequestFailures.requestReadFailure(
            requestFile, new AccessDeniedException(requestFile.toString()), "unused hint");

    assertEquals("Request file is not readable.", exception.getMessage());
    assertEquals(requestFile, exception.failure().path());
  }

  @Test
  void requestReadFailure_reportsGenericRequestFileIoFailures() {
    Path requestFile = Path.of("broken-request.json");

    CliRequestException exception =
        CliJsonRequestFailures.requestReadFailure(
            requestFile, new IOException("boom"), "unused hint");

    assertEquals("Failed to read request file.", exception.getMessage());
    assertEquals(requestFile, exception.failure().path());
  }

  @Test
  void requestReadFailure_reportsStandardInputTransportFailuresSeparately() {
    CliRequestException exception =
        CliJsonRequestFailures.requestReadFailure(
            Path.of("-"), new IOException("boom"), "unused hint");

    assertEquals("Failed to read request JSON from standard input.", exception.getMessage());
    assertTrue(
        Objects.requireNonNull(exception.failure().hint())
            .contains("Provide a readable JSON document on standard input"));
  }

  @Test
  void requestReadFailure_reportsOversizedRequestPayloadsWithLimitAwareDiagnostics() {
    CliRequestException stdinException =
        CliJsonRequestFailures.requestReadFailure(
            Path.of("-"),
            new CliRequestPayloadTooLargeException(
                ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES),
            "unused hint");

    assertEquals(
        "Request JSON from standard input exceeded the supported "
            + ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES
            + "-byte UTF-8 limit.",
        stdinException.getMessage());
    assertTrue(
        Objects.requireNonNull(stdinException.failure().hint())
            .contains("split the work into smaller"));

    Path requestFile = Path.of("oversized-request.json");
    CliRequestException fileException =
        CliJsonRequestFailures.requestReadFailure(
            requestFile,
            new CliRequestPayloadTooLargeException(
                ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES),
            "unused hint");

    assertEquals(
        "Request file exceeded the supported "
            + ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES
            + "-byte UTF-8 limit.",
        fileException.getMessage());
    assertEquals(requestFile, fileException.failure().path());
  }

  @Test
  void requestReadFailure_preservesInvalidJsonDiagnosticsForJsonParsingFailures() throws Exception {
    JacksonException parseFailure =
        (JacksonException)
            assertThrows(
                JacksonException.class,
                () ->
                    CliJsonObjectMappers.configuredObjectMapper()
                        .readTree("{".getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        CliJsonRequestFailures.requestReadFailure(
            Path.of("request.json"), parseFailure, "schema hint");

    assertEquals(
        "Failed to read request JSON at line "
            + parseFailure.getLocation().getLineNr()
            + ", column "
            + parseFailure.getLocation().getColumnNr()
            + ".",
        exception.getMessage());
    assertEquals("schema hint", exception.failure().hint());
    CliErrorJsonModels.InvalidJsonDetails details =
        assertInstanceOf(
            CliErrorJsonModels.InvalidJsonDetails.class, exception.failure().details());
    assertEquals(parseFailure.getOriginalMessage(), details.parseMessage());
    assertEquals(parseFailure.getLocation().getLineNr(), details.line());
    assertEquals(parseFailure.getLocation().getColumnNr(), details.column());
  }

  @Test
  void readFailureMessage_fallsBackForNonJacksonAndUnusableJacksonLocations() {
    assertEquals(
        "Failed to read request JSON.",
        CliJsonRequestFailures.readFailureMessage(new RuntimeException("boom")));
    assertEquals(
        "Failed to read request JSON.",
        CliJsonRequestFailures.readFailureMessage(new StubJacksonException("boom", null)));
    assertEquals(
        "Failed to read request JSON.",
        CliJsonRequestFailures.readFailureMessage(
            new StubJacksonException("boom", new FixedLocation(0, 7))));
    assertEquals(
        "Failed to read request JSON.",
        CliJsonRequestFailures.readFailureMessage(
            new StubJacksonException("boom", new FixedLocation(7, 0))));
  }

  @Test
  void parseLocation_returnsOnlyUsablePositiveLocations() {
    assertEquals(
        new CliJsonRequestFailures.JsonParseLocation(4, 9),
        CliJsonRequestFailures.parseLocation(
            new StubJacksonException("boom", new FixedLocation(4, 9))));
    assertEquals(
        null, CliJsonRequestFailures.parseLocation(new StubJacksonException("boom", null)));
    assertEquals(
        null,
        CliJsonRequestFailures.parseLocation(
            new StubJacksonException("boom", new FixedLocation(0, 9))));
    assertEquals(
        null,
        CliJsonRequestFailures.parseLocation(
            new StubJacksonException("boom", new FixedLocation(9, 0))));
  }

  @Test
  void jsonParseLocation_requiresPositiveCoordinates() {
    IllegalArgumentException lineFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CliJsonRequestFailures.JsonParseLocation(0, 1));
    assertEquals("line must be positive", lineFailure.getMessage());

    IllegalArgumentException columnFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CliJsonRequestFailures.JsonParseLocation(1, 0));
    assertEquals("column must be positive", columnFailure.getMessage());
  }

  @Test
  void requestHints_followTheBundleLauncherWhenRunningFromTheBundleSurface() {
    String priorDistribution =
        System.getProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, "__missing__");
    try {
      System.setProperty(
          FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION);
      String bundleLauncher =
          CliInvocationText.launcherCommandFor(
              FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION, System.getProperty("os.name", ""));

      assertTrue(
          CliJsonRequestHints.postEntryRequestHint()
              .contains(bundleLauncher + " print-request-template"));
      assertTrue(
          CliJsonRequestHints.postEntryRequestHint()
              .contains(bundleLauncher + " help post-entry --output json --detail full"));
      assertFalse(CliJsonRequestHints.postEntryRequestHint().contains("request-input"));
      assertTrue(
          CliJsonRequestHints.ledgerPlanRequestHint()
              .contains(bundleLauncher + " print-plan-template"));
      assertTrue(
          CliJsonRequestHints.ledgerPlanRequestHint()
              .contains(bundleLauncher + " help execute-plan --output json --detail full"));
      assertFalse(CliJsonRequestHints.ledgerPlanRequestHint().contains("request-input"));
      assertTrue(
          CliJsonRequestHints.declareAccountRequestHint()
              .contains(bundleLauncher + " print-request-template declare-account"));
      assertTrue(
          CliJsonRequestHints.declareAccountRequestHint()
              .contains(bundleLauncher + " help declare-account --output json --detail full"));
      assertFalse(CliJsonRequestHints.declareAccountRequestHint().contains("request-input"));
    } finally {
      if ("__missing__".equals(priorDistribution)) {
        System.clearProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
      } else {
        System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, priorDistribution);
      }
    }
  }

  /** Minimal Jackson exception stub with caller-controlled location semantics. */
  private static final class StubJacksonException extends JacksonException {
    private static final long serialVersionUID = 1L;
    private final @org.jspecify.annotations.Nullable TokenStreamLocation explicitLocation;

    private StubJacksonException(
        String message, @org.jspecify.annotations.Nullable TokenStreamLocation location) {
      super(message, location, null);
      this.explicitLocation = location;
    }

    @Override
    public @org.jspecify.annotations.Nullable TokenStreamLocation getLocation() {
      return explicitLocation;
    }
  }

  /** Fixed token location for explicit line and column edge-case tests. */
  private static final class FixedLocation extends TokenStreamLocation {
    private static final long serialVersionUID = 1L;

    private final int lineNumber;
    private final int columnNumber;

    private FixedLocation(int lineNumber, int columnNumber) {
      super(ContentReference.rawReference("x"), 0L, 1, 1);
      this.lineNumber = lineNumber;
      this.columnNumber = columnNumber;
    }

    @Override
    public int getLineNr() {
      return lineNumber;
    }

    @Override
    public int getColumnNr() {
      return columnNumber;
    }
  }
}
