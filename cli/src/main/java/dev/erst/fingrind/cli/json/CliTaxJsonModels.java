package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireNonNegative;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Tax-context JSON records emitted by the CLI transport layer. */
public interface CliTaxJsonModels {

  record DeclaredTaxCodePayload(
      String taxCode,
      String taxCodeName,
      int ratePartsPerMillion,
      String inclusionMode,
      String applicationKind) {
    public DeclaredTaxCodePayload {
      taxCode = requireText(taxCode, "taxCode");
      taxCodeName = requireText(taxCodeName, "taxCodeName");
      requireNonNegative(ratePartsPerMillion, "ratePartsPerMillion");
      inclusionMode = requireText(inclusionMode, "inclusionMode");
      applicationKind = requireText(applicationKind, "applicationKind");
    }
  }

  record DeclaredTaxRegistrationPayload(
      String taxRegistrationId,
      String taxRegistrationName,
      String jurisdiction,
      @Nullable String registrationNumber,
      String payableAccountCode,
      String recoverableAccountCode,
      String obligationFrequency,
      int dueDaysAfterPeriodEnd,
      List<DeclaredTaxCodePayload> taxCodes,
      String declaredAt) {
    public DeclaredTaxRegistrationPayload {
      taxRegistrationId = requireText(taxRegistrationId, "taxRegistrationId");
      taxRegistrationName = requireText(taxRegistrationName, "taxRegistrationName");
      jurisdiction = requireText(jurisdiction, "jurisdiction");
      registrationNumber = requireOptionalText(registrationNumber, "registrationNumber");
      payableAccountCode = requireText(payableAccountCode, "payableAccountCode");
      recoverableAccountCode = requireText(recoverableAccountCode, "recoverableAccountCode");
      obligationFrequency = requireText(obligationFrequency, "obligationFrequency");
      requireNonNegative(dueDaysAfterPeriodEnd, "dueDaysAfterPeriodEnd");
      taxCodes = copyList(taxCodes, "taxCodes");
      declaredAt = requireText(declaredAt, "declaredAt");
    }
  }

  record TaxRegistrationMutationPayload(String outcome, DeclaredTaxRegistrationPayload registration)
      implements CliSuccessPayload {
    public TaxRegistrationMutationPayload {
      outcome = requireText(outcome, "outcome");
      Objects.requireNonNull(registration, "registration");
    }
  }

  record TaxRegistrationListPayload(
      CliBookQueryJsonModels.BookContextPayload context,
      int limit,
      @Nullable String nextCursor,
      List<DeclaredTaxRegistrationPayload> registrations)
      implements CliSuccessPayload {
    public TaxRegistrationListPayload {
      Objects.requireNonNull(context, "context");
      requirePositive(limit, "limit");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      registrations = copyList(registrations, "registrations");
    }
  }

  record TaxObligationContextPayload(
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      DeclaredTaxRegistrationPayload registration,
      String effectiveDateFrom,
      String effectiveDateTo,
      String dueDate) {
    public TaxObligationContextPayload {
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(registration, "registration");
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      dueDate = requireText(dueDate, "dueDate");
    }
  }

  record TaxObligationCodeSummaryPayload(
      String taxCode,
      String taxCodeName,
      String applicationKind,
      int postingCount,
      MonetaryAmount taxableAmount,
      MonetaryAmount taxAmount,
      MonetaryAmount grossAmount) {
    public TaxObligationCodeSummaryPayload {
      taxCode = requireText(taxCode, "taxCode");
      taxCodeName = requireText(taxCodeName, "taxCodeName");
      applicationKind = requireText(applicationKind, "applicationKind");
      requirePositive(postingCount, "postingCount");
      Objects.requireNonNull(taxableAmount, "taxableAmount");
      Objects.requireNonNull(taxAmount, "taxAmount");
      Objects.requireNonNull(grossAmount, "grossAmount");
    }
  }

  record TaxObligationPayload(
      TaxObligationContextPayload context,
      List<TaxObligationCodeSummaryPayload> codeSummaries,
      MonetaryAmount outputTax,
      MonetaryAmount recoverableInputTax,
      MonetaryAmount nonrecoverableInputTax,
      MonetaryAmount netPayable,
      MonetaryAmount netReceivable)
      implements CliSuccessPayload {
    public TaxObligationPayload {
      Objects.requireNonNull(context, "context");
      codeSummaries = copyList(codeSummaries, "codeSummaries");
      Objects.requireNonNull(outputTax, "outputTax");
      Objects.requireNonNull(recoverableInputTax, "recoverableInputTax");
      Objects.requireNonNull(nonrecoverableInputTax, "nonrecoverableInputTax");
      Objects.requireNonNull(netPayable, "netPayable");
      Objects.requireNonNull(netReceivable, "netReceivable");
    }
  }

  record TaxSelectionPayload(String taxRegistrationId, String taxCode) {
    public TaxSelectionPayload {
      taxRegistrationId = requireText(taxRegistrationId, "taxRegistrationId");
      taxCode = requireText(taxCode, "taxCode");
    }
  }

  record AppliedTaxPayload(
      String taxRegistrationId,
      String taxCode,
      String taxCodeName,
      int ratePartsPerMillion,
      String inclusionMode,
      String applicationKind,
      MonetaryAmount taxableAmount,
      MonetaryAmount taxAmount,
      MonetaryAmount grossAmount,
      @Nullable String taxAccountCode) {
    public AppliedTaxPayload {
      taxRegistrationId = requireText(taxRegistrationId, "taxRegistrationId");
      taxCode = requireText(taxCode, "taxCode");
      taxCodeName = requireText(taxCodeName, "taxCodeName");
      requireNonNegative(ratePartsPerMillion, "ratePartsPerMillion");
      inclusionMode = requireText(inclusionMode, "inclusionMode");
      applicationKind = requireText(applicationKind, "applicationKind");
      Objects.requireNonNull(taxableAmount, "taxableAmount");
      Objects.requireNonNull(taxAmount, "taxAmount");
      Objects.requireNonNull(grossAmount, "grossAmount");
      taxAccountCode = requireOptionalText(taxAccountCode, "taxAccountCode");
    }
  }
}
