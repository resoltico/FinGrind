package dev.erst.fingrind.contract.runtime;

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
    UNKNOWN_COMMAND(
        "unknown-command",
        "Invocation refused because the selected command name is not one of the public FinGrind operations.",
        1),
    INVALID_REQUEST(
        "invalid-request",
        "Invocation or request document refused because it does not match the accepted FinGrind command or request contract.",
        1),
    INTERNAL_ERROR(
        "internal-error",
        "Command failed because FinGrind encountered an internal software defect rather than a deterministic caller or environment problem.",
        70),
    MANAGED_RUNTIME_FAILURE(
        "managed-runtime-failure",
        "Command failed because the managed FinGrind runtime dependency surface is unavailable, incompatible, or misconfigured.",
        5),
    STORAGE_RUNTIME_FAILURE(
        "storage-runtime-failure",
        "Command failed because SQLite storage or book-handle execution encountered a runtime problem outside the deterministic caller contract.",
        4),
    PDF_EXPORT_FAILURE(
        "pdf-export-failure",
        "Command completed its core work but failed while exporting the requested PDF artifact.",
        4),
    INVALID_PAGE_CURSOR(
        "invalid-page-cursor",
        "Paginated query refused because the supplied cursor is not a valid FinGrind page cursor.",
        1),
    BOOK_KEY_FILE_ALREADY_EXISTS(
        "book-key-file-already-exists",
        "Book key file generation refused because the selected destination already exists and FinGrind will not overwrite it.",
        7),
    INVALID_BOOK_KEY_FILE(
        "invalid-book-key-file",
        "Book access refused because the selected book key file path, permissions, or contents do not satisfy the protected-book contract.",
        6),
    INVALID_BOOK_PASSPHRASE_SOURCE(
        "invalid-book-passphrase-source",
        "Book access refused because the supplied passphrase source is empty, malformed, or otherwise does not satisfy the protected-book contract.",
        6),
    BOOK_MAINTENANCE_IN_PROGRESS(
        "book-maintenance-in-progress",
        "Book access refused because an exclusive FinGrind maintenance workflow currently holds the selected protected book.",
        7),
    INTERACTIVE_PROMPT_UNAVAILABLE(
        "interactive-prompt-unavailable",
        "Interactive passphrase entry refused because no supported controlling terminal is available.",
        5),
    INTERACTIVE_PROMPT_FAILED(
        "interactive-prompt-failed",
        "Interactive passphrase entry refused because FinGrind did not receive one valid passphrase from the interactive console.",
        5),
    PROTECTED_BOOK_VERIFICATION_FAILED(
        "protected-book-verification-failed",
        "Book access refused because FinGrind could not verify the selected protected book with the supplied passphrase source.",
        6);

    private final String code;
    private final String description;
    private final int exitCode;

    Descriptor(String code, String description, int exitCode) {
      this.code = code;
      this.description = description;
      this.exitCode = exitCode;
    }

    /** Returns the stable wire code for this deterministic error descriptor. */
    public String code() {
      return code;
    }

    /** Returns the canonical machine-readable description for this error descriptor. */
    public String description() {
      return description;
    }

    /** Returns the canonical process exit code for this deterministic error descriptor. */
    public int exitCode() {
      return exitCode;
    }

    /** Creates one deterministic failure with this canonical contract descriptor. */
    public ContractFailure failure(
        String message, @Nullable String hint, @Nullable String argument) {
      return new ContractFailure(this, message, hint, argument);
    }

    private ContractResponse.ErrorDescriptor descriptor() {
      List<ContractResponse.FieldDescriptor> detailFields = detailFields();
      return detailFields.isEmpty()
          ? new ContractResponse.ErrorDescriptor(code(), exitCode(), description())
          : new ContractResponse.ErrorDescriptor(code(), exitCode(), description(), detailFields);
    }

    private static List<ContractResponse.ErrorDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      if (this == INVALID_REQUEST) {
        return invalidRequestDetailFields();
      }
      return List.of();
    }

    private static List<ContractResponse.FieldDescriptor> invalidRequestDetailFields() {
      return List.of(
          new ContractResponse.FieldDescriptor(
              "parseMessage",
              "Parser-provided explanation for syntactically invalid JSON request input."),
          new ContractResponse.FieldDescriptor(
              "line", "1-based JSON source line for syntactically invalid request input."),
          new ContractResponse.FieldDescriptor(
              "column", "1-based JSON source column for syntactically invalid request input."),
          new ContractResponse.FieldDescriptor(
              "violations",
              "Ordered list of deterministic request-validation violations when one malformed request produces more than one diagnosis."));
    }
  }
}
