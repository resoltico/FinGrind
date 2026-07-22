package dev.erst.fingrind.contract.runtime;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Canonical machine-readable deterministic error vocabulary for FinGrind CLI failures. */
public final class ContractErrors {
  private ContractErrors() {}

  /** Returns the canonical machine descriptors for every supported CLI error code. */
  public static List<ContractResponse.ErrorDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Stable descriptor for a deterministic CLI error code. */
  public enum Descriptor {
    UNKNOWN_COMMAND(
        "unknown-command",
        "Invocation refused because the selected command name is not among the public FinGrind operations.",
        1,
        ContractResponse.FailureCategory.STRUCTURAL_INVALID),
    INVALID_REQUEST(
        "invalid-request",
        "Invocation or request document refused because it does not match the accepted FinGrind command or request contract.",
        1,
        ContractResponse.FailureCategory.STRUCTURAL_INVALID),
    INTERNAL_ERROR(
        "internal-error",
        "Command failed because FinGrind encountered an internal software defect rather than a deterministic caller or environment problem.",
        70,
        ContractResponse.FailureCategory.INTERNAL),
    INTERNAL_DEFECT(
        "internal-defect",
        "Command failed because FinGrind detected a deterministic internal contract defect in its typed bookkeeping write semantics.",
        70,
        ContractResponse.FailureCategory.INTERNAL),
    MANAGED_RUNTIME_FAILURE(
        "managed-runtime-failure",
        "Command failed because the managed FinGrind runtime dependency surface is unavailable, incompatible, or misconfigured.",
        5,
        ContractResponse.FailureCategory.PRECONDITION),
    STORAGE_RUNTIME_FAILURE(
        "storage-runtime-failure",
        "Command failed because SQLite storage or book-handle execution encountered a runtime problem outside the deterministic caller contract.",
        4,
        ContractResponse.FailureCategory.PRECONDITION),
    PDF_EXPORT_FAILURE(
        "pdf-export-failure",
        "Command failed while exporting the requested PDF artifact.",
        4,
        ContractResponse.FailureCategory.PRECONDITION),
    INVALID_PAGE_CURSOR(
        "invalid-page-cursor",
        "Paginated query refused because the supplied cursor is not a valid FinGrind page cursor.",
        1,
        ContractResponse.FailureCategory.STRUCTURAL_INVALID),
    SECRET_TARGET_OCCUPIED(
        "secret-target-occupied",
        "Generated-secret publication refused because the selected target already exists and FinGrind will not overwrite it.",
        7,
        ContractResponse.FailureCategory.PRECONDITION),
    BOOK_DESTINATION_OCCUPIED(
        "book-destination-occupied",
        "Book initialization refused because the selected book destination already exists and FinGrind will not access or replace it.",
        7,
        ContractResponse.FailureCategory.PRECONDITION),
    ARTIFACT_OUTPUT_ALREADY_EXISTS(
        "artifact-output-already-exists",
        "Artifact publication refused because the selected output destination already exists and FinGrind will not overwrite it.",
        7,
        ContractResponse.FailureCategory.PRECONDITION),
    INVALID_BOOK_KEY_FILE(
        "invalid-book-key-file",
        "Book access refused because the selected book key file path, permissions, or contents do not satisfy the protected-book contract.",
        6,
        ContractResponse.FailureCategory.PRECONDITION),
    INVALID_BOOK_FILE_PATH(
        "invalid-book-file-path",
        "Book access or initialization refused because the selected protected-book path, parent directory, or permissions do not satisfy the protected-book contract.",
        6,
        ContractResponse.FailureCategory.PRECONDITION),
    INVALID_BOOK_PASSPHRASE_SOURCE(
        "invalid-book-passphrase-source",
        "Book access refused because the supplied passphrase source is empty, malformed, or otherwise does not satisfy the protected-book contract.",
        6,
        ContractResponse.FailureCategory.PRECONDITION),
    INVALID_ATTESTATION_CREDENTIAL(
        "invalid-attestation-credential",
        "Attested-book authorization refused because a selected founder credential or passphrase source is invalid.",
        6,
        ContractResponse.FailureCategory.PRECONDITION),
    STALE_HEAD(
        "stale-head",
        "Attested-book mutation refused because the authenticated operation head advanced after signing and before atomic admission.",
        2,
        ContractResponse.FailureCategory.PRECONDITION),
    BOOK_MAINTENANCE_IN_PROGRESS(
        "book-maintenance-in-progress",
        "Book access refused because an exclusive FinGrind maintenance workflow currently holds the selected protected book.",
        7,
        ContractResponse.FailureCategory.PRECONDITION),
    INTERACTIVE_PROMPT_UNAVAILABLE(
        "interactive-prompt-unavailable",
        "Interactive passphrase entry refused because no supported controlling terminal is available.",
        5,
        ContractResponse.FailureCategory.PRECONDITION),
    INTERACTIVE_PROMPT_FAILED(
        "interactive-prompt-failed",
        "Interactive passphrase entry refused because FinGrind did not receive a valid passphrase from the interactive console.",
        5,
        ContractResponse.FailureCategory.PRECONDITION),
    UNSUPPORTED_OUTPUT_SELECTION(
        "unsupported-output-selection",
        "Invocation refused because the selected output mode does not fit the understood command and runtime policy.",
        2,
        ContractResponse.FailureCategory.UNSUPPORTED_SELECTION),
    PROTECTED_BOOK_VERIFICATION_FAILED(
        "protected-book-verification-failed",
        "Book access refused because FinGrind could not verify the selected protected book with the supplied passphrase source.",
        6,
        ContractResponse.FailureCategory.PRECONDITION);

