package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical CLI option spellings for selecting and maintaining protected book artifacts. */
public final class ProtocolBookAccessOptions {
  /** Option selecting the protected book file. */
  public static final String BOOK_FILE = "--book-file";

  /** Option selecting the current book key file. */
  public static final String BOOK_KEY_FILE = "--book-key-file";

  /** Option requesting the current book passphrase from standard input. */
  public static final String BOOK_PASSPHRASE_STDIN = "--book-passphrase-stdin";

  /** Option requesting the current book passphrase from the controlling terminal. */
  public static final String BOOK_PASSPHRASE_PROMPT = "--book-passphrase-prompt";

  /** Option selecting an absent target where FinGrind publishes one generated book key file. */
  public static final String NEW_BOOK_KEY_FILE = "--new-book-key-file";

  /** Option selecting one backup-book file source or destination, depending on the workflow. */
  public static final String BACKUP_FILE = "--backup-file";

  /** Caller-selected immutable backup identity used for acknowledgement idempotency and resume. */
  public static final String BACKUP_ID = "--backup-id";

  /** Option selecting one existing backup-key-file source. */
  public static final String BACKUP_KEY_FILE = "--backup-key-file";

  /** Option selecting an absent target where FinGrind publishes one generated backup key file. */
  public static final String NEW_BACKUP_KEY_FILE = "--new-backup-key-file";

  private ProtocolBookAccessOptions() {}

  /** Returns the accepted current-passphrase source options in public contract order. */
  public static List<String> passphraseSourceOptions() {
    return List.of(BOOK_KEY_FILE, BOOK_PASSPHRASE_STDIN, BOOK_PASSPHRASE_PROMPT);
  }

  /** Returns the rendered current-passphrase source syntax. */
  public static String passphraseSourceSyntax() {
    return BOOK_KEY_FILE + " <path> | " + BOOK_PASSPHRASE_STDIN + " | " + BOOK_PASSPHRASE_PROMPT;
  }
}
