package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Protocol-owned persisted protected-book format facts for the active SQLite3MC contract. */
public record ProtectedBookFormatContract(
    BookCipher cipher,
    boolean legacyMode,
    int pageSize,
    int reservedBytes,
    int legacyPageSize,
    int kdfIter,
    int plaintextHeaderSize) {
  public ProtectedBookFormatContract {
    cipher = ContractDescriptorValidation.requireValue(cipher, "cipher");
    pageSize = requirePowerOfTwoPageSize(pageSize, "pageSize");
    reservedBytes = requireNonNegative(reservedBytes, "reservedBytes");
    legacyPageSize = requirePowerOfTwoPageSize(legacyPageSize, "legacyPageSize");
    kdfIter = requirePositive(kdfIter, "kdfIter");
    plaintextHeaderSize = requireRange(plaintextHeaderSize, 0, 100, "plaintextHeaderSize");
  }

  private static int requirePositive(int value, String fieldName) {
    if (value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive.");
    }
    return value;
  }

  private static int requireNonNegative(int value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must be non-negative.");
    }
    return value;
  }

  private static int requireRange(int value, int minimum, int maximum, String fieldName) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(
          fieldName + " must be between " + minimum + " and " + maximum + ".");
    }
    return value;
  }

  private static int requirePowerOfTwoPageSize(int value, String fieldName) {
    if (value < 512 || value > 65_536 || ((value - 1) & value) != 0) {
      throw new IllegalArgumentException(
          fieldName + " must be one SQLite power-of-two page size between 512 and 65536.");
    }
    return value;
  }
}
