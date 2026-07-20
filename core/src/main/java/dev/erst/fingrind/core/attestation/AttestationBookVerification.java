package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Successful immutable-book verification result and its nonfatal compromise-review findings. */
final class AttestationBookVerification {
  private final UUID bookId;
  private final List<VerifiedOperation> operations;
  private final AttestationRegistry registry;
  private final List<AttestationReviewFinding> reviewFindings;

  AttestationBookVerification(
      UUID bookId,
      List<VerifiedOperation> operations,
      AttestationRegistry registry,
      List<AttestationReviewFinding> reviewFindings) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
    this.registry = Objects.requireNonNull(registry, "registry");
    this.reviewFindings = List.copyOf(Objects.requireNonNull(reviewFindings, "reviewFindings"));
    if (this.operations.isEmpty()) {
      throw new IllegalArgumentException("A verified book must contain genesis.");
    }
  }

  UUID bookId() {
    return bookId;
  }

  BigInteger headOrder() {
    return operations.getLast().operationOrder();
  }

  AttestationHash head() {
    return operations.getLast().head();
  }

  AttestationHash headAt(BigInteger operationOrder) {
    return operationAt(operationOrder).head();
  }

  VerifiedOperation operationAt(BigInteger operationOrder) {
    BigInteger checkedOrder = Objects.requireNonNull(operationOrder, "operationOrder");
    if (checkedOrder.signum() < 0
        || checkedOrder.compareTo(BigInteger.valueOf(operations.size() - 1L)) > 0) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.PREIMAGE_INVALID);
    }
    VerifiedOperation operation = operations.get(checkedOrder.intValueExact());
    if (!operation.operationOrder().equals(checkedOrder)) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.PREIMAGE_INVALID);
    }
    return operation;
  }

  AttestationRegistry registry() {
    return registry;
  }

  List<AttestationReviewFinding> reviewFindings() {
    return reviewFindings;
  }

  /** One accepted chain position and its raw head. */
  record VerifiedOperation(
      BigInteger operationOrder, AttestationHash head, AttestationBookOperation operation) {
    VerifiedOperation {
      operationOrder =
          AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
      Objects.requireNonNull(head, "head");
      Objects.requireNonNull(operation, "operation");
    }
  }
}
