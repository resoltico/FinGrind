package dev.erst.fingrind.core.attestation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validates the immutable effect facts that establish the initial attestation registry. */
final class AttestationGenesisBootstrapEffect {
  private static final int BOOK_IDENTITY_RECORD_TYPE = 0x0001;
  private static final int CREDENTIAL_BINDING_RECORD_TYPE = 0x0002;
  private static final int CREATE_MUTATION = 0;

  private AttestationGenesisBootstrapEffect() {}

  static List<AttestationFounder> requireValid(UUID bookId, AttestationPreimage effectPreimage) {
    List<AttestationFounder> founders = founders(effectPreimage);
    AttestationGenesisInitialRegistry.requireValid(effectPreimage, founders);
    requireExactEffectShape(effectPreimage, founders.size());
    requireBookIdentity(effectPreimage, bookId);
    requireDistinctFounders(founders);
    return founders;
  }

  private static List<AttestationFounder> founders(AttestationPreimage effectPreimage) {
    List<AttestationPreimage.Fact> bindings =
        AttestationPreimageFields.records(effectPreimage, CREDENTIAL_BINDING_RECORD_TYPE);
    if (bindings.isEmpty() || bindings.size() > 5) {
      throw failure();
    }
    return bindings.stream().map(AttestationGenesisBootstrapEffect::founder).toList();
  }

  private static AttestationFounder founder(AttestationPreimage.Fact binding) {
    if (AttestationPreimageValueReader.mutation(binding, 0, failureType()) != CREATE_MUTATION
        || !"enroll".equals(AttestationPreimageValueReader.token(binding, 3, failureType()))
        || !"operator".equals(AttestationPreimageValueReader.token(binding, 5, failureType()))) {
      throw failure();
    }
    AttestationPreimageFields.requireAbsent(binding, 6, failureType());
    UUID principalId = AttestationPreimageValueReader.uuid(binding, 1, failureType());
    AttestationHash keyId = AttestationPreimageValueReader.hash(binding, 2, failureType());
    AttestationSpki spki = AttestationPreimageValueReader.spki(binding, 4, failureType());
    if (!keyId.equals(AttestationHash.sha256(spki.bytes()))) {
      throw failure();
    }
    return new AttestationFounder(principalId, keyId, spki);
  }

  private static void requireExactEffectShape(
      AttestationPreimage effectPreimage, int founderCount) {
    int expectedRecordCount =
        1
            + founderCount
            + founderCount * AttestationCapability.values().length
            + AttestationCapability.values().length;
    if (effectPreimage.records().size() != expectedRecordCount) {
      throw failure();
    }
  }

  private static void requireBookIdentity(AttestationPreimage effectPreimage, UUID bookId) {
    List<AttestationPreimage.Fact> bookIdentityRecords =
        AttestationPreimageFields.records(effectPreimage, BOOK_IDENTITY_RECORD_TYPE);
    if (bookIdentityRecords.size() != 1
        || AttestationPreimageValueReader.mutation(bookIdentityRecords.getFirst(), 0, failureType())
            != CREATE_MUTATION
        || !bookId.equals(
            AttestationPreimageValueReader.uuid(
                bookIdentityRecords.getFirst(), 1, failureType()))) {
      throw failure();
    }
  }

  private static void requireDistinctFounders(List<AttestationFounder> founders) {
    Set<AttestationHash> keyIds = new HashSet<>();
    for (AttestationFounder founder : founders) {
      if (!keyIds.add(founder.keyId())) {
        throw failure();
      }
    }
  }

  private static AttestationAuthorizationFailure failureType() {
    return AttestationAuthorizationFailure.GENESIS_INVALID;
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(failureType());
  }
}
