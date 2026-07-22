package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Covers the immutable diagnostic payload of the stale-head admission refusal. */
class AttestationStaleHeadExceptionTest {
  @Test
  void preservesDefensiveCopiesOfBothHeads() {
    byte[] observedHead = new byte[] {1, 2, 3};
    byte[] currentHead = new byte[] {4, 5, 6};

    AttestationStaleHeadException exception =
        new AttestationStaleHeadException(observedHead, currentHead, BigInteger.TWO);
    observedHead[0] = 9;
    currentHead[0] = 9;
    byte[] returnedObservedHead = exception.observedHead();
    byte[] returnedCurrentHead = exception.currentHead();
    returnedObservedHead[1] = 9;
    returnedCurrentHead[1] = 9;

    assertEquals("stale-head", exception.getMessage());
    assertEquals(BigInteger.TWO, exception.currentOrder());
    assertArrayEquals(new byte[] {1, 2, 3}, exception.observedHead());
    assertArrayEquals(new byte[] {4, 5, 6}, exception.currentHead());
  }
}
