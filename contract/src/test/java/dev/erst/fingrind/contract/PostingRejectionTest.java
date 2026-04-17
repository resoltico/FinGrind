package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingRejection}. */
class PostingRejectionTest {
  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "book-not-initialized",
            "account-state-violations",
            "duplicate-idempotency-key",
            "reversal-reason-required",
            "reversal-reason-forbidden",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
            PostingRejection.wireCode(
                new PostingRejection.AccountStateViolations(
                    List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
            PostingRejection.wireCode(new PostingRejection.DuplicateIdempotencyKey()),
            PostingRejection.wireCode(new PostingRejection.ReversalReasonRequired()),
            PostingRejection.wireCode(new PostingRejection.ReversalReasonForbidden()),
            PostingRejection.wireCode(
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-2"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-3")))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "book-not-initialized",
            "account-state-violations",
            "duplicate-idempotency-key",
            "reversal-reason-required",
            "reversal-reason-forbidden",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        PostingRejection.descriptors().stream()
            .map(MachineContract.RejectionDescriptor::code)
            .toList());
  }
}
