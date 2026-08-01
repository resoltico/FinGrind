package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** Validates a requested registry mutation against one authenticated authority head. */
final class AttestationRegistryMutationAdmission {
  private AttestationRegistryMutationAdmission() {}

  static void require(
      AttestationRegistryResolution resolution,
      AttestationRegistryMutation mutation,
      BigInteger currentHeadOrder) {
    AttestationRegistryResolution checkedResolution =
        Objects.requireNonNull(resolution, "resolution");
    AttestationRegistryMutation checkedMutation = Objects.requireNonNull(mutation, "mutation");
    BigInteger checkedHeadOrder = Objects.requireNonNull(currentHeadOrder, "currentHeadOrder");
    switch (checkedMutation) {
      case AttestationRegistryMutation.EnrollKey enrollment -> {
        requireNewPrincipal(checkedResolution, enrollment.principalId(), checkedHeadOrder);
        requireNewCredential(checkedResolution, enrollment.credential(), checkedHeadOrder);
      }
      case AttestationRegistryMutation.RolloverKey rollover -> {
        requireNewCredential(checkedResolution, rollover.credential(), checkedHeadOrder);
        requireActiveCredential(
            checkedResolution,
            rollover.principalId(),
            rollover.predecessorCredential(),
            checkedHeadOrder);
      }
      case AttestationRegistryMutation.RevokeKey revocation ->
          requireActiveCredential(
              checkedResolution,
              revocation.principalId(),
              revocation.credential(),
              checkedHeadOrder);
      case AttestationRegistryMutation.AlterPolicy ignored -> {
        // Candidate verification validates the resulting policy and workflow history.
      }
    }
  }

  private static void requireNewPrincipal(
      AttestationRegistryResolution resolution, UUID principalId, BigInteger currentHeadOrder) {
    if (resolution.hasCredentialBindingForPrincipal(principalId, currentHeadOrder)) {
      throw new AttestationAuthorizationException(
          AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL);
    }
  }

  private static void requireNewCredential(
      AttestationRegistryResolution resolution,
      AttestationPublicCredential credential,
      BigInteger currentHeadOrder) {
    AttestationHash keyId = AttestationHash.of(credential.keyId());
    if (resolution.credentialAt(keyId, currentHeadOrder).binding() != null) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.DUPLICATE_KEY);
    }
  }

  private static void requireActiveCredential(
      AttestationRegistryResolution resolution,
      UUID principalId,
      AttestationPublicCredential credential,
      BigInteger currentHeadOrder) {
    AttestationCredentialState state =
        resolution.credentialAt(AttestationHash.of(credential.keyId()), currentHeadOrder);
    if (state.binding() == null) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.KEY_NOT_ENROLLED);
    }
    if (!state.requireBinding().principalId().equals(principalId)) {
      throw new AttestationAuthorizationException(
          AttestationAuthorizationFailure.KEY_PRINCIPAL_MISMATCH);
    }
    if (state.revoked()) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.KEY_REVOKED);
    }
    if (state.superseded()) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.KEY_SUPERSEDED);
    }
  }
}
