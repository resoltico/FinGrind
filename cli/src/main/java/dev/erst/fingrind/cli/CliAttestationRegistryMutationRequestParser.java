package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolAttestationRegistryRequestFields;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import tools.jackson.databind.node.ObjectNode;

/** Parses public credential-registry and authorization-policy request documents. */
final class CliAttestationRegistryMutationRequestParser {
  private CliAttestationRegistryMutationRequestParser() {}

  static AttestationRegistryMutation read(ObjectNode rootNode, OperationId operationId) {
    return switch (operationId) {
      case ENROLL_KEY -> enrollKey(rootNode);
      case ROLLOVER_KEY -> rolloverKey(rootNode);
      case REVOKE_KEY -> revokeKey(rootNode);
      case ALTER_POLICY -> alterPolicy(rootNode);
      default ->
          throw new IllegalArgumentException(
              "Attestation registry request parser does not own " + operationId.wireName() + ".");
    };
  }

  private static AttestationRegistryMutation.EnrollKey enrollKey(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAttestationRegistryRequestFields.enrollKeyFields());
    return new AttestationRegistryMutation.EnrollKey(
        CliAttestationRegistryValueParser.uuid(
            rootNode, ProtocolAttestationRegistryRequestFields.PRINCIPAL_ID),
        CliAttestationRegistryValueParser.credential(
            rootNode, ProtocolAttestationRegistryRequestFields.CREDENTIAL_SPKI),
        CliAttestationRegistryValueParser.credentialPurpose(
            rootNode, ProtocolAttestationRegistryRequestFields.CREDENTIAL_PURPOSE));
  }

  private static AttestationRegistryMutation.RolloverKey rolloverKey(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAttestationRegistryRequestFields.rolloverKeyFields());
    return new AttestationRegistryMutation.RolloverKey(
        CliAttestationRegistryValueParser.uuid(
            rootNode, ProtocolAttestationRegistryRequestFields.PRINCIPAL_ID),
        CliAttestationRegistryValueParser.credential(
            rootNode, ProtocolAttestationRegistryRequestFields.CREDENTIAL_SPKI),
        CliAttestationRegistryValueParser.credentialPurpose(
            rootNode, ProtocolAttestationRegistryRequestFields.CREDENTIAL_PURPOSE),
        CliAttestationRegistryValueParser.credential(
            rootNode, ProtocolAttestationRegistryRequestFields.PREDECESSOR_CREDENTIAL_SPKI));
  }

  private static AttestationRegistryMutation.RevokeKey revokeKey(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAttestationRegistryRequestFields.revokeKeyFields());
    return new AttestationRegistryMutation.RevokeKey(
        CliAttestationRegistryValueParser.uuid(
            rootNode, ProtocolAttestationRegistryRequestFields.PRINCIPAL_ID),
        CliAttestationRegistryValueParser.credential(
            rootNode, ProtocolAttestationRegistryRequestFields.CREDENTIAL_SPKI),
        CliJsonFieldAccess.optionalText(rootNode, ProtocolAttestationRegistryRequestFields.REASON));
  }

  private static AttestationRegistryMutation.AlterPolicy alterPolicy(ObjectNode rootNode) {
    return CliAttestationRegistryPolicyParser.parse(rootNode);
  }
}
