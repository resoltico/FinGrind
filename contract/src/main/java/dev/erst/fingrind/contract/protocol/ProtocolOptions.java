package dev.erst.fingrind.contract.protocol;

/** Canonical public CLI option spellings used by the protocol catalog and parser. */
public interface ProtocolOptions {

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
    public static final String FUNCTIONAL_CURRENCY = "--functional-currency";
    public static final String FISCAL_YEAR_START = "--fiscal-year-start";
    public static final String BOOK_START_EFFECTIVE_DATE = "--book-start-effective-date";

    private BookDefinition() {}
  }

  /** Attestation credential sources required for protected-book authorization. */
  public static final class Attestation {
    public static final String FOUNDER_PRINCIPAL_ID = "--attestation-founder-principal-id";
    public static final String FOUNDER_KEY_FILE = "--attestation-founder-key-file";
    public static final String FOUNDER_PASSPHRASE_FILE = "--attestation-founder-passphrase-file";
    public static final String CUSTODIAN = "--attestation-custodian";
    public static final String PRINCIPAL_ID = "--attestation-principal-id";
    public static final String KEY_FILE = "--attestation-key-file";
    public static final String NEW_KEY_FILE = "--new-attestation-key-file";
    public static final String PASSPHRASE_FILE = "--attestation-passphrase-file";
    public static final String REQUIRE_CLEAN = "--require-clean-attestation";
    public static final String REVIEW_FILE = "--attestation-review-file";
    public static final String RECEIPT_FILE = "--receipt-file";

    private Attestation() {}
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
}
