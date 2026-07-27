package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Applies the shared envelope authorization order at one resolving attestation position. */
final class AttestationAuthorization {
  private AttestationAuthorization() {}

  static void requireAuthorized(
      AttestationRegistry registry,
      AttestationAuthorizationContext context,
      AttestationAuthorizationEnvelope envelope) {
    Objects.requireNonNull(registry, "registry");
    AttestationAuthorizationContext checkedContext = Objects.requireNonNull(context, "context");
    AttestationAuthorizationEnvelope checkedEnvelope = Objects.requireNonNull(envelope, "envelope");
    if (!checkedContext.matchesPayload(checkedEnvelope.payload())) {
      throw failure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
    }
    List<AttestationSignatureEntry> entries = checkedEnvelope.entries();
    BigInteger resolvingOrder = checkedContext.resolvingOrder();
    int quorum = registry.quorumAt(checkedContext.capability(), resolvingOrder);
    requireExactQuorum(entries, quorum);
    requireDistinct(entries);
    requireAscending(entries);
    requireAlgorithm(checkedContext.algorithmId());

    List<AttestationCredentialState> credentials =
        entries.stream().map(entry -> requireCredential(registry, entry, resolvingOrder)).toList();
    requireSignatures(entries, credentials, checkedEnvelope.payload());
    requireCapability(registry, entries, checkedContext.capability(), resolvingOrder, quorum);
    requireCredentialPurpose(credentials, checkedContext.sourceChannel());
    requireSystemWorkflow(registry, checkedContext, resolvingOrder);
  }

  static void requireGenesis(
      AttestationGenesisAuthorizationContext context, AttestationAuthorizationEnvelope envelope) {
    AttestationGenesisAuthorizationContext checkedContext =
        Objects.requireNonNull(context, "context");
    AttestationAuthorizationEnvelope checkedEnvelope = Objects.requireNonNull(envelope, "envelope");
    if (!checkedContext.matchesPayload(checkedEnvelope.payload())) {
      throw failure(AttestationAuthorizationFailure.GENESIS_INVALID);
    }
    List<AttestationFounder> checkedFounders = checkedContext.founders();
    List<AttestationSignatureEntry> entries = checkedEnvelope.entries();
    if (entries.size() != checkedFounders.size()) {
      throw failure(AttestationAuthorizationFailure.GENESIS_INVALID);
    }
    requireGenesisEnvelopeShape(entries);
    requireAlgorithm(checkedContext.algorithmId());
    requireGenesisSignatures(
        AttestationRegistry.genesis(checkedFounders), entries, checkedEnvelope.payload());
  }

  private static void requireGenesisSignatures(
      AttestationRegistry genesis, List<AttestationSignatureEntry> entries, byte[] payload) {
    for (AttestationSignatureEntry entry : entries) {
      AttestationCredentialState state = genesis.credentialAt(entry.keyId(), BigInteger.ZERO);
      if (!state.active()) {
        throw failure(AttestationAuthorizationFailure.GENESIS_INVALID);
      }
      AttestationCredentialBinding binding = state.requireBinding();
      if (!binding.principalId().equals(entry.principalId())) {
        throw failure(AttestationAuthorizationFailure.GENESIS_INVALID);
      }
      if (!AttestationEd25519.verifies(binding.spki().bytes(), payload, entry.signature())) {
        throw failure(AttestationAuthorizationFailure.GENESIS_INVALID);
      }
    }
  }

  private static void requireGenesisEnvelopeShape(List<AttestationSignatureEntry> entries) {
    Set<UUID> principalIds = new HashSet<>();
    Set<AttestationHash> keyIds = new HashSet<>();
    for (int index = 0; index < entries.size(); index++) {
      AttestationSignatureEntry entry = entries.get(index);
      if (!principalIds.add(entry.principalId()) || !keyIds.add(entry.keyId())) {
        throw failure(AttestationAuthorizationFailure.GENESIS_INVALID);
      }
      if (index > 0 && entries.get(index - 1).keyId().compareTo(entry.keyId()) >= 0) {
        throw failure(AttestationAuthorizationFailure.GENESIS_INVALID);
      }
    }
  }

  private static void requireExactQuorum(List<AttestationSignatureEntry> entries, int quorum) {
    if (entries.size() < quorum) {
      throw failure(AttestationAuthorizationFailure.QUORUM_BELOW);
    }
    if (entries.size() > quorum) {
      throw failure(AttestationAuthorizationFailure.QUORUM_EXCESS);
    }
  }

