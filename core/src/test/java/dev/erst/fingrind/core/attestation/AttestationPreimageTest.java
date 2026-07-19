package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit coverage for the deterministic immutable-preimage grammar. */
class AttestationPreimageTest {
  @Test
  void encoded_sortsRecordsByTagThenTheirCatalogDefinedCompleteSortKey() {
    AttestationPreimage preimage =
        AttestationPreimage.of(
            List.of(policyRule("beta", 1), command("post-entry"), policyRule("alpha", 2)));

    assertEquals(
        "0000000301000004010a706f73742d656e747279000001086f70657261746f72"
            + "01030002010462657461010001010300020105616c706861010002",
        HexFormat.of().formatHex(preimage.encoded()));
  }

  @Test
  void constructor_rejectsDuplicateCompleteCatalogSortKeys() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> AttestationPreimage.of(List.of(policyRule("post", 1), policyRule("post", 2))));

    assertEquals(
        "Attestation preimage must not contain duplicate complete sort keys.",
        exception.getMessage());
  }

  @Test
  void values_encodeEveryFixedAndLengthPrefixedWirePrimitive() {
    assertEncoded("01", AttestationNumericFieldValue.unsigned8(1));
    assertEncoded("ffff", AttestationNumericFieldValue.unsigned16(65_535));
    assertEncoded(
        "ffffffff", AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(4_294_967_295L)));
    assertEncoded(
        "ffffffffffffffff",
        AttestationNumericFieldValue.unsigned64(
            BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)));
    assertEncoded(
        "ffffffffffffffff", AttestationNumericFieldValue.signed64(BigInteger.ONE.negate()));
    assertEncoded("0765643235353139", AttestationTextFieldValue.token("ed25519"));
    assertEncoded("00000003636174", AttestationTextFieldValue.text("cat"));
    assertEncoded(
        "323032362d30372d3139",
        AttestationTextFieldValue.date(java.time.LocalDate.parse("2026-07-19")));
    assertEncoded(
        "323032362d30372d31395431323a33343a35362e3738395a",
        AttestationTextFieldValue.instant(Instant.parse("2026-07-19T12:34:56.789Z")));
    assertEncoded(
        "455552000000000000000000000000000000002a",
        AttestationNumericFieldValue.money("EUR", false, BigInteger.valueOf(42)));
    assertEncoded(
        "02010000000000000000000000000000002a",
        AttestationNumericFieldValue.scaled(2, true, BigInteger.valueOf(42)));
  }

  private static AttestationPreimage.Fact command(String operationKind) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(operationKind)),
            AttestationField.absent(),
            AttestationField.absent(),
            AttestationField.present(AttestationTextFieldValue.token("operator"))));
  }

  private static AttestationPreimage.Fact policyRule(String capability, int quorum) {
    return new AttestationPreimage.Fact(
        0x0103,
        List.of(
            AttestationField.present(AttestationTextFieldValue.token(capability)),
            AttestationField.present(AttestationNumericFieldValue.unsigned16(quorum))));
  }

  private static void assertEncoded(String expected, AttestationFieldValue value) {
    assertEquals(expected, HexFormat.of().formatHex(value.encoded()));
  }
}
