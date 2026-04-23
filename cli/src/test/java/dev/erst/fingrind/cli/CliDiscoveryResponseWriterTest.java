package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.ApplicationIdentity;
import dev.erst.fingrind.contract.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.HelpDescriptor;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.VersionDescriptor;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.NormalBalance;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link CliResponseWriter}. */
@NullUnmarked
class CliDiscoveryResponseWriterTest extends CliResponseWriterTestSupport {

  @Test
  void outputChannel_serializesProjectEnumsUsingWireValue() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));

    outputChannel.writeJson(new EnumPayload(OutputMode.JSON, NormalBalance.DEBIT));

    JsonNode json = readJson(outputStream);
    assertEquals("json", json.path("outputMode").asText());
    assertEquals("DEBIT", json.path("normalBalance").asText());
  }

  @Test
  void outputChannel_serializesNonProjectEnumsUsingEnumName() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));

    outputChannel.writeJson(new ExternalEnumPayload(Thread.State.RUNNABLE));

    JsonNode json = readJson(outputStream);
    assertEquals("RUNNABLE", json.path("state").asText());
  }

  @Test
  void outputChannel_rejectsProjectEnumsWithoutWireValue() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> outputChannel.writeJson(new MissingWireValuePayload(MissingWireValue.UNSAFE)));

    assertTrue(exception.getMessage().contains("must implement WireValue"));
  }

  @Test
  void outputChannel_rejectsBlankProjectEnumWireValues() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    assertEquals(" ", CliBlankWireValueFixture.UNSAFE.wireValue());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                outputChannel.writeJson(
                    new BlankWireValuePayload(CliBlankWireValueFixture.UNSAFE)));

    assertTrue(exception.getMessage().contains("must return a non-blank String"));
  }

  @Test
  void outputChannel_rejectsNullProjectEnumWireValues() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> outputChannel.writeJson(new NullWireValuePayload(NullWireValue.UNSAFE)));

    assertTrue(exception.getMessage().contains("must return a non-blank String"));
  }

  @Test
  void outputChannel_rejectsProjectEnumsWhenWireValueResolutionThrows() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    assertThrows(
        UnsupportedOperationException.class, () -> CliExplodingWireValueFixture.UNSAFE.wireValue());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                outputChannel.writeJson(
                    new ExplodingWireValuePayload(CliExplodingWireValueFixture.UNSAFE)));

    assertTrue(exception.getMessage().contains("Failed to resolve CLI JSON wireValue()"));
    assertTrue(causeChainContains(exception, "boom"));
  }

  @Test
  void outputChannel_preservesUnrelatedIllegalStateFailures() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));

    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> outputChannel.writeJson(new ExplodingGetter()));

    assertFalse(exception instanceof IllegalStateException);
    assertTrue(causeChainContains(exception, "boom"));
  }

  @Test
  void outputChannel_preservesNullMessageIllegalStateFailures() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> outputChannel.writeJson(new ExplodingGetter(true, null)));

    assertFalse(exception instanceof IllegalStateException);
  }

  @Test
  void writeVersion_writesOkEnvelope() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeVersion(
        new VersionDescriptor(
            "FinGrind",
            "0.9.0",
            "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"));

    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").asString());
    assertEquals("0.9.0", json.path("payload").path("version").asString());
  }

  @Test
  void writeHelp_supportsJsonAndHumanButRejectsCsv() throws IOException {
    HelpDescriptor helpDescriptor =
        MachineContract.help(
            new ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"),
            environmentDescriptor(
                "self-contained-bundle",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                "loaded",
                "3.53.0",
                "2.3.3",
                null));
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    CliResponseWriter jsonWriter = new CliResponseWriter(utf8PrintStream(jsonOutput));

    jsonWriter.writeHelp(helpDescriptor);
    assertEquals("ok", readJson(jsonOutput).path("status").asText());

    ByteArrayOutputStream humanOutput = new ByteArrayOutputStream();
    CliResponseWriter humanWriter = new CliResponseWriter(utf8PrintStream(humanOutput));
    humanWriter.writeHelp(helpDescriptor, OutputMode.HUMAN);
    assertTrue(humanOutput.toString(StandardCharsets.UTF_8).contains("FinGrind Help"));

    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    CliResponseWriter csvWriter = new CliResponseWriter(utf8PrintStream(csvOutput));
    assertThrows(
        IllegalArgumentException.class, () -> csvWriter.writeHelp(helpDescriptor, OutputMode.CSV));
  }

  @Test
  void writeCapabilities_omitsNullEnvironmentFields() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeCapabilities(
        MachineContract.capabilities(
            new ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"),
            environmentDescriptor(
                "container-image",
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                "unavailable",
                null,
                null,
                "system sqlite unavailable"),
            Instant.parse("2026-04-13T12:00:00Z")));

    JsonNode json = readJson(outputStream);
    JsonNode payload = json.path("payload");
    JsonNode environment = payload.path("environment");

    assertTrue(payload.has("preflight"));
    assertTrue(payload.has("currencyModel"));
    assertEquals("advisory", payload.path("preflight").path("semantics").asString());
    assertEquals("required", environment.path("storage").path("bookProtectionMode").asString());
    assertEquals(
        "container-image", environment.path("distribution").path("runtimeDistribution").asString());
    assertEquals(
        "self-contained-bundle",
        environment.path("distribution").path("publicCliDistribution").asString());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        readTextArray(environment.path("distribution").path("supportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
        readTextArray(
            environment.path("distribution").path("unsupportedPublicCliOperatingSystems")));
    assertFalse(environment.path("sqlite").has("loadedSqliteVersion"));
    assertFalse(environment.path("sqlite").has("loadedSqlite3mcVersion"));
  }

  @Test
  void writeCapabilities_supportsHumanButRejectsCsv() {
    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(
            new ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"),
            environmentDescriptor(
                "self-contained-bundle",
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                "loaded",
                "3.53.0",
                "2.3.3",
                null),
            Instant.parse("2026-04-13T12:00:00Z"));
    ByteArrayOutputStream humanOutput = new ByteArrayOutputStream();
    CliResponseWriter humanWriter = new CliResponseWriter(utf8PrintStream(humanOutput));

    humanWriter.writeCapabilities(capabilities, OutputMode.HUMAN);
    assertTrue(humanOutput.toString(StandardCharsets.UTF_8).contains("FinGrind Capabilities"));

    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    CliResponseWriter csvWriter = new CliResponseWriter(utf8PrintStream(csvOutput));
    assertThrows(
        IllegalArgumentException.class,
        () -> csvWriter.writeCapabilities(capabilities, OutputMode.CSV));
  }

  @Test
  void writeVersion_supportsHumanButRejectsCsv() {
    VersionDescriptor versionDescriptor =
        new VersionDescriptor(
            "FinGrind",
            "0.9.0",
            "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence");
    ByteArrayOutputStream humanOutput = new ByteArrayOutputStream();
    CliResponseWriter humanWriter = new CliResponseWriter(utf8PrintStream(humanOutput));

    humanWriter.writeVersion(versionDescriptor, OutputMode.HUMAN);
    assertTrue(humanOutput.toString(StandardCharsets.UTF_8).contains("Version"));

    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    CliResponseWriter csvWriter = new CliResponseWriter(utf8PrintStream(csvOutput));
    assertThrows(
        IllegalArgumentException.class,
        () -> csvWriter.writeVersion(versionDescriptor, OutputMode.CSV));
  }

  @Test
  void writeGenerateBookKeyFileResult_writesNonSecretMetadataEnvelope() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeGenerateBookKeyFileResult(
        new dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator.GeneratedKeyFile(
            Path.of("secrets").resolve("entity.book-key"), "base64url-no-padding", 256, "0600"));

    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").asString());
    assertEquals(
        Path.of("secrets").resolve("entity.book-key").toAbsolutePath().normalize().toString(),
        json.path("payload").path("bookKeyFile").asString());
    assertEquals("base64url-no-padding", json.path("payload").path("encoding").asString());
    assertEquals(256, json.path("payload").path("entropyBits").asInt());
    assertEquals("0600", json.path("payload").path("permissions").asString());
  }
}
