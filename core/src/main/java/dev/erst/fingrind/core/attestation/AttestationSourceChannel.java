package dev.erst.fingrind.core.attestation;

/** Provenance channel whose operations require one credential purpose. */
enum AttestationSourceChannel {
  CLI(AttestationCredentialPurpose.OPERATOR),
  SYSTEM(AttestationCredentialPurpose.SYSTEM);

  private final AttestationCredentialPurpose credentialPurpose;

  AttestationSourceChannel(AttestationCredentialPurpose credentialPurpose) {
    this.credentialPurpose = credentialPurpose;
  }

  AttestationCredentialPurpose credentialPurpose() {
    return credentialPurpose;
  }
}
