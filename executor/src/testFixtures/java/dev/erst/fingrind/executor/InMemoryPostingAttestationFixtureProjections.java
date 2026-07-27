package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationPostingEffectSnapshot;
import dev.erst.fingrind.core.attestation.AttestationPostingEvidenceDocument;
import dev.erst.fingrind.core.attestation.AttestationPostingLine;
import dev.erst.fingrind.core.attestation.AttestationPostingRequestSnapshot;
import dev.erst.fingrind.core.attestation.AttestationTaxCodeSnapshot;
import dev.erst.fingrind.core.attestation.AttestationTaxRegistrationSnapshot;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.util.UUID;

/** Builds attestation-facing values from in-memory posting and tax-registration fixture state. */
final class InMemoryPostingAttestationFixtureProjections {
  private InMemoryPostingAttestationFixtureProjections() {}

  static AttestationPostingRequestSnapshot postingRequestSnapshot(PostingDraft postingDraft) {
    return new AttestationPostingRequestSnapshot(
        AttestationOperationKind.POST_ENTRY.wireToken(),
        postingDraft.provenance().requestProvenance().idempotencyKey().value(),
        postingDraft.provenance().requestProvenance().causationId().value(),
        postingDraft.provenance().sourceChannel().wireValue(),
        postingDraft.journalEntry().effectiveDate(),
        postingDraft.postingKind().wireValue(),
        postingDraft
            .postingLineage()
            .reversalReference()
            .map(reference -> reference.priorPostingId().value())
            .orElse(null),
        postingDraft.postingLineage().reversalReason().map(reason -> reason.value()).orElse(null),
        postingDraft.evidence().sourceDocuments().stream()
            .map(
                document ->
                    new AttestationPostingEvidenceDocument(
                        document.sourceDocumentId().value(),
                        document.sourceDocumentType().value(),
                        document.documentDate()))
            .toList(),
        postingDraft.journalEntry().lines().stream()
            .map(
                line ->
                    new AttestationPostingLine(
                        line.accountCode().value(),
                        line.side().wireValue(),
                        line.amount().currencyUnit().code(),
                        line.amount().minorUnits()))
            .toList());
  }

  static AttestationPostingEffectSnapshot postingEffectSnapshot(CommittedPosting posting) {
    return new AttestationPostingEffectSnapshot(
        UUID.fromString(posting.postingId().value()),
        AttestationOperationKind.POST_ENTRY.wireToken(),
        posting.postingKind().wireValue(),
        posting.postingOriginKind().wireValue(),
        posting.provenance().recordedAt(),
        posting
            .postingLineage()
            .reversalReference()
            .map(reference -> UUID.fromString(reference.priorPostingId().value()))
            .orElse(null),
        UUID.fromString(posting.provenance().requestProvenance().commandId().value()));
  }

  static AttestationTaxRegistrationSnapshot taxRegistrationSnapshot(
      DeclareTaxRegistrationCommand registration) {
    return new AttestationTaxRegistrationSnapshot(
        registration.taxRegistrationId().value(),
        registration.taxRegistrationName().value(),
        registration.jurisdiction().value(),
        registration.registrationNumber() == null
            ? null
            : registration.registrationNumber().value(),
        registration.payableAccountCode().value(),
        registration.recoverableAccountCode().value(),
        registration.obligationFrequency().wireValue(),
        registration.dueDaysAfterPeriodEnd(),
        registration.taxCodes().stream()
            .map(InMemoryPostingAttestationFixtureProjections::taxCodeSnapshot)
            .toList());
  }

  static AttestationTaxRegistrationSnapshot taxRegistrationSnapshot(
      DeclaredTaxRegistration registration) {
    return new AttestationTaxRegistrationSnapshot(
        registration.taxRegistrationId().value(),
        registration.taxRegistrationName().value(),
        registration.jurisdiction().value(),
        registration.registrationNumber() == null
            ? null
            : registration.registrationNumber().value(),
        registration.payableAccountCode().value(),
        registration.recoverableAccountCode().value(),
        registration.obligationFrequency().wireValue(),
        registration.dueDaysAfterPeriodEnd(),
        registration.taxCodes().stream()
            .map(InMemoryPostingAttestationFixtureProjections::taxCodeSnapshot)
            .toList());
  }

  private static AttestationTaxCodeSnapshot taxCodeSnapshot(
      dev.erst.fingrind.contract.tax.TaxCodeDefinition taxCode) {
    return new AttestationTaxCodeSnapshot(
        taxCode.taxCode().value(),
        taxCode.taxCodeName().value(),
        taxCode.rate().partsPerMillionOfWhole(),
        taxCode.inclusionMode().wireValue(),
        taxCode.applicationKind().wireValue());
  }
}
