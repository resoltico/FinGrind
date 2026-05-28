package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Validates public passphrase-source grammar and canonical text rules. */
final class SqliteBookPassphraseValidation {
  private SqliteBookPassphraseValidation() {}

  static String normalizeSourceDescription(String sourceDescription) {
    Objects.requireNonNull(sourceDescription, "sourceDescription");
    String normalized = sourceDescription.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("sourceDescription must not be blank.");
    }
    return normalized;
  }

  static ContractFailure oversizedPassphraseSourceFailure(String sourceDescription) {
    return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
        "The FinGrind book passphrase source exceeded the %d-byte UTF-8 limit: %s"
            .formatted(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES, sourceDescription),
        "Provide one non-empty single-line UTF-8 passphrase within the %d-byte limit through the selected passphrase source and rerun the command."
            .formatted(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES),
        null);
  }

  static ContractDecision<Integer> normalizedPassphraseLength(
      byte[] loadedBytes, String sourceDescription) {
    int endIndex = loadedBytes.length;
    if (endIndex > 0 && loadedBytes[endIndex - 1] == '\n') {
      endIndex--;
      if (endIndex > 0 && loadedBytes[endIndex - 1] == '\r') {
        endIndex--;
      }
    }
    if (endIndex == 0) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
              "The FinGrind book passphrase source must contain a non-empty UTF-8 passphrase: "
                  + sourceDescription,
              "Provide one non-empty UTF-8 passphrase through the selected key file, standard input, or interactive prompt route.",
              null));
    }
    return ContractDecision.accepted(endIndex);
  }

  static ContractDecision<String> validateTextPassphrase(
      byte[] keyBytes, int keyLength, String sourceDescription) {
    CharBuffer decoded;
    try {
      decoded =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(keyBytes, 0, keyLength));
    } catch (CharacterCodingException exception) {
      return ContractDecision.rejected(invalidUtf8PassphraseSourceFailure(sourceDescription));
    }
    try {
      int offset = 0;
      while (offset < decoded.length()) {
        int codePoint = Character.codePointAt(decoded, offset);
        if (Character.isISOControl(codePoint)) {
          return ContractDecision.rejected(
              ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
                  "The FinGrind book passphrase source must contain a single-line UTF-8 text passphrase without control characters: "
                      + sourceDescription,
                  "Provide one single-line passphrase without control characters through the selected passphrase source and rerun the command.",
                  null));
        }
        offset += Character.charCount(codePoint);
      }
      return ContractDecision.accepted(sourceDescription);
    } finally {
      SqliteBookPassphraseZeroization.zeroize(decoded);
    }
  }

  static ContractFailure invalidUtf8PassphraseSourceFailure(String sourceDescription) {
    return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
        "The FinGrind book passphrase source must contain a UTF-8 passphrase: " + sourceDescription,
        "Provide one UTF-8 passphrase payload through the selected passphrase source and rerun the command.",
        null);
  }
}
