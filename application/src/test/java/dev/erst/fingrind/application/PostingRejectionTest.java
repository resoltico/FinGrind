package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingRejection}. */
class PostingRejectionTest {
  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "book-not-initialized",
            "unknown-account",
            "inactive-account",
            "duplicate-idempotency-key",
            "reversal-reason-required",
            "reversal-reason-forbidden",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
            PostingRejection.wireCode(new PostingRejection.UnknownAccount(new AccountCode("1000"))),
            PostingRejection.wireCode(
                new PostingRejection.InactiveAccount(new AccountCode("2000"))),
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
  void descriptors_guardAgainstUnsupportedSubtypeRequests()
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle descriptorFor =
        MethodHandles.privateLookupIn(PostingRejection.class, MethodHandles.lookup())
            .findStatic(
                PostingRejection.class,
                "descriptorFor",
                MethodType.methodType(MachineContract.RejectionDescriptor.class, Class.class));

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class, () -> descriptorFor.invoke(PostingRejection.class));
    assertEquals(
        "Unsupported posting rejection type: " + PostingRejection.class.getName(),
        failure.getMessage());
  }
}
