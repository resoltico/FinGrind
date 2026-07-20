package dev.erst.fingrind.core.attestation;

/** Provenance channel whose operations require one credential purpose. */
enum AttestationSourceChannel {
  CLI("cli", AttestationCredentialPurpose.OPERATOR),
  SYSTEM("system", AttestationCredentialPurpose.SYSTEM);

  private final String wireToken;
  private final AttestationCredentialPurpose credentialPurpose;

  AttestationSourceChannel(String wireToken, AttestationCredentialPurpose credentialPurpose) {
    this.wireToken = wireToken;
    this.credentialPurpose = credentialPurpose;
  }

  static AttestationSourceChannel forWireToken(
      String wireToken, AttestationAuthorizationFailure failure) {
    return switch (wireToken) {
      case "cli" -> CLI;
      case "system" -> SYSTEM;
      default -> throw new AttestationAuthorizationException(failure);
    };
  }

  String wireToken() {
    return wireToken;
  }

  AttestationCredentialPurpose credentialPurpose() {
    return credentialPurpose;
  }
}
