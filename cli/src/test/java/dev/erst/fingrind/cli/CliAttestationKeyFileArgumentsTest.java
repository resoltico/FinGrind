package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the strict direct-argument boundary for standalone attestation credential custody. */
class CliAttestationKeyFileArgumentsTest {
  @Test
  void parsesGenerationAndInspectionWithOnlyTheirPublishedOptions() {
    GenerateAttestationKeyFile generated =
        assertInstanceOf(
            GenerateAttestationKeyFile.class,
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of(
                    "generate-attestation-key-file",
                    "--attestation-custodian",
                    "file-pkcs8",
                    "--new-attestation-key-file",
                    "operator.fgatk",
                    "--attestation-passphrase-file",
                    "operator.passphrase",
                    "--output",
                    "json")));
    InspectAttestationKeyFile inspected =
        assertInstanceOf(
            InspectAttestationKeyFile.class,
            CliAttestationKeyFileArguments.parseInspectAttestationKeyFileCommand(
                List.of(
                    "inspect-attestation-key-file",
                    "--attestation-custodian",
                    "file-pkcs8",
                    "--attestation-key-file",
                    "operator.fgatk",
                    "--output",
                    "text")));

    assertEquals(Path.of("operator.fgatk"), generated.attestationKeyFilePath());
    assertEquals(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8, generated.custodian());
    assertEquals(Path.of("operator.passphrase"), generated.passphraseFilePath());
    assertEquals(OutputMode.JSON, generated.outputMode());
    assertEquals(OutputMode.TEXT, inspected.outputMode());
    assertEquals(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8, inspected.custodian());
  }

  @Test
  void rejectsMissingOrDuplicatedCustodyOptions() {
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of(
                    "generate-attestation-key-file",
                    "--attestation-passphrase-file",
                    "operator.passphrase")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of(
                    "generate-attestation-key-file",
                    "--new-attestation-key-file",
                    "operator.fgatk")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of(
                    "generate-attestation-key-file",
                    "--new-attestation-key-file",
                    "operator.fgatk",
                    "--new-attestation-key-file",
                    "second.fgatk",
                    "--attestation-passphrase-file",
                    "operator.passphrase")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of(
                    "generate-attestation-key-file",
                    "--new-attestation-key-file",
                    "operator.fgatk",
                    "--attestation-passphrase-file",
                    "operator.passphrase",
                    "--attestation-passphrase-file",
                    "second.passphrase")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of("generate-attestation-key-file", "--unsupported")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseInspectAttestationKeyFileCommand(
                List.of("inspect-attestation-key-file")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseInspectAttestationKeyFileCommand(
                List.of(
                    "inspect-attestation-key-file",
                    "--attestation-key-file",
                    "operator.fgatk",
                    "--attestation-key-file",
                    "second.fgatk")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseInspectAttestationKeyFileCommand(
                List.of("inspect-attestation-key-file", "--unsupported")));
  }

  @Test
  void rejectsEveryIncompleteOrDuplicateExplicitCustodySelection() {
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of(
                    "generate-attestation-key-file",
                    "--attestation-custodian",
                    "file-pkcs8",
                    "--attestation-custodian",
                    "file-pkcs8")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of("generate-attestation-key-file", "--attestation-custodian", "file-pkcs8")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseGenerateAttestationKeyFileCommand(
                List.of(
                    "generate-attestation-key-file",
                    "--attestation-custodian",
                    "file-pkcs8",
                    "--new-attestation-key-file",
                    "operator.fgatk")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseInspectAttestationKeyFileCommand(
                List.of(
                    "inspect-attestation-key-file",
                    "--attestation-custodian",
                    "file-pkcs8",
                    "--attestation-custodian",
                    "file-pkcs8")));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliAttestationKeyFileArguments.parseInspectAttestationKeyFileCommand(
                List.of("inspect-attestation-key-file", "--attestation-custodian", "file-pkcs8")));
  }
}
