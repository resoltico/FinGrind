package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.bookkeeping.RequestFingerprintOwner;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Locks request fingerprints to caller intent before state-dependent posting resolution. */
class RequestFingerprintOwnerTest {
  @Test
  void callerAuthoredFingerprint_distinguishesDirectAndReversalIntent() {
    var direct = PostingApplicationServiceTestSupport.command("fingerprint-direct");
    var reversal =
        PostingApplicationServiceTestSupport.command(
            "fingerprint-reversal",
            Optional.of(
                new ReversalReference(
                    new dev.erst.fingrind.core.PostingId("6045a122-24cf-44c8-b85e-5f1f46e6ab36"))),
            Optional.of(new ReversalReason("customer cancellation")));

    assertNotEquals(fingerprint(direct), fingerprint(reversal));
  }

  private static dev.erst.fingrind.core.RequestFingerprint fingerprint(
      dev.erst.fingrind.contract.bookkeeping.PostEntryCommand command) {
    return RequestFingerprintOwner.fingerprintCallerAuthored(
        command.entry(), command.sourceChannel(), command.requestProvenance(), command.evidence());
  }
}
