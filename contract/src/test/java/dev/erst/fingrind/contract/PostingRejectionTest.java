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
            "posting-book-not-initialized",
            "account-state-violations",
            "duplicate-idempotency-key",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
            PostingRejection.wireCode(
                new PostingRejection.AccountStateViolations(
                    List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
            PostingRejection.wireCode(new PostingRejection.DuplicateIdempotencyKey()),
            PostingRejection.wireCode(
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-2"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-3")))));
  }

  @Test
  void accountStateViolationWireCode_isStableForEverySubtype() {
    assertEquals(
        List.of("unknown-account", "inactive-account"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.UnknownAccount(new AccountCode("1000"))),
            PostingRejection.wireCode(
                new PostingRejection.InactiveAccount(new AccountCode("2000")))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "account-state-violations",
            "duplicate-idempotency-key",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        PostingRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
  }

  @Test
  void bookNotInitializedCode_matchesTheCanonicalDescriptor() {
    assertEquals(
        PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
        PostingRejection.bookNotInitializedCode());
  }
}
