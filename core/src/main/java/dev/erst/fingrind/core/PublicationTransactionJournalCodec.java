package dev.erst.fingrind.core;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Authenticates and validates the one canonical JSON representation of a transaction journal. */
final class PublicationTransactionJournalCodec {
  private static final JsonMapper STRICT_JSON =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();
  private static final int OWNER_KEY_BYTES = 32;

  private PublicationTransactionJournalCodec() {}

  /**
   * Returns the one canonical authenticated UTF-8 journal representation for the supplied payload.
   */
  static byte[] encode(PublicationTransactionJournal journal, byte[] ownerKey) {
    PublicationTransactionJournal checkedJournal = Objects.requireNonNull(journal, "journal");
    byte[] checkedOwnerKey = requireOwnerKey(ownerKey);
    byte[] payloadBytes =
        PublicationTransactionJournalJsonWriter.payload(STRICT_JSON, checkedJournal);
    String integrity =
        HexFormat.of().formatHex(CryptographicPrimitives.hmacSha256(checkedOwnerKey, payloadBytes));
    return PublicationTransactionJournalJsonWriter.authenticated(
        STRICT_JSON, checkedJournal, integrity);
  }

  /**
   * Returns an owned journal only after strict parsing, HMAC verification, and byte canonicality.
   */
  static PublicationTransactionJournal decode(byte[] encodedJournal, byte[] ownerKey)
      throws PublicationTransactionJournalViolation {
    byte[] checkedEncodedJournal = Objects.requireNonNull(encodedJournal, "encodedJournal");
    byte[] checkedOwnerKey = requireOwnerKey(ownerKey);
    PublicationTransactionJournalJsonReader.ParsedJournal parsed =
        PublicationTransactionJournalJsonReader.read(STRICT_JSON, checkedEncodedJournal);
    byte[] expectedIntegrity =
        CryptographicPrimitives.hmacSha256(
            checkedOwnerKey,
            PublicationTransactionJournalJsonWriter.payload(STRICT_JSON, parsed.journal()));
    byte[] actualIntegrity = HexFormat.of().parseHex(parsed.integrity());
    if (!CryptographicPrimitives.constantTimeEquals(expectedIntegrity, actualIntegrity)) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.INTEGRITY,
          "Publication transaction journal integrity did not verify.");
    }
    if (!Arrays.equals(checkedEncodedJournal, encode(parsed.journal(), checkedOwnerKey))) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.NON_CANONICAL,
          "Publication transaction journal bytes are not in the required canonical representation.");
    }
    return parsed.journal();
  }

  private static byte[] requireOwnerKey(byte[] ownerKey) {
    byte[] checkedOwnerKey = Objects.requireNonNull(ownerKey, "ownerKey");
    if (checkedOwnerKey.length != OWNER_KEY_BYTES) {
      throw new IllegalArgumentException(
          "Publication transaction owner key must contain exactly 32 bytes.");
    }
    return checkedOwnerKey.clone();
  }
}
