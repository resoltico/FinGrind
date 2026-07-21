package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates one canonical signed operation envelope from previously projected immutable preimages.
 */
public final class AttestationOperationSigner {
  private AttestationOperationSigner() {}

  /**
   * Signs an exact immutable operation projection.
   *
   * <p>The recorded time is canonicalized to milliseconds here, at the single signing boundary,
   * before it enters the immutable payload. Authorization is deliberately checked at the
   * transactional CAS boundary against the
   * persisted historical registry and policy. This method only constructs the canonical evidence
   * that is later verified there.
   */
  public static AttestationEvidence sign(
      UUID bookId,
      BigInteger operationOrder,
      String operationKind,
      byte[] previousHead,
      Instant recordedAt,
      byte[] requestPreimage,
      byte[] effectPreimage,
      List<AttestationSigningCredential> signers) {
    AttestationPreimage checkedRequest =
        AttestationPreimage.decode(
            Objects.requireNonNull(requestPreimage, "requestPreimage"),
            AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationPreimage checkedEffect =
        AttestationPreimage.decode(
            Objects.requireNonNull(effectPreimage, "effectPreimage"),
            AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            Objects.requireNonNull(bookId, "bookId"),
            Objects.requireNonNull(operationOrder, "operationOrder"),
            Objects.requireNonNull(operationKind, "operationKind"),
            AttestationHash.of(Objects.requireNonNull(previousHead, "previousHead")),
            Objects.requireNonNull(recordedAt, "recordedAt").truncatedTo(ChronoUnit.MILLIS),
            AttestationHash.sha256(checkedRequest.encoded()),
            AttestationHash.sha256(checkedEffect.encoded()));
    List<AttestationSignatureEntry> entries =
        List.copyOf(Objects.requireNonNull(signers, "signers")).stream()
            .map(
                signer ->
                    Objects.requireNonNull(signer, "signers must not contain null")
                        .sign(payload.encoded()))
            .toList();
    return new AttestationEvidence(
        AttestationEnvelope.of(payload, entries).encoded(),
        checkedRequest.encoded(),
        checkedEffect.encoded());
  }
}
