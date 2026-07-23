package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.util.HexFormat;
import java.util.Objects;

/** Projects one verified newly appended operation into the public attestation commitment. */
public final class AttestationCommitProjection {
  private AttestationCommitProjection() {}

  /** Returns the exact durable chain position proved by the supplied append verification. */
  public static AttestationCommit fromVerifiedAppend(AttestationVerification verification) {
    AttestationVerification checkedVerification =
        Objects.requireNonNull(verification, "verification");
    return new AttestationCommit(
        checkedVerification.headOrder(),
        HexFormat.of().formatHex(checkedVerification.operationHead()));
  }
}
