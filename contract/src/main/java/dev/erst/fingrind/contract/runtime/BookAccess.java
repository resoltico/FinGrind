package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationLimits;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One durable book file, its passphrase source, and optional mutation-authorization sources. */
public record BookAccess(
    Path bookFilePath,
    PassphraseSource passphraseSource,
    List<AttestationCredentialSource> attestationCredentialSources) {
  public BookAccess {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(passphraseSource, "passphraseSource");
    attestationCredentialSources =
        List.copyOf(
            Objects.requireNonNull(attestationCredentialSources, "attestationCredentialSources"));
    if (attestationCredentialSources.size() > AttestationAuthorizationLimits.MAXIMUM_QUORUM) {
      throw new IllegalArgumentException(
          "Book access may name at most "
              + AttestationAuthorizationLimits.MAXIMUM_QUORUM
              + " attestation authorization credentials.");
    }
    requireDistinctCredentialSources(attestationCredentialSources);
  }

  /** Requires the explicit one-through-64 credentials needed by a mutating protected-book call. */
  public List<AttestationCredentialSource> requireAttestationCredentialSources() {
    if (attestationCredentialSources.isEmpty()) {
      throw new IllegalStateException(
          "Protected-book mutation requires at least one attestation authorization credential.");
    }
    return attestationCredentialSources;
  }

  private static void requireDistinctCredentialSources(
      List<AttestationCredentialSource> credentialSources) {
    Set<java.util.UUID> principalIds = new HashSet<>();
    Set<Path> keyPaths = new HashSet<>();
    for (AttestationCredentialSource credentialSource : credentialSources) {
      AttestationCredentialSource checkedSource =
          Objects.requireNonNull(
              credentialSource, "attestationCredentialSources must not contain null");
      if (!principalIds.add(checkedSource.principalId())) {
        throw new IllegalArgumentException(
            "Attestation authorization credential principals must be distinct.");
      }
      if (!keyPaths.add(checkedSource.encryptedKeyFilePath())) {
        throw new IllegalArgumentException(
            "Attestation authorization credential key files must be distinct.");
      }
    }
  }

  /** Supported CLI-visible passphrase transport selections for one protected book command. */
  public sealed interface PassphraseSource
      permits PassphraseSource.KeyFile,
          PassphraseSource.StandardInput,
          PassphraseSource.InteractivePrompt {
    /** Returns the canonical CLI option name for this passphrase source. */
    String optionName();

    /** Passphrase source that reads one UTF-8 passphrase file from the filesystem. */
    record KeyFile(Path bookKeyFilePath) implements PassphraseSource {
      public KeyFile {
        Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
      }

      @Override
      public String optionName() {
        return ProtocolBookAccessOptions.BOOK_KEY_FILE;
      }
    }

    /** Passphrase source that reads one UTF-8 passphrase payload from standard input. */
    record StandardInput() implements PassphraseSource {
      public static final StandardInput INSTANCE = new StandardInput();

      @Override
      public String optionName() {
        return ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN;
      }
    }

    /** Passphrase source that reads one passphrase from the controlling terminal without echo. */
    record InteractivePrompt() implements PassphraseSource {
      public static final InteractivePrompt INSTANCE = new InteractivePrompt();

      @Override
      public String optionName() {
        return ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT;
      }
    }
  }
}
