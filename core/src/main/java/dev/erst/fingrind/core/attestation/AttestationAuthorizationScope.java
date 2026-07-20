package dev.erst.fingrind.core.attestation;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Names the capability and optional source-channel purpose for one signed structure. */
record AttestationAuthorizationScope(
    AttestationCapability capability, @Nullable AttestationSourceChannel sourceChannel) {
  AttestationAuthorizationScope {
    Objects.requireNonNull(capability, "capability");
  }

  static AttestationAuthorizationScope operation(
      AttestationOperationKind operationKind, AttestationSourceChannel sourceChannel) {
    return new AttestationAuthorizationScope(
        AttestationCapability.forOperation(operationKind),
        Objects.requireNonNull(sourceChannel, "sourceChannel"));
  }

  static AttestationAuthorizationScope manifest() {
    return new AttestationAuthorizationScope(AttestationCapability.BACKUP, null);
  }

  static AttestationAuthorizationScope receipt() {
    return new AttestationAuthorizationScope(AttestationCapability.ANCHOR, null);
  }
}
