package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.NormalBalance;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link CliResponseWriter}. */
class CliDiscoveryResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void outputChannel_serializesProjectEnumsUsingWireValue() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    outputChannel.writeJson(new EnumPayload(OutputMode.JSON, NormalBalance.DEBIT));
    JsonNode json = readJson(outputStream);
    assertEquals("json", json.path("outputMode").stringValue());
    assertEquals("DEBIT", json.path("normalBalance").stringValue());
  }

  @Test
  void outputChannel_serializesNonProjectEnumsUsingEnumName() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    outputChannel.writeJson(new ExternalEnumPayload(Thread.State.RUNNABLE));
    JsonNode json = readJson(outputStream);
    assertEquals("RUNNABLE", json.path("state").stringValue());
  }

  @Test
  void outputChannel_rejectsProjectEnumsWithoutWireValue() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> outputChannel.writeJson(new MissingWireValuePayload(MissingWireValue.UNSAFE)));
    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage())
            .contains("must implement WireValue"));
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
    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage())
            .contains("must return a non-blank String"));
  }

  @Test
  void outputChannel_rejectsNullProjectEnumWireValues() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> outputChannel.writeJson(new NullWireValuePayload(NullWireValue.UNSAFE)));
    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage())
            .contains("must return a non-blank String"));
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
    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage())
            .contains("Failed to resolve CLI JSON wireValue()"));
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
            RuntimeException.class,
            () -> outputChannel.writeJson(new ExplodingGetter(true, nullOf())));
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
            "Command-line double-entry bookkeeping with one protected book per accounting entity"));
    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").stringValue());
    assertEquals("0.9.0", json.path("payload").path("version").stringValue());
  }

  @Test
  void writeHelp_supportsJsonAndHumanButRejectsCsv() throws IOException {
    HelpDescriptor helpDescriptor =
        MachineContract.help(
            new ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Command-line double-entry bookkeeping with one protected book per accounting entity"),
            environmentDescriptor(
                FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION,
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                "ready",
                "3.53.1",
                "2.3.4",
                nullOf()));
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    CliResponseWriter jsonWriter = new CliResponseWriter(utf8PrintStream(jsonOutput));
    jsonWriter.writeHelp(helpDescriptor);
    assertEquals("ok", readJson(jsonOutput).path("status").stringValue());
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
                "Command-line double-entry bookkeeping with one protected book per accounting entity"),
            environmentDescriptor(
                FinGrindCli.CONTAINER_RUNTIME_DISTRIBUTION,
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                "unavailable",
                nullOf(),
                nullOf(),
                "system sqlite unavailable")));
    JsonNode json = readJson(outputStream);
    JsonNode payload = json.path("payload");
    JsonNode environment = payload.path("environment");
    assertTrue(payload.has("preflight"));
    assertTrue(payload.has("currencyModel"));
    assertEquals("advisory", payload.path("preflight").path("semantics").stringValue());
    assertEquals(
        ProtocolCatalog.bookProtectionMode().wireValue(),
        environment.path("storage").path("bookProtectionMode").stringValue());
    assertEquals(
        FinGrindCli.CONTAINER_RUNTIME_DISTRIBUTION,
        environment.path("distribution").path("runtimeDistribution").stringValue());
    assertEquals(
        ProtocolCatalog.publicCliDistribution().wireValue(),
        environment.path("distribution").path("publicCliDistribution").stringValue());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets().stream()
            .map(dev.erst.fingrind.contract.protocol.PublicCliBundleTarget::wireValue)
            .toList(),
        readTextArray(environment.path("distribution").path("supportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliBundleTargets().stream()
            .map(dev.erst.fingrind.contract.protocol.PublicCliBundleTarget::wireValue)
            .toList(),
        readTextArray(environment.path("distribution").path("unsupportedPublicCliBundleTargets")));
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
                "Command-line double-entry bookkeeping with one protected book per accounting entity"),
            environmentDescriptor(
                FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION,
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                "ready",
                "3.53.1",
                "2.3.4",
                nullOf()));
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
            "Command-line double-entry bookkeeping with one protected book per accounting entity");
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
    assertEquals("ok", json.path("status").stringValue());
    assertEquals(
        Path.of("secrets").resolve("entity.book-key").toAbsolutePath().normalize().toString(),
        json.path("payload").path("bookKeyFile").stringValue());
    assertEquals("base64url-no-padding", json.path("payload").path("encoding").stringValue());
    assertEquals(256, json.path("payload").path("entropyBits").asInt());
    assertEquals("0600", json.path("payload").path("permissions").stringValue());
  }
}