    private final String code;
    private final String description;
    private final int exitCode;
    private final ContractResponse.FailureCategory category;

    Descriptor(
        String code, String description, int exitCode, ContractResponse.FailureCategory category) {
      this.code = code;
      this.description = description;
      this.exitCode = exitCode;
      this.category = category;
    }

    /** Returns the stable wire code for this deterministic error descriptor. */
    public String code() {
      return code;
    }

    /** Returns the canonical machine-readable description for this error descriptor. */
    public String description() {
      return description;
    }

    /** Returns the transport category declared for this deterministic error. */
    public ContractResponse.FailureCategory category() {
      return category;
    }

    /** Returns the canonical process exit code for this deterministic error descriptor. */
    public int exitCode() {
      return exitCode;
    }

    /** Creates a deterministic failure with this canonical contract descriptor. */
    public ContractFailure failure(
        String message, @Nullable String hint, @Nullable String argument) {
      return new ContractFailure(this, message, hint, argument, null);
    }

    /** Creates a deterministic failure anchored to one real filesystem location. */
    public ContractFailure failureAt(
        Path path, String message, @Nullable String hint, @Nullable String argument) {
      return new ContractFailure(this, message, hint, argument, ContractFailurePaths.primary(path));
    }

    private ContractResponse.ErrorDescriptor descriptor() {
      List<ContractResponse.FieldDescriptor> detailFields = detailFields();
      return detailFields.isEmpty()
          ? new ContractResponse.ErrorDescriptor(code(), category(), exitCode(), description())
          : new ContractResponse.ErrorDescriptor(
              code(), category(), exitCode(), description(), detailFields);
    }

    private static List<ContractResponse.ErrorDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      if (this == INVALID_REQUEST) {
        return invalidRequestDetailFields();
      }
      if (this == STALE_HEAD) {
        return staleHeadDetailFields();
      }
      return List.of();
    }

    private static List<ContractResponse.FieldDescriptor> staleHeadDetailFields() {
      return List.of(
          new ContractResponse.FieldDescriptor(
              "observedHead",
              "Lowercase hexadecimal operation head used for the refused signature."),
          new ContractResponse.FieldDescriptor(
              "currentHead", "Lowercase hexadecimal operation head authenticated at admission."),
          new ContractResponse.FieldDescriptor(
              "currentOrder",
              "Canonical decimal order of the authenticated current operation head."));
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
              "Ordered list of deterministic request-validation violations when a malformed request produces multiple diagnoses."));
    }
  }
}
