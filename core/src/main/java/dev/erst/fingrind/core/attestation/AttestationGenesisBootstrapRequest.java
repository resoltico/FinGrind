package dev.erst.fingrind.core.attestation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validates the signed genesis request against its immutable initial effect facts. */
final class AttestationGenesisBootstrapRequest {
  private static final int BOOK_IDENTITY_RECORD_TYPE = 0x0001;
  private static final int REQUEST_BOOK_IDENTITY_RECORD_TYPE = 0x0101;
  private static final int REQUEST_FOUNDER_RECORD_TYPE = 0x0102;
  private static final int REQUEST_POLICY_RULE_RECORD_TYPE = 0x0103;
  private static final int REQUEST_CAPABILITY_GRANT_RECORD_TYPE = 0x0183;
  private static final String GRANT_STATE = "grant";

  private AttestationGenesisBootstrapRequest() {}

  static void requireMatches(
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage,
      List<AttestationFounder> founders) {
    requireOnlyGenesisRecords(requestPreimage);
    requireBookIdentityDeclaration(requestPreimage, effectPreimage);
    requireFounderDeclarations(requestPreimage, founders);
    requirePolicyDeclarations(requestPreimage, founders.size());
    requireGrantDeclarations(requestPreimage, founders);
  }

  private static void requireOnlyGenesisRecords(AttestationPreimage requestPreimage) {
    for (AttestationPreimage.Fact record : requestPreimage.records()) {
      switch (record.recordTypeTag()) {
        case 0x0100,
            REQUEST_BOOK_IDENTITY_RECORD_TYPE,
            REQUEST_FOUNDER_RECORD_TYPE,
            REQUEST_POLICY_RULE_RECORD_TYPE,
            REQUEST_CAPABILITY_GRANT_RECORD_TYPE -> {}
        default -> throw failure();
      }
    }
  }

  private static void requireBookIdentityDeclaration(
      AttestationPreimage requestPreimage, AttestationPreimage effectPreimage) {
    List<AttestationPreimage.Fact> requestRecords =
        AttestationPreimageFields.records(requestPreimage, REQUEST_BOOK_IDENTITY_RECORD_TYPE);
    List<AttestationPreimage.Fact> effectRecords =
        AttestationPreimageFields.records(effectPreimage, BOOK_IDENTITY_RECORD_TYPE);
    if (requestRecords.size() != 1) {
      throw failure();
    }
    requireMatchingFields(requestRecords.getFirst(), 0, effectRecords.getFirst(), 1, 12);
  }

  private static void requireFounderDeclarations(
      AttestationPreimage requestPreimage, List<AttestationFounder> founders) {
    List<AttestationPreimage.Fact> requests =
        AttestationPreimageFields.records(requestPreimage, REQUEST_FOUNDER_RECORD_TYPE);
    if (requests.size() != founders.size()) {
      throw failure();
    }
    Set<AttestationFounder> declaredFounders = new HashSet<>();
    for (AttestationPreimage.Fact request : requests) {
      declaredFounders.add(requireFounderDeclaration(request));
    }
    if (!declaredFounders.equals(Set.copyOf(founders))) {
      throw failure();
    }
  }

  private static AttestationFounder requireFounderDeclaration(AttestationPreimage.Fact request) {
    if (!"operator".equals(AttestationPreimageValueReader.token(request, 3, failureType()))) {
      throw failure();
    }
    UUID principalId = AttestationPreimageValueReader.uuid(request, 0, failureType());
    AttestationHash keyId = AttestationPreimageValueReader.hash(request, 1, failureType());
    AttestationSpki spki = AttestationPreimageValueReader.spki(request, 2, failureType());
    if (!keyId.equals(AttestationHash.sha256(spki.bytes()))) {
      throw failure();
    }
    return new AttestationFounder(principalId, keyId, spki);
  }

  private static void requirePolicyDeclarations(
      AttestationPreimage requestPreimage, int founderCount) {
    List<AttestationPreimage.Fact> requests =
        AttestationPreimageFields.records(requestPreimage, REQUEST_POLICY_RULE_RECORD_TYPE);
    if (requests.size() != AttestationCapability.values().length) {
      throw failure();
    }
    for (AttestationPreimage.Fact request : requests) {
      AttestationCapability capability =
          capability(AttestationPreimageValueReader.token(request, 0, failureType()));
      if (AttestationPreimageValueReader.unsigned16(request, 1, failureType())
          != capability.genesisQuorum(founderCount)) {
        throw failure();
      }
    }
  }

  private static void requireGrantDeclarations(
      AttestationPreimage requestPreimage, List<AttestationFounder> founders) {
    List<AttestationPreimage.Fact> requests =
        AttestationPreimageFields.records(requestPreimage, REQUEST_CAPABILITY_GRANT_RECORD_TYPE);
    if (requests.size() != founders.size() * AttestationCapability.values().length) {
      throw failure();
    }
    Set<UUID> founderIds =
        founders.stream()
            .map(AttestationFounder::principalId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    for (AttestationPreimage.Fact request : requests) {
      requireGrantDeclaration(request, founderIds);
    }
  }

  private static void requireGrantDeclaration(
      AttestationPreimage.Fact request, Set<UUID> founderIds) {
    if (!GRANT_STATE.equals(AttestationPreimageValueReader.token(request, 2, failureType()))) {
      throw failure();
    }
    UUID principalId = AttestationPreimageValueReader.uuid(request, 0, failureType());
    if (!founderIds.contains(principalId)) {
      throw failure();
    }
    capability(AttestationPreimageValueReader.token(request, 1, failureType()));
  }

  private static void requireMatchingFields(
      AttestationPreimage.Fact left,
      int leftStart,
      AttestationPreimage.Fact right,
      int rightStart,
      int count) {
    for (int index = 0; index < count; index++) {
      AttestationPreimageFields.requireSameField(
          left, leftStart + index, right, rightStart + index, failureType());
    }
  }

  private static AttestationCapability capability(String token) {
    for (AttestationCapability capability : AttestationCapability.values()) {
      if (capability.token().equals(token)) {
        return capability;
      }
    }
    throw failure();
  }

  private static AttestationAuthorizationFailure failureType() {
    return AttestationAuthorizationFailure.GENESIS_INVALID;
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(failureType());
  }
}
