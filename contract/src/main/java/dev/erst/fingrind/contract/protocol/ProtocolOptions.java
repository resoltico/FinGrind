package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.ComparativeMode;
import dev.erst.fingrind.core.WireValue;
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

  /** Option selecting one new book key file during rekey. */
  public static final String NEW_BOOK_KEY_FILE = "--new-book-key-file";

  /** Option selecting one backup-book file source or destination, depending on the workflow. */
  public static final String BACKUP_FILE = "--backup-file";

  /** Option selecting one backup-key-file source or destination, depending on the workflow. */
  public static final String BACKUP_KEY_FILE = "--backup-key-file";

  /** Option selecting one explicit stale rollback-book artifact for rekey recovery. */
  public static final String ROLLBACK_BOOK_FILE = "--rollback-book-file";

  /** Option requesting the new passphrase from standard input during rekey. */
  public static final String NEW_BOOK_PASSPHRASE_STDIN = "--new-book-passphrase-stdin";

  /** Option requesting the new passphrase from the terminal during rekey. */
  public static final String NEW_BOOK_PASSPHRASE_PROMPT = "--new-book-passphrase-prompt";

  /** Option selecting a JSON request document. */
  public static final String REQUEST_FILE = "--request-file";

  /** Option selecting a durable posting identifier. */
  public static final String POSTING_ID = "--posting-id";

  /** Option selecting one declared tax registration identifier. */
  public static final String TAX_REGISTRATION_ID = "--tax-registration-id";

  /** Option selecting a book-local account code. */
  public static final String ACCOUNT_CODE = "--account-code";

  /** Option selecting the inclusive lower effective-date bound. */
  public static final String EFFECTIVE_DATE_FROM = "--effective-date-from";

  /** Option selecting the inclusive upper effective-date bound. */
  public static final String EFFECTIVE_DATE_TO = "--effective-date-to";

  /** Option selecting the inclusive period-start bound for period-shaped commands. */
  public static final String PERIOD_START = "--period-start";

  /** Option selecting the inclusive period-end bound for period-shaped commands. */
  public static final String PERIOD_END = "--period-end";

  /** Option selecting the fiscal year label used by fiscal-year-close. */
  public static final String YEAR = "--year";

  /** Option selecting the inclusive transferred-through date used by interim-result-sweep. */
  public static final String THROUGH = "--through";

  /** Option selecting one as-of effective date for as-of report commands. */
  public static final String EFFECTIVE_DATE_AS_OF = "--effective-date-as-of";

  /** Option selecting one comparative mode or explicit comparative range for supported reports. */
  public static final String COMPARATIVE = "--comparative";

  /** Option selecting the accounting-entity name used when initializing one new book. */
  public static final String ENTITY_NAME = "--entity-name";

  /** Option selecting the seed-template doctrine used when initializing one new book. */
  public static final String BOOK_TEMPLATE_ID = "--book-template-id";

  /** Option selecting the accounting basis used when initializing one new book. */
  public static final String ACCOUNTING_BASIS = "--accounting-basis";

  /** Option restoring the interactive context footer on supported text read commands. */
  public static final String WITH_CONTEXT = "--with-context";

  /** Option explicitly tightening one existing named parent directory to owner-only access. */
  public static final String TIGHTEN_PARENTS = "--tighten-parents";

  /** Option selecting the functional currency code used when initializing one new book. */
  public static final String FUNCTIONAL_CURRENCY = "--functional-currency";

  /** Option selecting the {@code MM-DD} fiscal-year start used when initializing one new book. */
  public static final String FISCAL_YEAR_START = "--fiscal-year-start";

  /** Option selecting which posting kinds one report query should include. */
  public static final String POSTING_COVERAGE = "--posting-coverage";

  /** Option selecting a paginated query page size. */
  public static final String LIMIT = "--limit";

  /** Option selecting the opaque next-page cursor for posting-history pagination. */
  public static final String CURSOR = "--cursor";

  /** Option selecting the presentation format for commands that advertise output modes. */
  public static final String OUTPUT = "--output";

  /** Option selecting one discovery-payload detail level. */
  public static final String DETAIL = "--detail";

  /** Option selecting one discovery concern for JSON discovery responses. */
  public static final String FOCUS = "--focus";

  /** Option selecting one command family for JSON discovery responses. */
  public static final String CATEGORY = "--category";

  /** Option selecting whether execute-plan returns one summary or the full execution journal. */
  public static final String RESULT_DETAIL = "--result-detail";

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

  /** Returns the rendered new-passphrase source syntax. */
  public static String newPassphraseSourceSyntax() {
    return NEW_BOOK_KEY_FILE
        + " <existing-path> | "
        + NEW_BOOK_PASSPHRASE_STDIN
        + " | "
        + NEW_BOOK_PASSPHRASE_PROMPT;
  }

  /** Returns the rendered optional page-limit syntax. */
  public static String optionalLimitSyntax() {
    return "[%s <%d-%d>]"
        .formatted(
            ProtocolOptions.LIMIT,
            ProtocolInteractionLimits.PAGE_LIMIT_MIN,
            ProtocolInteractionLimits.PAGE_LIMIT_MAX);
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

  /** Returns the rendered optional posting-coverage syntax for close-sensitive read models. */
  public static String optionalPostingCoverageSyntax() {
    return "["
        + POSTING_COVERAGE
        + " <"
        + String.join(
            "|",
            dev.erst.fingrind.core.WireValue.wireValues(
                dev.erst.fingrind.core.PostingCoverage.class))
        + ">]";
  }

  /** Returns the rendered optional comparative syntax for as-of report commands. */
  public static String optionalAsOfComparativeSyntax() {
    return "[" + COMPARATIVE + " <none|prior-period|..YYYY-MM-DD>]";
  }

  /** Returns the rendered optional comparative syntax for bounded-period report commands. */
  public static String optionalPeriodComparativeSyntax() {
    return "[" + COMPARATIVE + " <none|prior-period|YYYY-MM-DD..YYYY-MM-DD>]";
  }

  /** Returns the published comparative capability mode inventory in stable wire order. */
  public static List<String> comparativeModes() {
    return WireValue.wireValues(ComparativeMode.class);
  }

  /** Returns the rendered optional execute-plan result-detail syntax. */
  public static String optionalResultDetailSyntax() {
    return "["
        + RESULT_DETAIL
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(PlanResultDetail.class))
        + ">]";
  }

  /** Returns the rendered optional discovery-detail syntax. */
  public static String optionalDiscoveryDetailSyntax() {
    return "["
        + DETAIL
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryDetail.class))
        + ">]";
  }

  /** Returns the rendered optional discovery-detail syntax for JSON-only discovery surfaces. */
  public static String optionalJsonOnlyDiscoveryDetailSyntax() {
    return "["
        + DETAIL
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryDetail.class))
        + ">"
        + " (json only)]";
  }

  /** Returns the rendered optional discovery-focus syntax for JSON-only discovery surfaces. */
  public static String optionalJsonOnlyDiscoveryFocusSyntax() {
    return "["
        + FOCUS
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryFocus.class))
        + ">"
        + " (json only)]";
  }

  /** Returns the rendered optional operation-category syntax for JSON-only discovery surfaces. */
  public static String optionalJsonOnlyOperationCategorySyntax() {
    return "["
        + CATEGORY
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(OperationCategory.class))
        + ">"
        + " (json only)]";
  }
}
