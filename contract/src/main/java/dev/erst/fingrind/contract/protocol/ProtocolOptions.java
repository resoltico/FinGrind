package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.InteractionLimits;
import java.util.List;

/** Canonical public CLI option spellings used by the protocol catalog and parser. */
public final class ProtocolOptions {
  /** Option selecting the protected book file. */
  public static final String BOOK_FILE = "--book-file";

  /** Option selecting the current book key file. */
  public static final String BOOK_KEY_FILE = "--book-key-file";

  /** Option requesting the current book passphrase from standard input. */
  public static final String BOOK_PASSPHRASE_STDIN = "--book-passphrase-stdin";

  /** Option requesting the current book passphrase from the controlling terminal. */
  public static final String BOOK_PASSPHRASE_PROMPT = "--book-passphrase-prompt";

  /** Option selecting one existing replacement book key file during rekey. */
  public static final String REPLACEMENT_BOOK_KEY_FILE = "--replacement-book-key-file";

  /** Option requesting the replacement passphrase from standard input during rekey. */
  public static final String REPLACEMENT_BOOK_PASSPHRASE_STDIN =
      "--replacement-book-passphrase-stdin";

  /** Option requesting the replacement passphrase from the terminal during rekey. */
  public static final String REPLACEMENT_BOOK_PASSPHRASE_PROMPT =
      "--replacement-book-passphrase-prompt";

  /** Option selecting a JSON request document. */
  public static final String REQUEST_FILE = "--request-file";

  /** Option selecting a durable posting identifier. */
  public static final String POSTING_ID = "--posting-id";

  /** Option selecting a book-local account code. */
  public static final String ACCOUNT_CODE = "--account-code";

  /** Option selecting the inclusive lower effective-date bound. */
  public static final String EFFECTIVE_DATE_FROM = "--effective-date-from";

  /** Option selecting the inclusive upper effective-date bound. */
  public static final String EFFECTIVE_DATE_TO = "--effective-date-to";

  /** Option selecting a paginated query page size. */
  public static final String LIMIT = "--limit";

  /** Option selecting the opaque next-page cursor for posting-history pagination. */
  public static final String CURSOR = "--cursor";

  /** Option selecting the presentation format for commands that advertise output modes. */
  public static final String OUTPUT = "--output";

  /** Option selecting one PDF export destination for supported report commands. */
  public static final String PDF_OUT = "--pdf-out";

  /** Token that routes request JSON through standard input. */
  public static final String STDIN_TOKEN = "-";

  private ProtocolOptions() {}

  /** Returns the accepted current-passphrase source options in public contract order. */
  public static List<String> bookPassphraseOptions() {
    return List.of(BOOK_KEY_FILE, BOOK_PASSPHRASE_STDIN, BOOK_PASSPHRASE_PROMPT);
  }

  /** Returns the rendered current-passphrase source syntax. */
  public static String currentPassphraseSourceSyntax() {
    return BOOK_KEY_FILE + " <path> | " + BOOK_PASSPHRASE_STDIN + " | " + BOOK_PASSPHRASE_PROMPT;
  }

  /** Returns the rendered replacement-passphrase source syntax. */
  public static String replacementPassphraseSourceSyntax() {
    return REPLACEMENT_BOOK_KEY_FILE
        + " <existing-path> | "
        + REPLACEMENT_BOOK_PASSPHRASE_STDIN
        + " | "
        + REPLACEMENT_BOOK_PASSPHRASE_PROMPT;
  }

  /** Returns the rendered optional page-limit syntax. */
  public static String optionalLimitSyntax() {
    return "[%s <%d-%d>]"
        .formatted(
            ProtocolOptions.LIMIT,
            InteractionLimits.PAGE_LIMIT_MIN,
            InteractionLimits.PAGE_LIMIT_MAX);
  }

  /** Returns the rendered optional page-cursor syntax. */
  public static String optionalCursorSyntax() {
    return "[" + ProtocolOptions.CURSOR + " <cursor>]";
  }

  /** Returns the rendered optional output-mode syntax for the supplied modes. */
  public static String optionalOutputSyntax(List<OutputMode> outputModes) {
    return "["
        + OUTPUT
        + " <"
        + outputModes.stream()
            .map(OutputMode::wireValue)
            .collect(java.util.stream.Collectors.joining("|"))
        + ">]";
  }

  /** Returns the rendered optional PDF-export syntax for supported report commands. */
  public static String optionalPdfOutSyntax() {
    return "[" + PDF_OUT + " <path>]";
  }
}
