package dev.erst.fingrind.core.attestation;

/** Closed version-one cryptographic algorithm catalog. */
enum AttestationAlgorithm {
  ED25519("ed25519", "Ed25519", AttestationSignatureEntry.SIGNATURE_BYTE_LENGTH);

  private final String id;
  private final String jcaName;
  private final int signatureByteLength;

  AttestationAlgorithm(String id, String jcaName, int signatureByteLength) {
    this.id = id;
    this.jcaName = jcaName;
    this.signatureByteLength = signatureByteLength;
  }

  String id() {
    return id;
  }

  String jcaName() {
    return jcaName;
  }

  int signatureByteLength() {
    return signatureByteLength;
  }
}
