package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verifies the canonical 128-bit identifier grammar used by publication transactions. */
class PublicationTransactionIdTest {
  @Test
  void fromEntropy_encodesExactlySixteenBytesAsLowercaseHexadecimal() {
    PublicationTransactionId identifier =
        PublicationTransactionId.fromEntropy(
            new byte[] {
              0x00,
              0x01,
              0x02,
              0x03,
              0x04,
              0x05,
              0x06,
              0x07,
              0x08,
              0x09,
              0x0a,
              0x0b,
              0x0c,
              0x0d,
              0x0e,
              0x0f
            });

    assertEquals("000102030405060708090a0b0c0d0e0f", identifier.value());
  }

  @Test
  void fresh_returnsTheCanonicalLowercase128BitGrammar() {
    PublicationTransactionId identifier = PublicationTransactionId.fresh();

    assertTrue(identifier.value().matches("[0-9a-f]{32}"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidValues")
  void constructor_rejectsEveryNoncanonicalIdentifier(String description, String value) {
    assertThrows(IllegalArgumentException.class, () -> new PublicationTransactionId(value), description);
  }

  @Test
  void constructor_requiresAValue() {
    assertThrows(NullPointerException.class, () -> new PublicationTransactionId(nullOf()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidEntropy")
  void fromEntropy_requiresExactlySixteenBytes(String description, byte[] entropy) {
    assertThrows(IllegalArgumentException.class, () -> PublicationTransactionId.fromEntropy(entropy), description);
  }

  @Test
  void fromEntropy_requiresEntropy() {
    assertThrows(NullPointerException.class, () -> PublicationTransactionId.fromEntropy(nullOf()));
  }

  private static Stream<Arguments> invalidValues() {
    return Stream.of(
        Arguments.of("blank", ""),
        Arguments.of("uppercase", "000102030405060708090A0B0C0D0E0F"),
        Arguments.of("hyphenated UUID", "00010203-0405-0607-0809-0a0b0c0d0e0f"),
        Arguments.of("too short", "000102030405060708090a0b0c0d0e"),
        Arguments.of("too long", "000102030405060708090a0b0c0d0e0f0"),
        Arguments.of("nonhexadecimal", "000102030405060708090a0b0c0d0e0g"),
        Arguments.of("surrounding whitespace", " 000102030405060708090a0b0c0d0e0f "));
  }

  private static Stream<Arguments> invalidEntropy() {
    return Stream.of(
        Arguments.of("too short", new byte[15]), Arguments.of("too long", new byte[17]));
  }
}
