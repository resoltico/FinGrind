package dev.erst.fingrind.core;

/** Owns the only filenames that are eligible for authenticated transaction-journal discovery. */
final class PublicationTransactionJournalFileNames {
  static final String PREFIX = "txn-";
  static final String SUFFIX = ".json";

  private PublicationTransactionJournalFileNames() {}

  static boolean isCanonical(String fileName) {
    return fileName.length() == PREFIX.length() + 32 + SUFFIX.length()
        && fileName.startsWith(PREFIX)
        && fileName.endsWith(SUFFIX)
        && fileName
            .substring(PREFIX.length(), fileName.length() - SUFFIX.length())
            .matches("[0-9a-f]{32}");
  }

  static PublicationTransactionId transactionIdFromCanonical(String fileName) {
    return new PublicationTransactionId(
        fileName.substring(PREFIX.length(), fileName.length() - SUFFIX.length()));
  }
}
