package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.nio.file.Path;
import java.util.Objects;

/** Deterministic refusal raised when an artifact cannot be staged in a private output directory. */
final class CliArtifactOutputDirectoryException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String artifactLabel;
  private final String artifactOptionName;
  private final FailureReason failureReason;
  private final transient Path outputPath;
  private final String serializedOutputPath;

  CliArtifactOutputDirectoryException(
      Path outputPath, String artifactOptionName, String artifactLabel) {
    super(FailureReason.OUTPUT_PATH_MUST_NAME_ARTIFACT.message(artifactLabel));
    this.artifactLabel = Objects.requireNonNull(artifactLabel, "artifactLabel");
    this.artifactOptionName = Objects.requireNonNull(artifactOptionName, "artifactOptionName");
    this.failureReason = FailureReason.OUTPUT_PATH_MUST_NAME_ARTIFACT;
    this.outputPath = CliExceptionPathSnapshot.capture(outputPath);
    this.serializedOutputPath = this.outputPath.toString();
  }

  CliArtifactOutputDirectoryException(
      Path outputPath, String artifactOptionName, String artifactLabel, Throwable cause) {
    super(FailureReason.from(Objects.requireNonNull(cause, "cause")).message(artifactLabel), cause);
    this.artifactLabel = Objects.requireNonNull(artifactLabel, "artifactLabel");
    this.artifactOptionName = Objects.requireNonNull(artifactOptionName, "artifactOptionName");
    this.failureReason = FailureReason.from(cause);
    this.outputPath = CliExceptionPathSnapshot.capture(outputPath);
    this.serializedOutputPath = this.outputPath.toString();
  }

  String artifactLabel() {
    return artifactLabel;
  }

  String artifactOptionName() {
    return artifactOptionName;
  }

  Path outputPath() {
    return outputPath == null ? CliExceptionPathSnapshot.restore(serializedOutputPath) : outputPath;
  }

  String publicMessage() {
    return failureReason.message(artifactLabel);
  }

  String publicHint() {
    return failureReason.hint(artifactOptionName);
  }

  /** Privacy-safe category for one rejected artifact-output location. */
  private enum FailureReason {
    OUTPUT_PATH_MUST_NAME_ARTIFACT {
      @Override
      String message(String artifactLabel) {
        return "The "
            + artifactLabel
            + " output path must name a file beneath an existing private directory.";
      }

      @Override
      String hint(String artifactOptionName) {
        return "Choose a file path for "
            + artifactOptionName
            + " beneath an existing private output directory, then rerun the command.";
      }
    },
    PATH_COLLISION {
      @Override
      String message(String artifactLabel) {
        return "The "
            + artifactLabel
            + " output parent cannot be a symbolic link or non-directory path.";
      }

      @Override
      String hint(String artifactOptionName) {
        return "Choose an existing real directory for "
            + artifactOptionName
            + " that contains no symbolic-link or non-directory path component, then rerun the command.";
      }
    },
    OWNER_ONLY_REQUIRED {
      @Override
      String message(String artifactLabel) {
        return "The "
            + artifactLabel
            + " output parent must be an existing owner-only directory with non-mutable ancestry.";
      }

      @Override
      String hint(String artifactOptionName) {
        return "Create or select an existing owner-only directory for "
            + artifactOptionName
            + "; on POSIX, restrict it to the owner (for example chmod 700), then rerun the command.";
      }
    };

    abstract String message(String artifactLabel);

    abstract String hint(String artifactOptionName);

    private static FailureReason from(Throwable cause) {
      return cause instanceof PrivateOutputDirectory.Violation violation
          ? switch (violation.kind()) {
            case PATH_COLLISION -> PATH_COLLISION;
            case OWNER_ONLY_REQUIRED -> OWNER_ONLY_REQUIRED;
          }
          : OWNER_ONLY_REQUIRED;
    }
  }
}
