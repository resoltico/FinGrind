package dev.erst.fingrind.contract;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Canonical machine-readable deterministic error vocabulary for FinGrind CLI failures. */
public final class ContractErrors {
  private ContractErrors() {}

  /** Returns the canonical machine descriptors for every supported CLI error code. */
  public static List<ContractResponse.ErrorDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Stable descriptor for one deterministic CLI error code. */
  @SuppressWarnings("ImmutableEnumChecker")
  public enum Descriptor {
    UNKNOWN_COMMAND(
        "unknown-command",
        "Invocation refused because the selected command name is not one of the public FinGrind operations."),
    INVALID_REQUEST(
        "invalid-request",
        "Invocation or request document refused because it does not match the accepted FinGrind command or request contract."),
    RUNTIME_FAILURE(
        "runtime-failure",
        "Command failed because of a genuine runtime or environment problem rather than deterministic caller input."),
    INVALID_PAGE_CURSOR(
        "invalid-page-cursor",
        "Posting-history pagination refused because the supplied cursor is not a valid FinGrind posting page cursor."),
    BOOK_KEY_FILE_ALREADY_EXISTS(
        "book-key-file-already-exists",
        "Book key file generation refused because the selected destination already exists and FinGrind will not overwrite it."),
    INVALID_BOOK_KEY_FILE(
        "invalid-book-key-file",
        "Book access refused because the selected book key file path, permissions, or contents do not satisfy the protected-book contract."),
    INVALID_BOOK_PASSPHRASE_SOURCE(
        "invalid-book-passphrase-source",
        "Book access refused because the supplied passphrase source is empty, malformed, or otherwise does not satisfy the protected-book contract."),
    INTERACTIVE_PROMPT_UNAVAILABLE(
        "interactive-prompt-unavailable",
        "Interactive passphrase entry refused because no supported controlling terminal is available."),
    INTERACTIVE_PROMPT_FAILED(
        "interactive-prompt-failed",
        "Interactive passphrase entry refused because FinGrind did not receive one valid passphrase from the interactive console."),
    BOOK_AUTHENTICATION_FAILED(
        "book-authentication-failed",
        "Book access refused because FinGrind could not authenticate the selected protected book with the supplied passphrase source.");

    private final String code;
    private final String description;

    Descriptor(String code, String description) {
      this.code = Objects.requireNonNull(code, "code");
      this.description = Objects.requireNonNull(description, "description");
    }

    /** Returns the stable wire code for this deterministic error descriptor. */
    public String code() {
      return code;
    }

    /** Returns the canonical machine-readable description for this error descriptor. */
    public String description() {
      return description;
    }

    private ContractResponse.ErrorDescriptor descriptor() {
      return new ContractResponse.ErrorDescriptor(code, description);
    }

    private static List<ContractResponse.ErrorDescriptor> descriptors() {
      return Arrays.stream(values()).map(Descriptor::descriptor).toList();
    }
  }
}
