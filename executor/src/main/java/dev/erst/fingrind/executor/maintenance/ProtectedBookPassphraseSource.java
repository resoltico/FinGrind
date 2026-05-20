package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Objects;

/** Local maintenance passphrase-source model decoupled from published contract DTOs. */
public sealed interface ProtectedBookPassphraseSource
    permits ProtectedBookPassphraseSource.KeyFile,
        ProtectedBookPassphraseSource.StandardInput,
        ProtectedBookPassphraseSource.InteractivePrompt {
  /** Projects the local maintenance source back into the published contract shape. */
  BookAccess.PassphraseSource toPublished();

  /** Projects one published contract passphrase source into the local maintenance shape. */
  static ProtectedBookPassphraseSource fromPublished(BookAccess.PassphraseSource passphraseSource) {
    Objects.requireNonNull(passphraseSource, "passphraseSource");
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> new KeyFile(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput _ -> StandardInput.INSTANCE;
      case BookAccess.PassphraseSource.InteractivePrompt _ -> InteractivePrompt.INSTANCE;
    };
  }

  /** Local key-file passphrase source. */
  record KeyFile(Path bookKeyFilePath) implements ProtectedBookPassphraseSource {
    public KeyFile {
      Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    }

    @Override
    public BookAccess.PassphraseSource toPublished() {
      return new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath);
    }
  }

  /** Local standard-input passphrase source. */
  enum StandardInput implements ProtectedBookPassphraseSource {
    INSTANCE;

    @Override
    public BookAccess.PassphraseSource toPublished() {
      return BookAccess.PassphraseSource.StandardInput.INSTANCE;
    }
  }

  /** Local interactive-prompt passphrase source. */
  enum InteractivePrompt implements ProtectedBookPassphraseSource {
    INSTANCE;

    @Override
    public BookAccess.PassphraseSource toPublished() {
      return BookAccess.PassphraseSource.InteractivePrompt.INSTANCE;
    }
  }
}
