package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationTaxCodeSnapshot;
import dev.erst.fingrind.core.attestation.AttestationTaxRegistrationMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationTaxRegistrationSnapshot;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/** Maps tax-registration commands and snapshots to their durable attestation facts. */
final class SqliteTaxRegistrationMutationMapper {
  private SqliteTaxRegistrationMutationMapper() {}

  static DeclaredTaxRegistration declaredTaxRegistrationSnapshot(
      DeclareTaxRegistrationCommand command, Instant snapshotDeclaredAt) {
    return new DeclaredTaxRegistration(
        command.taxRegistrationId(),
        command.taxRegistrationName(),
        command.jurisdiction(),
        command.registrationNumber(),
        command.payableAccountCode(),
        command.recoverableAccountCode(),
        command.obligationFrequency(),
        command.dueDaysAfterPeriodEnd(),
        command.taxCodes().stream()
            .sorted(Comparator.comparing(SqliteTaxRegistrationMutationMapper::taxCodeKey))
            .toList(),
        snapshotDeclaredAt);
  }

  static AttestationOperationPreimages attestationPreimages(
      DeclareTaxRegistrationCommand command,
      DeclaredTaxRegistration registration,
      boolean updatesExistingRegistration,
      AttestationOperationKind operationKind) {
    Objects.requireNonNull(operationKind, "operationKind");
    return AttestationTaxRegistrationMutationProjection.project(
        operationKind.wireToken(),
        taxRegistrationSnapshot(command),
        taxRegistrationSnapshot(registration),
        updatesExistingRegistration
            ? AttestationEffectMutation.AMEND
            : AttestationEffectMutation.CREATE);
  }

  private static String taxCodeKey(TaxCodeDefinition taxCodeDefinition) {
    return taxCodeDefinition.taxCode().value();
  }

  private static AttestationTaxRegistrationSnapshot taxRegistrationSnapshot(
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
            .map(SqliteTaxRegistrationMutationMapper::taxCodeSnapshot)
            .toList());
  }

  private static AttestationTaxRegistrationSnapshot taxRegistrationSnapshot(
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
            .map(SqliteTaxRegistrationMutationMapper::taxCodeSnapshot)
            .toList());
  }

  private static AttestationTaxCodeSnapshot taxCodeSnapshot(TaxCodeDefinition taxCode) {
    return new AttestationTaxCodeSnapshot(
        taxCode.taxCode().value(),
        taxCode.taxCodeName().value(),
        taxCode.rate().partsPerMillionOfWhole(),
        taxCode.inclusionMode().wireValue(),
        taxCode.applicationKind().wireValue());
  }
}
