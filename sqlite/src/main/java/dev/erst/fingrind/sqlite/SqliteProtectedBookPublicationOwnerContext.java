package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PublicationTransactionOwnerContext;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** Derives the opaque journal lookup identity for one exact protected-book publication request. */
final class SqliteProtectedBookPublicationOwnerContext {
  private static final String FORMAT = "fingrind-protected-book-publication-context-v1";

  private SqliteProtectedBookPublicationOwnerContext() {}

  /**
   * Binds the caller's immutable operation identity, both canonical finals, and target policy.
   *
   * <p>The resulting digest is private journal metadata. It contains no passphrase, key bytes, or
   * un-hashed filesystem spelling, and it intentionally names no stage path.
   */
  static PublicationTransactionOwnerContext forPair(
      ProtectedBookPairPublicationRecoveryRequest request,
      Path normalizedBookTargetPath,
      Path normalizedSecretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy) {
    ProtectedBookPairPublicationRecoveryRequest checkedRequest =
        Objects.requireNonNull(request, "request");
    StringBuilder description = new StringBuilder(FORMAT);
    append(
        description,
        "book-target",
        normalized(normalizedBookTargetPath, "normalizedBookTargetPath"));
    append(
        description,
        "secret-target",
        normalized(normalizedSecretTargetPath, "normalizedSecretTargetPath"));
    append(
        description,
        "book-target-policy",
        Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy").name());
    switch (checkedRequest) {
      case ProtectedBookPairPublicationRecoveryRequest.Backup backup -> {
        append(description, "operation", "backup");
        append(description, "source-book", backup.sourceBookPath().toString());
        append(description, "backup-id", backup.backupId().toString());
      }
      case ProtectedBookPairPublicationRecoveryRequest.Restore restore -> {
        append(description, "operation", "restore");
        append(description, "backup-artifact", restore.backupArtifactPath().toString());
        append(description, "backup-key", restore.backupKeyPath().toString());
        appendAcknowledgement(description, restore);
      }
      case ProtectedBookPairPublicationRecoveryRequest.Rekey _ -> {
        append(description, "operation", "rekey");
      }
    }
    return PublicationTransactionOwnerContext.fromCanonicalDescription(description.toString());
  }

  private static void appendAcknowledgement(
      StringBuilder description, ProtectedBookPairPublicationRecoveryRequest.Restore restore) {
    append(description, "backup-id", restore.acknowledgement().backupId().toString());
    append(description, "source-order", restore.acknowledgement().sourceOrder().toString());
    byte[] backupDigest = restore.acknowledgement().backupArtifactDigest();
    byte[] sourceHead = restore.acknowledgement().sourceOperationHead();
    try {
      append(description, "backup-digest", HexFormat.of().formatHex(backupDigest));
      append(description, "source-head", HexFormat.of().formatHex(sourceHead));
    } finally {
      Arrays.fill(backupDigest, (byte) 0);
      Arrays.fill(sourceHead, (byte) 0);
    }
  }

  private static String normalized(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize().toString();
  }

  private static void append(StringBuilder description, String label, String value) {
    String checkedLabel = Objects.requireNonNull(label, "label");
    String checkedValue = Objects.requireNonNull(value, "value");
    description
        .append('\n')
        .append(checkedLabel.length())
        .append(':')
        .append(checkedLabel)
        .append('=')
        .append(checkedValue.length())
        .append(':')
        .append(checkedValue);
  }
}
