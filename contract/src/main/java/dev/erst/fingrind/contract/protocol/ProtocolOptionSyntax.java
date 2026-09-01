package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.ComparativeMode;
import dev.erst.fingrind.core.WireValue;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationLimits;
import java.util.List;

/** Canonical rendered CLI option syntax grouped by the option family that owns it. */
public interface ProtocolOptionSyntax {

  /** Renders accepted passphrase-source syntax for protected-book access. */
  public static final class BookAccess {
    private BookAccess() {}

    /** Returns the accepted current-passphrase source options in public contract order. */
    public static List<String> bookPassphraseOptions() {
      return ProtocolBookAccessOptions.passphraseSourceOptions();
    }

    /** Returns the rendered current-passphrase source syntax. */
    public static String currentPassphraseSourceSyntax() {
      return ProtocolBookAccessOptions.passphraseSourceSyntax();
    }
  }

  /** Renders credential syntax for protected-book authorization. */
  public static final class Attestation {
    private Attestation() {}

    /** Returns the required authorization-credential syntax for protected-book mutations. */
    public static String requiredCredentialSyntax() {
      return ProtocolOptions.Attestation.CUSTODIAN
          + " <file-pkcs8> "
          + ProtocolOptions.Attestation.PRINCIPAL_ID
          + " <uuid> "
          + ProtocolOptions.Attestation.KEY_FILE
          + " <path> "
          + ProtocolOptions.Attestation.PASSPHRASE_FILE
          + " <path> (repeat one through "
          + AttestationAuthorizationLimits.MAXIMUM_QUORUM
          + " aligned credential triplets under the selected custody)";
    }

    /**
     * Returns the conditional attestation-credential syntax for {@code execute-plan}.
     *
     * <p>The bracket means that the tuple is selected only after the request document establishes
     * that the plan contains a protected-book mutation. It is required for such a plan and
     * forbidden for a query-only or assertion-only plan; it is not an independently optional
     * mutation credential.
     */
    public static String conditionalExecutePlanCredentialSyntax() {
      return "[" + requiredCredentialSyntax() + "]";
    }
  }

  /** Renders pagination and report-selection syntax. */
  public static final class ReportQuery {
    private ReportQuery() {}

    /** Returns the rendered optional page-limit syntax. */
    public static String optionalLimitSyntax() {
      return "[%s <%d-%d>]"
          .formatted(
              ProtocolOptions.ReportQuery.LIMIT,
              ProtocolInteractionLimits.PAGE_LIMIT_MIN,
              ProtocolInteractionLimits.PAGE_LIMIT_MAX);
    }

    /** Returns the rendered optional page-cursor syntax. */
    public static String optionalCursorSyntax() {
      return "[" + ProtocolOptions.ReportQuery.CURSOR + " <cursor>]";
    }

    /** Returns the rendered optional posting-coverage syntax for close-sensitive read models. */
    public static String optionalPostingCoverageSyntax() {
      return "["
          + ProtocolOptions.ReportQuery.POSTING_COVERAGE
          + " <"
          + String.join(
              "|",
              dev.erst.fingrind.core.WireValue.wireValues(
                  dev.erst.fingrind.core.PostingCoverage.class))
          + ">]";
    }

    /** Returns the rendered optional comparative syntax for as-of report commands. */
    public static String optionalAsOfComparativeSyntax() {
      return "["
          + ProtocolOptions.ReportQuery.COMPARATIVE
          + " <none|same-period-prior-year|..YYYY-MM-DD>]";
    }

    /** Returns the rendered optional comparative syntax for bounded-period report commands. */
    public static String optionalPeriodComparativeSyntax() {
      return "["
          + ProtocolOptions.ReportQuery.COMPARATIVE
          + " <none|same-period-prior-year|YYYY-MM-DD..YYYY-MM-DD>]";
    }

    /** Returns the published comparative capability mode inventory in stable wire order. */
    public static List<String> comparativeModes() {
      return WireValue.wireValues(ComparativeMode.class);
    }
  }

  /** Renders output-mode and artifact-export syntax. */
  public static final class Presentation {
    private Presentation() {}

    /** Returns the rendered optional output-mode syntax for the supplied modes. */
    public static String optionalOutputSyntax(List<OutputMode> outputModes) {
      return "["
          + ProtocolOptions.Presentation.OUTPUT
          + " <"
          + outputModes.stream()
              .map(OutputMode::wireValue)
              .collect(java.util.stream.Collectors.joining("|"))
          + ">]";
    }

    /** Returns the rendered optional PDF-export syntax for supported report commands. */
    public static String optionalPdfOutSyntax() {
      return "[" + ProtocolOptions.Presentation.PDF_OUT + " <path>]";
    }
  }

  /** Renders discovery and plan-result detail syntax. */
  public static final class Discovery {
    private Discovery() {}

    /** Returns the rendered optional execute-plan result-detail syntax. */
    public static String optionalResultDetailSyntax() {
      return "["
          + ProtocolOptions.Discovery.RESULT_DETAIL
          + " <"
          + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(PlanResultDetail.class))
          + ">]";
    }

    /** Returns the rendered optional discovery-detail syntax. */
    public static String optionalDiscoveryDetailSyntax() {
      return "["
          + ProtocolOptions.Discovery.DETAIL
          + " <"
          + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryDetail.class))
          + ">]";
    }

    /** Returns the rendered optional discovery-detail syntax for JSON-only discovery surfaces. */
    public static String optionalJsonOnlyDiscoveryDetailSyntax() {
      return "["
          + ProtocolOptions.Discovery.DETAIL
          + " <"
          + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryDetail.class))
          + ">"
          + " (json only)]";
    }

    /** Returns the rendered optional discovery-focus syntax for JSON-only discovery surfaces. */
    public static String optionalJsonOnlyDiscoveryFocusSyntax() {
      return "["
          + ProtocolOptions.Discovery.FOCUS
          + " <"
          + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(DiscoveryFocus.class))
          + ">"
          + " (json only)]";
    }

    /** Returns the rendered optional operation-category syntax for JSON-only discovery surfaces. */
    public static String optionalJsonOnlyOperationCategorySyntax() {
      return "["
          + ProtocolOptions.Discovery.CATEGORY
          + " <"
          + String.join("|", dev.erst.fingrind.core.WireValue.wireValues(OperationCategory.class))
          + ">"
          + " (json only)]";
    }
  }
}
