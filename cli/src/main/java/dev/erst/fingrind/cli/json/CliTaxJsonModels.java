package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireNonNegative;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
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

  record TaxRegistrationMutationPayload(
      String outcome,
      DeclaredTaxRegistrationPayload registration,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public TaxRegistrationMutationPayload {
      outcome = requireText(outcome, "outcome");
      Objects.requireNonNull(registration, "registration");
    }
  }

  record TaxRegistrationListPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      TaxRegistrationListResolvedQuery resolvedQuery,
      String generatedAt,
      @Nullable String nextCursor,
      List<DeclaredTaxRegistrationPayload> registrations)
      implements ProtocolSuccessPayload {
    public TaxRegistrationListPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      registrations = copyList(registrations, "registrations");
    }
  }

  /** The exact accepted tax-registration page selection. */
  record TaxRegistrationListResolvedQuery(int limit, @Nullable String cursor) {
    public TaxRegistrationListResolvedQuery {
      requirePositive(limit, "limit");
      cursor = requireOptionalText(cursor, "cursor");
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
