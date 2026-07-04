package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;

/** Canonical caller-authored entry fingerprint fields for posting request fingerprints. */
final class RequestFingerprintCallerAuthoredEntryWriter {
  private RequestFingerprintCallerAuthoredEntryWriter() {}

  static void append(StringBuilder canonical, BookkeepingEntry entry) {
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.entryKind", entry.entryKind().wireValue());
    RequestFingerprintTypedEntryWriter.append(canonical, entry);
    RequestFingerprintEntryFieldWriter.appendOptionalForeignExchangeDetails(
        canonical, entry.foreignExchangeDetails());
  }
}
