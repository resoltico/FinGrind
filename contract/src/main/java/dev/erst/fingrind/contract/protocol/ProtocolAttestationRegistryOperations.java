package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Public credential-registry and authorization-policy mutation operations. */
final class ProtocolAttestationRegistryOperations {
  private ProtocolAttestationRegistryOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        operation(
            OperationId.ENROLL_KEY,
            "Enroll Attestation Key",
            "Append a public Ed25519 credential binding for a principal.",
            "enroll-key.json"),
        operation(
            OperationId.ROLLOVER_KEY,
            "Rollover Attestation Key",
            "Append a replacement public Ed25519 credential binding for an active principal credential.",
            "rollover-key.json"),
        operation(
            OperationId.REVOKE_KEY,
            "Revoke Attestation Key",
            "Permanently revoke an enrolled public Ed25519 credential binding.",
            "revoke-key.json"),
        operation(
            OperationId.ALTER_POLICY,
            "Alter Attestation Policy",
            "Append future quorum, principal-capability grant, and autonomous workflow policy facts.",
            "alter-policy.json"));
  }

  private static ProtocolOperation operation(
      OperationId operationId, String summary, String description, String requestFileName) {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            operationId,
            OperationCategory.ADMINISTRATION,
            summary,
            List.of(),
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.Request.FILE + " <path|->",
                ProtocolOptions.requiredAttestationCredentialSyntax(),
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            List.of(),
            description,
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s %s ./books/acme.sqlite %s ./secrets/acme.book-key %s ./%s %s file-pkcs8 %s 123e4567-e89b-12d3-a456-426614174000 %s ./secrets/operator.fgatk %s ./secrets/operator.passphrase"
                        .formatted(
                            operationId.wireName(),
                            ProtocolBookAccessOptions.BOOK_FILE,
                            ProtocolBookAccessOptions.BOOK_KEY_FILE,
                            ProtocolOptions.Request.FILE,
                            requestFileName,
                            ProtocolOptions.Attestation.CUSTODIAN,
                            ProtocolOptions.Attestation.PRINCIPAL_ID,
                            ProtocolOptions.Attestation.KEY_FILE,
                            ProtocolOptions.Attestation.PASSPHRASE_FILE)))));
  }
}
