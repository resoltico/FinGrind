package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireText;

/** Administration and inspection JSON records emitted by the CLI transport layer. */
interface CliAdministrationJsonModels {

  record OpenBookPayload(String bookFile, String initializedAt) {
    public OpenBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      initializedAt = requireText(initializedAt, "initializedAt");
    }
  }

  record GeneratedBookKeyFilePayload(
      String bookKeyFile, String encoding, int entropyBits, String permissions) {
    public GeneratedBookKeyFilePayload {
      bookKeyFile = requireText(bookKeyFile, "bookKeyFile");
      encoding = requireText(encoding, "encoding");
      requirePositive(entropyBits, "entropyBits");
      permissions = requireText(permissions, "permissions");
    }
  }

  record RekeyBookPayload(String bookFile) {
    public RekeyBookPayload {
      bookFile = requireText(bookFile, "bookFile");
    }
  }

  record MissingBookInspectionPayload(
      String bookFile,
      String state,
      boolean initialized,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int supportedBookFormatVersion,
      String migrationPolicy) {
    public MissingBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
      migrationPolicy = requireText(migrationPolicy, "migrationPolicy");
    }
  }

  record ExistingBookInspectionPayload(
      String bookFile,
      String state,
      boolean initialized,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      String migrationPolicy) {
    public ExistingBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      CliJsonModelValidation.requireNonNegative(
          detectedBookFormatVersion, "detectedBookFormatVersion");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
      migrationPolicy = requireText(migrationPolicy, "migrationPolicy");
    }
  }

  record InitializedBookInspectionPayload(
      String bookFile,
      String state,
      boolean initialized,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      String migrationPolicy,
      String initializedAt) {
    public InitializedBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      CliJsonModelValidation.requireNonNegative(
          detectedBookFormatVersion, "detectedBookFormatVersion");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
      migrationPolicy = requireText(migrationPolicy, "migrationPolicy");
      initializedAt = requireText(initializedAt, "initializedAt");
    }
  }
}
