package dev.erst.fingrind.testsupport;

import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Builds deterministic verified-append evidence for test-only durable posting fixtures. */
public final class AttestationVerificationTestFixtures {
  private static final UUID BOOK_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");
  private static final byte[] OPERATION_HEAD = HexFormat.of().parseHex("a".repeat(64));

  private AttestationVerificationTestFixtures() {}

  /** Returns one verified append suitable for a test fixture's fresh durable mutation. */
  public static AttestationVerification verifiedAppend() {
    return new AttestationVerification(
        BOOK_ID, BigInteger.ONE, OPERATION_HEAD, new byte[32], List.of());
  }
}