  private static void requireDistinct(List<AttestationSignatureEntry> entries) {
    Set<UUID> principalIds = new HashSet<>();
    Set<AttestationHash> keyIds = new HashSet<>();
    for (AttestationSignatureEntry entry : entries) {
      if (!principalIds.add(entry.principalId())) {
        throw failure(AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL);
      }
      if (!keyIds.add(entry.keyId())) {
        throw failure(AttestationAuthorizationFailure.DUPLICATE_KEY);
      }
    }
  }

  private static void requireAscending(List<AttestationSignatureEntry> entries) {
    for (int index = 1; index < entries.size(); index++) {
      if (entries.get(index - 1).keyId().compareTo(entries.get(index).keyId()) >= 0) {
        throw failure(AttestationAuthorizationFailure.ENVELOPE_ORDER_INVALID);
      }
    }
  }

  private static void requireAlgorithm(String algorithmId) {
    if (!AttestationAlgorithm.ED25519.id().equals(algorithmId)) {
      throw failure(AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID);
    }
  }

  private static AttestationCredentialState requireCredential(
      AttestationRegistry registry, AttestationSignatureEntry entry, BigInteger resolvingOrder) {
    AttestationCredentialState credential = registry.credentialAt(entry.keyId(), resolvingOrder);
    if (credential.binding() == null) {
      throw failure(AttestationAuthorizationFailure.KEY_NOT_ENROLLED);
    }
    if (!AttestationEd25519.isEd25519Spki(credential.binding().spki().bytes())) {
      throw failure(AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID);
    }
    if (credential.revoked()) {
      throw failure(AttestationAuthorizationFailure.KEY_REVOKED);
    }
    if (credential.superseded()) {
      throw failure(AttestationAuthorizationFailure.KEY_SUPERSEDED);
    }
    if (!credential.binding().principalId().equals(entry.principalId())) {
      throw failure(AttestationAuthorizationFailure.KEY_PRINCIPAL_MISMATCH);
    }
    return credential;
  }

  private static void requireSignatures(
      List<AttestationSignatureEntry> entries,
      List<AttestationCredentialState> credentials,
      byte[] payload) {
    for (int index = 0; index < entries.size(); index++) {
      AttestationCredentialBinding binding = credentials.get(index).requireBinding();
      if (!AttestationEd25519.verifies(
          binding.spki().bytes(), payload, entries.get(index).signature())) {
        throw failure(AttestationAuthorizationFailure.SIGNATURE_INVALID);
      }
    }
  }

  private static void requireCapability(
      AttestationRegistry registry,
      List<AttestationSignatureEntry> entries,
      AttestationCapability capability,
      BigInteger resolvingOrder,
      int quorum) {
    if (registry.eligiblePrincipalCount(capability, resolvingOrder) < quorum) {
      throw failure(AttestationAuthorizationFailure.CAPABILITY_INVALID);
    }
    for (AttestationSignatureEntry entry : entries) {
      if (!registry.isEligible(entry.principalId(), capability, resolvingOrder)) {
        throw failure(AttestationAuthorizationFailure.CAPABILITY_INVALID);
      }
    }
  }

  private static void requireCredentialPurpose(
      List<AttestationCredentialState> credentials,
      @org.jspecify.annotations.Nullable AttestationSourceChannel sourceChannel) {
    if (sourceChannel == null) {
      return;
    }
    for (AttestationCredentialState credential : credentials) {
      if (credential.requireBinding().purpose() != sourceChannel.credentialPurpose()) {
        throw failure(AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID);
      }
    }
  }

  private static void requireSystemWorkflow(
      AttestationRegistry registry,
      AttestationAuthorizationContext context,
      BigInteger resolvingOrder) {
    AttestationSystemWorkflowKind requiredWorkflowKind = context.requiredSystemWorkflowKind();
    if (requiredWorkflowKind != null
        && !registry.hasActiveSystemWorkflow(
            Objects.requireNonNull(context.systemWorkflowId(), "systemWorkflowId"),
            requiredWorkflowKind,
            resolvingOrder)) {
      throw failure(AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID);
    }
  }

  private static AttestationAuthorizationException failure(
      AttestationAuthorizationFailure failure) {
    return new AttestationAuthorizationException(failure);
  }
}
