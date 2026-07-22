package dev.erst.fingrind.contract.bookkeeping;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Covers the public boundary for newly appended attestation-chain positions. */
class AttestationCommitTest {
  @Test
  void acceptsUnsignedOrdersAndCanonicalOperationHeads() {
    AttestationCommit commit = new AttestationCommit(BigInteger.ZERO, "a".repeat(64));

    assertEquals(BigInteger.ZERO, commit.operationOrder());
    assertEquals("a".repeat(64), commit.operationHeadHex());
  }

  @Test
  void rejectsInvalidOrdersAndOperationHeads() {
    assertThrows(NullPointerException.class, () -> new AttestationCommit(nullOf(), "a".repeat(64)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationCommit(BigInteger.ONE.negate(), "a".repeat(64)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationCommit(BigInteger.ONE.shiftLeft(Long.SIZE), "a".repeat(64)));
    assertThrows(
        NullPointerException.class, () -> new AttestationCommit(BigInteger.ZERO, nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationCommit(BigInteger.ZERO, "A".repeat(64)));
  }
}
