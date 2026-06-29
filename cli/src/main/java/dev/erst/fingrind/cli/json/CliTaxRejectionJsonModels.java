package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Tax-context rejection-detail JSON records emitted by the CLI transport layer. */
public interface CliTaxRejectionJsonModels {

  /** Sealed category for tax-context rejection payloads. */
  sealed interface TaxRejectionDetails extends CliRejectionJsonModels.RejectionDetails
      permits TaxDefinitionViolationsDetails,
          UnknownTaxRegistrationDetails,
          ObligationPeriodMismatchDetails {}

  record TaxDefinitionViolationDetails(String code, @Nullable String field, String message) {
    public TaxDefinitionViolationDetails {
      code = requireText(code, "code");
      field = requireOptionalText(field, "field");
      message = requireText(message, "message");
    }
  }

  record TaxDefinitionViolationsDetails(List<TaxDefinitionViolationDetails> violations)
      implements TaxRejectionDetails {
    public TaxDefinitionViolationsDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }

  record UnknownTaxRegistrationDetails(String taxRegistrationId) implements TaxRejectionDetails {
    public UnknownTaxRegistrationDetails {
      taxRegistrationId = requireText(taxRegistrationId, "taxRegistrationId");
    }
  }

  record ObligationPeriodMismatchDetails(
      String obligationFrequency, String effectiveDateFrom, String effectiveDateTo)
      implements TaxRejectionDetails {
    public ObligationPeriodMismatchDetails {
      obligationFrequency = requireText(obligationFrequency, "obligationFrequency");
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
    }
  }
}
