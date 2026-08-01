package dev.erst.fingrind.sqlite;

/** Names one protected-book staging boundary whose failure must preserve final artifacts. */
enum SqliteProtectedBookStagingCheckpoint {
  BACKUP_EXPORT("Failed to prepare the encrypted FinGrind backup stage."),
  BACKUP_SOURCE_OPEN("Failed to open the encrypted FinGrind backup source."),
  BACKUP_STAGE_OPEN("Failed to open the encrypted FinGrind backup stage."),
  BACKUP_COPY("Failed to copy the encrypted FinGrind backup stage."),
  BACKUP_SECRET_GENERATION("Failed to generate the FinGrind backup stage key."),
  BACKUP_REKEY("Failed to re-encrypt the FinGrind backup stage."),
  RESTORE_COPY("Failed to copy the encrypted FinGrind restored-book stage."),
  RESTORE_SECRET_GENERATION("Failed to generate the FinGrind restored-book stage key."),
  RESTORE_REKEY("Failed to re-encrypt the FinGrind restored-book stage.");

  private final String failureMessage;

  SqliteProtectedBookStagingCheckpoint(String failureMessage) {
    this.failureMessage = failureMessage;
  }

  String failureMessage() {
    return failureMessage;
  }
}
