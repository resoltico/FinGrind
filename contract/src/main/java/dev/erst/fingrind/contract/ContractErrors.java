package dev.erst.fingrind.contract;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Canonical machine-readable deterministic error vocabulary for FinGrind CLI failures. */
public final class ContractErrors {
  private ContractErrors() {}

  /** Returns the canonical machine descriptors for every supported CLI error code. */
  public static List<ContractResponse.ErrorDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Stable descriptor for one deterministic CLI error code. */
  public enum Descriptor {
    UNKNOWN_COMMAND,
    INVALID_REQUEST,
    RUNTIME_FAILURE,
    MANAGED_RUNTIME_FAILURE,
    STORAGE_RUNTIME_FAILURE,
    PDF_EXPORT_FAILURE,
    INVALID_PAGE_CURSOR,
    BOOK_KEY_FILE_ALREADY_EXISTS,
    INVALID_BOOK_KEY_FILE,
    INVALID_BOOK_PASSPHRASE_SOURCE,
    INTERACTIVE_PROMPT_UNAVAILABLE,
    INTERACTIVE_PROMPT_FAILED,
    BOOK_AUTHENTICATION_FAILED;

    /** Returns the stable wire code for this deterministic error descriptor. */
    public String code() {
      return switch (this) {
        case UNKNOWN_COMMAND -> "unknown-command";
        case INVALID_REQUEST -> "invalid-request";
        case RUNTIME_FAILURE -> "runtime-failure";
        case MANAGED_RUNTIME_FAILURE -> "managed-runtime-failure";
        case STORAGE_RUNTIME_FAILURE -> "storage-runtime-failure";
        case PDF_EXPORT_FAILURE -> "pdf-export-failure";
        case INVALID_PAGE_CURSOR -> "invalid-page-cursor";
        case BOOK_KEY_FILE_ALREADY_EXISTS -> "book-key-file-already-exists";
        case INVALID_BOOK_KEY_FILE -> "invalid-book-key-file";
        case INVALID_BOOK_PASSPHRASE_SOURCE -> "invalid-book-passphrase-source";
        case INTERACTIVE_PROMPT_UNAVAILABLE -> "interactive-prompt-unavailable";
        case INTERACTIVE_PROMPT_FAILED -> "interactive-prompt-failed";
        case BOOK_AUTHENTICATION_FAILED -> "book-authentication-failed";
      };
    }

    /** Returns the canonical machine-readable description for this error descriptor. */
    public String description() {
      return switch (this) {
        case UNKNOWN_COMMAND ->
            "Invocation refused because the selected command name is not one of the public FinGrind operations.";
        case INVALID_REQUEST ->
            "Invocation or request document refused because it does not match the accepted FinGrind command or request contract.";
        case RUNTIME_FAILURE ->
            "Command failed because of a genuine runtime or environment problem rather than deterministic caller input.";
        case MANAGED_RUNTIME_FAILURE ->
            "Command failed because the managed FinGrind runtime dependency surface is unavailable, incompatible, or misconfigured.";
        case STORAGE_RUNTIME_FAILURE ->
            "Command failed because SQLite storage or book-handle execution encountered a runtime problem outside the deterministic caller contract.";
        case PDF_EXPORT_FAILURE ->
            "Command completed its core work but failed while exporting the requested PDF artifact.";
        case INVALID_PAGE_CURSOR ->
            "Paginated query refused because the supplied cursor is not a valid FinGrind page cursor.";
        case BOOK_KEY_FILE_ALREADY_EXISTS ->
            "Book key file generation refused because the selected destination already exists and FinGrind will not overwrite it.";
        case INVALID_BOOK_KEY_FILE ->
            "Book access refused because the selected book key file path, permissions, or contents do not satisfy the protected-book contract.";
        case INVALID_BOOK_PASSPHRASE_SOURCE ->
            "Book access refused because the supplied passphrase source is empty, malformed, or otherwise does not satisfy the protected-book contract.";
        case INTERACTIVE_PROMPT_UNAVAILABLE ->
            "Interactive passphrase entry refused because no supported controlling terminal is available.";
        case INTERACTIVE_PROMPT_FAILED ->
            "Interactive passphrase entry refused because FinGrind did not receive one valid passphrase from the interactive console.";
        case BOOK_AUTHENTICATION_FAILED ->
            "Book access refused because FinGrind could not authenticate the selected protected book with the supplied passphrase source.";
      };
    }

    /** Creates one deterministic failure with this canonical contract descriptor. */
    public ContractFailure failure(
        String message, @Nullable String hint, @Nullable String argument) {
      return new ContractFailure(this, message, hint, argument);
    }

    private ContractResponse.ErrorDescriptor descriptor() {
      return new ContractResponse.ErrorDescriptor(code(), description());
    }

    private static List<ContractResponse.ErrorDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }
  }
}
