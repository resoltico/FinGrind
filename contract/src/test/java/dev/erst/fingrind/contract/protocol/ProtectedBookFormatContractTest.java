package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Invariant tests for the typed protected-book format contract. */
class ProtectedBookFormatContractTest {
  @Test
  void constructor_acceptsCanonicalValues() {
    ProtectedBookFormatContract contract =
        new ProtectedBookFormatContract(
            1_179_079_236, 14, BookCipher.CHACHA20, false, 4096, 32, 4096, 64007, 0);

    assertEquals(1_179_079_236, contract.applicationId());
    assertEquals(14, contract.formatVersion());
    assertEquals(BookCipher.CHACHA20, contract.cipher());
    assertFalse(contract.legacyMode());
    assertEquals(4096, contract.pageSize());
    assertEquals(32, contract.reservedBytes());
    assertEquals(4096, contract.legacyPageSize());
    assertEquals(64007, contract.kdfIter());
    assertEquals(0, contract.plaintextHeaderSize());
  }

  @Test
  void constructor_rejectsUnsupportedPageSizes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                1_179_079_236, 14, BookCipher.CHACHA20, false, 256, 32, 4096, 64007, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                1_179_079_236, 14, BookCipher.CHACHA20, false, 4096, 32, 131072, 64007, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                1_179_079_236, 14, BookCipher.CHACHA20, false, 3000, 32, 4096, 64007, 0));
  }

  @Test
  void constructor_rejectsNegativeReserveBytesNonPositiveKdfAndOutOfRangeHeaderSizes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                1_179_079_236, 14, BookCipher.CHACHA20, false, 4096, -1, 4096, 64007, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                -1, 14, BookCipher.CHACHA20, false, 4096, 32, 4096, 64007, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                1_179_079_236, 0, BookCipher.CHACHA20, false, 4096, 32, 4096, 64007, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                1_179_079_236, 14, BookCipher.CHACHA20, false, 4096, 32, 4096, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                1_179_079_236, 14, BookCipher.CHACHA20, false, 4096, 32, 4096, 64007, -1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookFormatContract(
                1_179_079_236, 14, BookCipher.CHACHA20, false, 4096, 32, 4096, 64007, 101));
  }
}
