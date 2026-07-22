package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;

import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationCredentialPurpose;
import dev.erst.fingrind.core.attestation.AttestationGrantState;
import dev.erst.fingrind.core.attestation.AttestationPublicCredential;
import dev.erst.fingrind.core.attestation.AttestationSystemWorkflowKind;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import tools.jackson.databind.node.ObjectNode;

/** Parses closed attestation registry scalar values from one strict JSON request. */
final class CliAttestationRegistryValueParser {
  private static final int ED25519_SPKI_BASE64URL_LENGTH = 59;
  private static final Pattern CANONICAL_UUID =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final byte[] ED25519_SPKI_PREFIX = {
    0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
  };

  private CliAttestationRegistryValueParser() {}

  static UUID uuid(ObjectNode rootNode, String fieldName) {
    String encoded = requiredText(rootNode, fieldName);
    if (!CANONICAL_UUID.matcher(encoded).matches()) {
      throw new IllegalArgumentException("Field must be a UUID: " + fieldName);
    }
    return UUID.fromString(encoded);
  }

  static AttestationPublicCredential credential(ObjectNode rootNode, String fieldName) {
    String encoded = requiredText(rootNode, fieldName);
    if (!encoded.matches("[A-Za-z0-9_-]{" + ED25519_SPKI_BASE64URL_LENGTH + "}")) {
      throw invalidCredential(fieldName);
    }
    byte[] spki = Base64.getUrlDecoder().decode(encoded);
    if (!Base64.getUrlEncoder().withoutPadding().encodeToString(spki).equals(encoded)
        || !hasEd25519SpkiPrefix(spki)) {
      throw invalidCredential(fieldName);
    }
    return new AttestationPublicCredential(spki);
  }

  static AttestationCredentialPurpose credentialPurpose(ObjectNode rootNode, String fieldName) {
    String token = requiredText(rootNode, fieldName);
    for (AttestationCredentialPurpose purpose : AttestationCredentialPurpose.values()) {
      if (purpose.token().equals(token)) {
        return purpose;
      }
    }
    throw unsupportedToken(
        fieldName,
        token,
        AttestationCredentialPurpose.values(),
        AttestationCredentialPurpose::token);
  }

  static AttestationCapability capability(ObjectNode rootNode, String fieldName) {
    String token = requiredText(rootNode, fieldName);
    for (AttestationCapability capability : AttestationCapability.values()) {
      if (capability.token().equals(token)) {
        return capability;
      }
    }
    throw unsupportedToken(
        fieldName, token, AttestationCapability.values(), AttestationCapability::token);
  }

  static AttestationGrantState grantState(ObjectNode rootNode, String fieldName) {
    String token = requiredText(rootNode, fieldName);
    for (AttestationGrantState state : AttestationGrantState.values()) {
      if (state.token().equals(token)) {
        return state;
      }
    }
    throw unsupportedToken(
        fieldName, token, AttestationGrantState.values(), AttestationGrantState::token);
  }

  static AttestationSystemWorkflowKind workflowKind(ObjectNode rootNode, String fieldName) {
    String token = requiredText(rootNode, fieldName);
    for (AttestationSystemWorkflowKind kind : AttestationSystemWorkflowKind.values()) {
      if (kind.wireToken().equals(token)) {
        return kind;
      }
    }
    throw unsupportedToken(
        fieldName,
        token,
        AttestationSystemWorkflowKind.values(),
        AttestationSystemWorkflowKind::wireToken);
  }

  private static boolean hasEd25519SpkiPrefix(byte[] spki) {
    for (int index = 0; index < ED25519_SPKI_PREFIX.length; index++) {
      if (spki[index] != ED25519_SPKI_PREFIX[index]) {
        return false;
      }
    }
    return true;
  }

  private static IllegalArgumentException invalidCredential(String fieldName) {
    return new IllegalArgumentException(
        "Field must be a canonical base64url Ed25519 DER SPKI: " + fieldName);
  }

  private static <T> IllegalArgumentException unsupportedToken(
      String fieldName, String token, T[] acceptedValues, Function<T, String> tokenMapper) {
    String accepted =
        java.util.Arrays.stream(acceptedValues)
            .map(tokenMapper)
            .collect(java.util.stream.Collectors.joining(", "));
    return new IllegalArgumentException(
        "Unsupported value for "
            + fieldName
            + ": "
            + token
            + ". Accepted values: "
            + accepted
            + ".");
  }
}
