package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Semantic machine payloads for tax obligation reports. */
public interface CliTaxReportJsonModels {
  record TaxObligationPayload(
      String family,
      CliBookInspectionJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.TaxObligationResolvedQuery resolvedQuery,
      String generatedAt,
      TaxRegistrationPayload taxRegistration,
      String dueDate,
      List<TaxObligationRowPayload> rows,
      TaxObligationTotalsPayload totals)
      implements CliReportJsonModels.ReportPayload {
    public TaxObligationPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      Objects.requireNonNull(taxRegistration, "taxRegistration");
      dueDate = requireText(dueDate, "dueDate");
      rows = copyList(rows, "rows");
      Objects.requireNonNull(totals, "totals");
    }
  }

  record TaxRegistrationPayload(
      String taxRegistrationId,
      String taxRegistrationName,
      String jurisdiction,
      @Nullable String registrationNumber,
      String obligationFrequency) {
    public TaxRegistrationPayload {
      taxRegistrationId = requireText(taxRegistrationId, "taxRegistrationId");
      taxRegistrationName = requireText(taxRegistrationName, "taxRegistrationName");
      jurisdiction = requireText(jurisdiction, "jurisdiction");
      registrationNumber = requireOptionalText(registrationNumber, "registrationNumber");
      obligationFrequency = requireText(obligationFrequency, "obligationFrequency");
    }
  }

  record TaxObligationRowPayload(
      String taxCode,
      String taxCodeName,
      String application,
      int postings,
      CliReportValueJsonModels.MoneyPayload taxable,
      CliReportValueJsonModels.MoneyPayload tax,
      CliReportValueJsonModels.MoneyPayload gross) {
    public TaxObligationRowPayload {
      taxCode = requireText(taxCode, "taxCode");
      taxCodeName = requireText(taxCodeName, "taxCodeName");
      application = requireText(application, "application");
      Objects.requireNonNull(taxable, "taxable");
      Objects.requireNonNull(tax, "tax");
      Objects.requireNonNull(gross, "gross");
    }
  }

  record TaxObligationTotalsPayload(
      CliReportValueJsonModels.MoneyPayload outputTax,
      CliReportValueJsonModels.MoneyPayload recoverableInputTax,
      CliReportValueJsonModels.MoneyPayload nonrecoverableInputTax,
      CliReportValueJsonModels.MoneyPayload netPayable,
      CliReportValueJsonModels.MoneyPayload netReceivable) {
    public TaxObligationTotalsPayload {
      Objects.requireNonNull(outputTax, "outputTax");
      Objects.requireNonNull(recoverableInputTax, "recoverableInputTax");
      Objects.requireNonNull(nonrecoverableInputTax, "nonrecoverableInputTax");
      Objects.requireNonNull(netPayable, "netPayable");
      Objects.requireNonNull(netReceivable, "netReceivable");
    }
  }
}
