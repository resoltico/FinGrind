package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Verifies the immutable public operation-commitment value. */
class AttestationOperationCommitmentTest {
  @Test
  void ownsTheHeadDefensivelyAndRendersCanonicalLowercaseHex() {
    byte[] sourceHead = new byte[32];
    sourceHead[0] = (byte) 0xAB;
    AttestationOperationCommitment commitment =
        new AttestationOperationCommitment(BigInteger.valueOf(42), sourceHead);

    sourceHead[0] = 0;
    byte[] returnedHead = commitment.operationHead();
    returnedHead[1] = 1;

    assertEquals(BigInteger.valueOf(42), commitment.operationOrder());
    assertEquals("ab" + "00".repeat(31), commitment.operationHeadHex());
    assertArrayEquals(
        new byte[] {
          (byte) 0xAB,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0
        },
        commitment.operationHead());
  }

  @Test
  void rejectsOperationOrdersOutsideTheUnsigned64BitDomain() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationOperationCommitment(BigInteger.valueOf(-1), new byte[32]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationOperationCommitment(BigInteger.ONE.shiftLeft(Long.SIZE), new byte[32]));
  }

  @Test
  void rejectsOperationHeadsThatAreNotSha256Length() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationOperationCommitment(BigInteger.ZERO, new byte[31]));
  }
}
