package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import java.util.HexFormat;
import java.util.Objects;

/** Projects one verified newly appended operation into the public attestation commitment. */
public final class AttestationCommitProjection {
  private AttestationCommitProjection() {}

  /** Returns the exact durable chain position proved by one newly appended operation. */
  public static AttestationCommit fromVerifiedAppend(AttestationAppendOutcome.Appended append) {
    AttestationAppendOutcome.Appended checkedAppend = Objects.requireNonNull(append, "append");
    var checkedVerification = checkedAppend.verification();
    return new AttestationCommit(
        checkedVerification.headOrder(),
        HexFormat.of().formatHex(checkedVerification.operationHead()));
  }
}
