package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.ComparativeMode;
import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Canonical public CLI option spellings used by the protocol catalog and parser. */
public final class ProtocolOptions {
  private ProtocolOptions() {}

  /** Request-document and primary-resource selection options. */
  public static final class Request {
    public static final String FILE = "--request-file";
    public static final String POSTING_ID = "--posting-id";
    public static final String TAX_REGISTRATION_ID = "--tax-registration-id";
    public static final String ACCOUNT_CODE = "--account-code";
    public static final String STDIN_TOKEN = "-";

    private Request() {}
  }

  /** Date and period selection options. */
  public static final class DateRange {
    public static final String EFFECTIVE_DATE_FROM = "--effective-date-from";
    public static final String EFFECTIVE_DATE_TO = "--effective-date-to";
    public static final String PERIOD_START = "--period-start";
    public static final String PERIOD_END = "--period-end";
    public static final String YEAR = "--year";
    public static final String THROUGH = "--through";
    public static final String EFFECTIVE_DATE_AS_OF = "--effective-date-as-of";
    public static final String AS_OF = "--as-of";
    public static final String MOVEMENTS = "--movements";

    private DateRange() {}
  }

  /** Report filtering and pagination options. */
  public static final class ReportQuery {
    public static final String COMPARATIVE = "--comparative";
    public static final String POSTING_COVERAGE = "--posting-coverage";
    public static final String LIMIT = "--limit";
    public static final String CURSOR = "--cursor";

    private ReportQuery() {}
  }

  /** New-book doctrine and initialization options. */
  public static final class BookDefinition {
    public static final String ENTITY_NAME = "--entity-name";
    public static final String TEMPLATE_ID = "--book-template-id";
    public static final String ACCOUNTING_BASIS = "--accounting-basis";
    public static final String INVENTORY_COSTING = "--inventory-costing";
    public static final String TIGHTEN_PARENTS = "--tighten-parents";
    public static final String FUNCTIONAL_CURRENCY = "--functional-currency";
    public static final String FISCAL_YEAR_START = "--fiscal-year-start";
    public static final String BOOK_START_EFFECTIVE_DATE = "--book-start-effective-date";

    private BookDefinition() {}
  }

  /** Text and artifact rendering options. */
  public static final class Presentation {
    public static final String WITH_CONTEXT = "--with-context";
    public static final String OUTPUT = "--output";
    public static final String PDF_OUT = "--pdf-out";

    private Presentation() {}
  }

  /** Discovery-response shaping options. */
  public static final class Discovery {
    public static final String DETAIL = "--detail";
    public static final String FOCUS = "--focus";
    public static final String CATEGORY = "--category";
    public static final String RESULT_DETAIL = "--result-detail";

    private Discovery() {}
  }

  /** Returns the accepted current-passphrase source options in public contract order. */
  public static List<String> bookPassphraseOptions() {
    return ProtocolBookAccessOptions.passphraseSourceOptions();
  }

  /** Returns the rendered current-passphrase source syntax. */
  public static String currentPassphraseSourceSyntax() {
    return ProtocolBookAccessOptions.passphraseSourceSyntax();
  }

  /** Returns the rendered optional page-limit syntax. */
  public static String optionalLimitSyntax() {
    return "[%s <%d-%d>]"
        .formatted(
            ReportQuery.LIMIT,
            ProtocolInteractionLimits.PAGE_LIMIT_MIN,
            ProtocolInteractionLimits.PAGE_LIMIT_MAX);
  }

  /** Returns the rendered optional page-cursor syntax. */
  public static String optionalCursorSyntax() {
    return "[" + ReportQuery.CURSOR + " <cursor>]";
  }

  /** Returns the rendered optional output-mode syntax for the supplied modes. */
  public static String optionalOutputSyntax(List<OutputMode> outputModes) {
    return "["
        + Presentation.OUTPUT
        + " <"
        + outputModes.stream()
            .map(OutputMode::wireValue)
            .collect(java.util.stream.Collectors.joining("|"))
        + ">]";
  }

  /** Returns the rendered optional PDF-export syntax for supported report commands. */
  public static String optionalPdfOutSyntax() {
    return "[" + Presentation.PDF_OUT + " <path>]";
  }

  /** Returns the rendered optional posting-coverage syntax for close-sensitive read models. */
  public static String optionalPostingCoverageSyntax() {
    return "["
        + ReportQuery.POSTING_COVERAGE
        + " <"
        + String.join(
            "|",
            dev.erst.fingrind.core.WireValue.wireValues(
                dev.erst.fingrind.core.PostingCoverage.class))
        + ">]";
  }

  /** Returns the rendered optional comparative syntax for as-of report commands. */
  public static String optionalAsOfComparativeSyntax() {
    return "[" + ReportQuery.COMPARATIVE + " <none|prior-period|..YYYY-MM-DD>]";
  }

  /** Returns the rendered optional comparative syntax for bounded-period report commands. */
  public static String optionalPeriodComparativeSyntax() {
    return "[" + ReportQuery.COMPARATIVE + " <none|prior-period|YYYY-MM-DD..YYYY-MM-DD>]";
  }

  /** Returns the published comparative capability mode inventory in stable wire order. */
  public static List<String> comparativeModes() {
    return WireValue.wireValues(ComparativeMode.class);
  }

  /** Returns the rendered optional execute-plan result-detail syntax. */
  public static String optionalResultDetailSyntax() {
    return "["
        + Discovery.RESULT_DETAIL
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(PlanResultDetail.class))
        + ">]";
  }

  /** Returns the rendered optional discovery-detail syntax. */
  public static String optionalDiscoveryDetailSyntax() {
    return "["
        + Discovery.DETAIL
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryDetail.class))
        + ">]";
  }

  /** Returns the rendered optional discovery-detail syntax for JSON-only discovery surfaces. */
  public static String optionalJsonOnlyDiscoveryDetailSyntax() {
    return "["
        + Discovery.DETAIL
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryDetail.class))
        + ">"
        + " (json only)]";
  }

  /** Returns the rendered optional discovery-focus syntax for JSON-only discovery surfaces. */
  public static String optionalJsonOnlyDiscoveryFocusSyntax() {
    return "["
        + Discovery.FOCUS
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryFocus.class))
        + ">"
        + " (json only)]";
  }

  /** Returns the rendered optional operation-category syntax for JSON-only discovery surfaces. */
  public static String optionalJsonOnlyOperationCategorySyntax() {
    return "["
        + Discovery.CATEGORY
        + " <"
        + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(OperationCategory.class))
        + ">"
        + " (json only)]";
  }
}
