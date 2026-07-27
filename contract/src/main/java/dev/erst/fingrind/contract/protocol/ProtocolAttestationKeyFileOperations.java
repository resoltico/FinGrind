package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical off-book attestation-credential custody operations for the public protocol. */
final class ProtocolAttestationKeyFileOperations {
  private ProtocolAttestationKeyFileOperations() {}

  static ProtocolOperation generateAttestationKeyFileOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.GENERATE_ATTESTATION_KEY_FILE,
            OperationCategory.ADMINISTRATION,
            "Generate Attestation Key File",
            List.of(),
            List.of(
                ProtocolOptions.Attestation.CUSTODIAN + " <file-pkcs8>",
                ProtocolOptions.Attestation.NEW_KEY_FILE + " <path>",
                ProtocolOptions.Attestation.PASSPHRASE_FILE + " <path>",
                ProtocolOptionSyntax.Presentation.optionalOutputSyntax(
                    List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(ProtocolArtifactOutput.generatedAttestationKeyFile()),
            "Create a new encrypted Ed25519 attestation credential and return the exact public SPKI needed for credential enrollment or rollover.",
            List.of(
                ProtocolExampleStep.note(
                    "The passphrase file is caller-supplied custody material and must be a distinct owner-only UTF-8 secret file; FinGrind never prints it."),
                ProtocolExampleStep.command(
                    "fingrind %s %s file-pkcs8 %s ./secrets/operator.fgatk %s ./secrets/operator.passphrase"
                        .formatted(
                            OperationId.GENERATE_ATTESTATION_KEY_FILE.wireName(),
                            ProtocolOptions.Attestation.CUSTODIAN,
                            ProtocolOptions.Attestation.NEW_KEY_FILE,
                            ProtocolOptions.Attestation.PASSPHRASE_FILE)))));
  }

  static ProtocolOperation inspectAttestationKeyFileOperation() {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            OperationId.INSPECT_ATTESTATION_KEY_FILE,
            OperationCategory.QUERY,
            "Inspect Attestation Key File",
            List.of(),
            List.of(
                ProtocolOptions.Attestation.CUSTODIAN + " <file-pkcs8>",
                ProtocolOptions.Attestation.KEY_FILE + " <path>",
                ProtocolOptionSyntax.Presentation.optionalOutputSyntax(
                    List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(),
            "Read the non-secret Ed25519 public identity embedded in the selected encrypted attestation credential without loading private key material or a passphrase.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s file-pkcs8 %s ./secrets/founder.fgatk"
                        .formatted(
                            OperationId.INSPECT_ATTESTATION_KEY_FILE.wireName(),
                            ProtocolOptions.Attestation.CUSTODIAN,
                            ProtocolOptions.Attestation.KEY_FILE)))));
  }
}
