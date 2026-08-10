package dev.erst.fingrind.contract.runtime;

import java.util.Map;

/** Owns descriptor metadata for protected-book access, lifecycle, and console failures. */
final class ContractProtectedBookErrorDescriptors {
  static final ContractErrorDescriptorDefinition BOOK_DESTINATION_OCCUPIED =
      ContractErrorDescriptorDefinitions.precondition(
          "book-destination-occupied",
          "Book initialization refused because the selected book destination already exists and FinGrind will not access or replace it.",
          7);
  static final ContractErrorDescriptorDefinition INVALID_BOOK_KEY_FILE =
      ContractErrorDescriptorDefinitions.precondition(
          "invalid-book-key-file",
          "Book access refused because the selected book key file path, permissions, or contents do not satisfy the protected-book contract.",
          6);
  static final ContractErrorDescriptorDefinition INVALID_BOOK_FILE_PATH =
      ContractErrorDescriptorDefinitions.precondition(
          "invalid-book-file-path",
          "Book access or initialization refused because the selected protected-book path, parent directory, or permissions do not satisfy the protected-book contract.",
          6);
  static final ContractErrorDescriptorDefinition INVALID_BOOK_PASSPHRASE_SOURCE =
      ContractErrorDescriptorDefinitions.precondition(
          "invalid-book-passphrase-source",
          "Book access refused because the supplied passphrase source is empty, malformed, or otherwise does not satisfy the protected-book contract.",
          6);
  static final ContractErrorDescriptorDefinition OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED =
      ContractErrorDescriptorDefinitions.precondition(
          "open-book-preparation-artifacts-retained",
          "Book opening did not complete and FinGrind retained every created founder-key or book-file artifact as immutable evidence.",
          4);
  static final ContractErrorDescriptorDefinition OPEN_BOOK_PUBLICATION_PROGRESS =
      ContractErrorDescriptorDefinitions.precondition(
          "open-book-publication-progress",
          "Book opening did not complete after FinGrind recorded completed or incomplete founder-key publication transactions.",
          4);
  static final ContractErrorDescriptorDefinition OPEN_BOOK_COMPLETION_UNCERTAIN =
      ContractErrorDescriptorDefinitions.precondition(
          "open-book-completion-uncertain",
          "Book opening returned initialized-book facts, but SQLite could not confirm durable completion after initialization COMMIT or session shutdown.",
          4);
  static final ContractErrorDescriptorDefinition PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN =
      ContractErrorDescriptorDefinitions.precondition(
          "protected-book-pair-publication-uncertain",
          "Protected-book maintenance could not establish a safe durable disposition for an operation-bound book-and-key pair publication or its recovery record.",
          4);
  static final ContractErrorDescriptorDefinition PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED =
      ContractErrorDescriptorDefinitions.precondition(
          "protected-book-pair-publication-evidence-blocked",
          "Protected-book maintenance refused because retained pair evidence cannot establish a safe final-member publication state.",
          4);
  static final ContractErrorDescriptorDefinition BOOK_MAINTENANCE_IN_PROGRESS =
      ContractErrorDescriptorDefinitions.precondition(
          "book-maintenance-in-progress",
          "Book access refused because an exclusive FinGrind maintenance workflow currently holds the selected protected book.",
          7);
  static final ContractErrorDescriptorDefinition INTERACTIVE_PROMPT_UNAVAILABLE =
      ContractErrorDescriptorDefinitions.precondition(
          "interactive-prompt-unavailable",
          "Interactive passphrase entry refused because no supported controlling terminal is available.",
          5);
  static final ContractErrorDescriptorDefinition INTERACTIVE_PROMPT_FAILED =
      ContractErrorDescriptorDefinitions.precondition(
          "interactive-prompt-failed",
          "Interactive passphrase entry refused because FinGrind did not receive a valid passphrase from the interactive console.",
          5);
  static final ContractErrorDescriptorDefinition PROTECTED_BOOK_VERIFICATION_FAILED =
      ContractErrorDescriptorDefinitions.precondition(
          "protected-book-verification-failed",
          "Book access refused because FinGrind could not verify the selected protected book with the supplied passphrase source.",
          6);
  static final ContractErrorDescriptorDefinition UNSUPPORTED_BOOK_FORMAT_VERSION =
      ContractErrorDescriptorDefinitions.precondition(
          "unsupported-book-format-version",
          "Book access refused because the selected authenticated FinGrind book declares a format version this binary does not support.",
          6);

  private ContractProtectedBookErrorDescriptors() {}

  static void addTo(Map<ContractErrors.Descriptor, ContractErrorDescriptorDefinition> definitions) {
    definitions.put(ContractErrors.Descriptor.BOOK_DESTINATION_OCCUPIED, BOOK_DESTINATION_OCCUPIED);
    definitions.put(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, INVALID_BOOK_KEY_FILE);
    definitions.put(ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH, INVALID_BOOK_FILE_PATH);
    definitions.put(
        ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE, INVALID_BOOK_PASSPHRASE_SOURCE);
    definitions.put(
        ContractErrors.Descriptor.OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED,
        OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED);
    definitions.put(
        ContractErrors.Descriptor.OPEN_BOOK_PUBLICATION_PROGRESS, OPEN_BOOK_PUBLICATION_PROGRESS);
    definitions.put(
        ContractErrors.Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN, OPEN_BOOK_COMPLETION_UNCERTAIN);
    definitions.put(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
        PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN);
    definitions.put(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
        PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED);
    definitions.put(
        ContractErrors.Descriptor.BOOK_MAINTENANCE_IN_PROGRESS, BOOK_MAINTENANCE_IN_PROGRESS);
    definitions.put(
        ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE, INTERACTIVE_PROMPT_UNAVAILABLE);
    definitions.put(ContractErrors.Descriptor.INTERACTIVE_PROMPT_FAILED, INTERACTIVE_PROMPT_FAILED);
    definitions.put(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED,
        PROTECTED_BOOK_VERIFICATION_FAILED);
    definitions.put(
        ContractErrors.Descriptor.UNSUPPORTED_BOOK_FORMAT_VERSION, UNSUPPORTED_BOOK_FORMAT_VERSION);
  }
}
