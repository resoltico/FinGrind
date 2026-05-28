package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Encodes and normalizes raw passphrase material into FinGrind's owned UTF-8 form. */
final class SqliteBookPassphraseEncoding {
  private SqliteBookPassphraseEncoding() {}

  static CharsetEncoder utf8Encoder() {
    return StandardCharsets.UTF_8
        .newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  static ContractDecision<SqliteBookPassphrase> fromCharactersDecision(
      String normalizedSource, char[] characters, CharsetEncoder encoder) {
    ByteBuffer encodedBytes = ByteBuffer.allocate(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES + 1);
    try {
      CoderResult encodeResult = encoder.encode(CharBuffer.wrap(characters), encodedBytes, true);
      if (encodeResult.isOverflow()) {
        return ContractDecision.rejected(
            SqliteBookPassphraseValidation.oversizedPassphraseSourceFailure(normalizedSource));
      }
      if (encodeResult.isError()) {
        return ContractDecision.rejected(
            SqliteBookPassphraseValidation.invalidUtf8PassphraseSourceFailure(normalizedSource));
      }
      encoder.flush(encodedBytes);
      encodedBytes.flip();
      byte[] copiedBytes = new byte[encodedBytes.remaining()];
      encodedBytes.get(copiedBytes);
      return SqliteBookPassphrase.fromUtf8BytesDecision(normalizedSource, copiedBytes);
    } finally {
      SqliteBookPassphraseZeroization.zeroize(encodedBytes);
      Arrays.fill(characters, '\0');
    }
  }

  static ContractDecision<byte[]> normalizeLoadedBytesDecision(
      byte[] loadedBytes, String sourceDescription) {
    if (loadedBytes.length > SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES) {
      return ContractDecision.rejected(
          SqliteBookPassphraseValidation.oversizedPassphraseSourceFailure(sourceDescription));
    }
    ContractDecision<Integer> normalizedLengthDecision =
        SqliteBookPassphraseValidation.normalizedPassphraseLength(loadedBytes, sourceDescription);
    return switch (normalizedLengthDecision) {
      case ContractDecision.Accepted<Integer>(Integer normalizedLengthBoxed) -> {
        int normalizedLength = normalizedLengthBoxed.intValue();
        yield SqliteBookPassphraseValidation.validateTextPassphrase(
                loadedBytes, normalizedLength, sourceDescription)
            .fold(
                ignored -> ContractDecision.accepted(ownedBytes(loadedBytes, normalizedLength)),
                ContractDecision::rejected);
      }
      case ContractDecision.Rejected<Integer>(var failure) -> ContractDecision.rejected(failure);
    };
  }

  private static byte[] ownedBytes(byte[] loadedBytes, int normalizedLength) {
    if (normalizedLength == loadedBytes.length) {
      return loadedBytes;
    }
    byte[] trimmedBytes = Arrays.copyOf(loadedBytes, normalizedLength);
    Arrays.fill(loadedBytes, (byte) 0);
    return trimmedBytes;
  }
}
