package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookPassphraseSource;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Non-secret caller identity that binds a recoverable rekey publication to its selected source.
 *
 * <p>The identity deliberately records only the source path and passphrase transport. A key-file
 * transport also records its canonical path; passphrase material is never persisted.
 */
public record ProtectedBookPairPublicationSourceIdentity(
    Path bookPath, Kind passphraseSourceKind, @Nullable Path keyFilePath) {
  /** Stable non-secret passphrase transport kinds admitted for a rekey source. */
  public enum Kind {
    KEY_FILE,
    STANDARD_INPUT,
    INTERACTIVE_PROMPT
  }

  public ProtectedBookPairPublicationSourceIdentity {
    bookPath = normalized(bookPath, "bookPath");
    Objects.requireNonNull(passphraseSourceKind, "passphraseSourceKind");
    if (passphraseSourceKind == Kind.KEY_FILE) {
      keyFilePath = normalized(keyFilePath, "keyFilePath");
    } else if (keyFilePath != null) {
      throw new IllegalArgumentException(
          "Only a key-file source may retain a canonical key-file path.");
    }
  }

  /** Derives the durable non-secret identity from the caller's selected protected-book access. */
  public static ProtectedBookPairPublicationSourceIdentity from(ProtectedBookAccess access) {
    ProtectedBookAccess checked = Objects.requireNonNull(access, "access");
    return switch (checked.passphraseSource()) {
      case ProtectedBookPassphraseSource.KeyFile keyFile ->
          new ProtectedBookPairPublicationSourceIdentity(
              checked.bookFilePath(), Kind.KEY_FILE, keyFile.bookKeyFilePath());
      case ProtectedBookPassphraseSource.StandardInput _ ->
          new ProtectedBookPairPublicationSourceIdentity(
              checked.bookFilePath(), Kind.STANDARD_INPUT, null);
      case ProtectedBookPassphraseSource.InteractivePrompt _ ->
          new ProtectedBookPairPublicationSourceIdentity(
              checked.bookFilePath(), Kind.INTERACTIVE_PROMPT, null);
    };
  }

  private static Path normalized(@Nullable Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
