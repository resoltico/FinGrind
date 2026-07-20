package dev.erst.fingrind.core.attestation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Verified order-zero bootstrap facts bound to one genesis operation payload. */
final class AttestationGenesisAuthorizationContext {
  private static final byte[] ZERO_HEAD = new byte[AttestationHash.BYTE_LENGTH];

  private final byte[] payload;
  private final List<AttestationFounder> founders;

  private AttestationGenesisAuthorizationContext(
      AttestationOperationPayload payload, List<AttestationFounder> founders) {
    this.payload = payload.encoded();
    this.founders = List.copyOf(founders);
  }

  static AttestationGenesisAuthorizationContext verify(
      AttestationOperationPayload payload,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    AttestationOperationPayload checkedPayload = Objects.requireNonNull(payload, "payload");
    AttestationPreimage checkedRequestPreimage =
        Objects.requireNonNull(requestPreimage, "requestPreimage");
    AttestationPreimage checkedEffectPreimage =
        Objects.requireNonNull(effectPreimage, "effectPreimage");
    requireGenesisPayload(checkedPayload);
    requireCliRequestProvenance(checkedPayload, checkedRequestPreimage);
    if (!checkedPayload
        .effectDigest()
        .equals(AttestationHash.sha256(checkedEffectPreimage.encoded()))) {
      throw failure();
    }
    List<AttestationFounder> founders =
        AttestationGenesisBootstrapEffect.requireValid(
            checkedPayload.bookId(), checkedEffectPreimage);
    AttestationGenesisBootstrapRequest.requireMatches(
        checkedRequestPreimage, checkedEffectPreimage, founders);
    return new AttestationGenesisAuthorizationContext(checkedPayload, founders);
  }

  boolean matchesPayload(byte[] candidate) {
    return Arrays.equals(payload, candidate);
  }

  byte[] payload() {
    return payload.clone();
  }

  List<AttestationFounder> founders() {
    return founders;
  }

  private static void requireGenesisPayload(AttestationOperationPayload payload) {
    if (payload.operationOrder().signum() != 0
        || !AttestationOperationKind.BOOK_GENESIS.wireToken().equals(payload.operationKind())
        || !Arrays.equals(payload.previousHead().bytes(), ZERO_HEAD)) {
      throw failure();
    }
  }

  private static void requireCliRequestProvenance(
      AttestationOperationPayload payload, AttestationPreimage requestPreimage) {
    if (AttestationVerifiedOperationProvenance.verify(payload, requestPreimage).sourceChannel()
        != AttestationSourceChannel.CLI) {
      throw failure();
    }
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(AttestationAuthorizationFailure.GENESIS_INVALID);
  }
}
