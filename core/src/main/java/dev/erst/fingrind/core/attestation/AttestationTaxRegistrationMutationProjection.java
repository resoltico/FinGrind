package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Projects a complete tax-registration request and its committed domain effect. */
public final class AttestationTaxRegistrationMutationProjection {

  private AttestationTaxRegistrationMutationProjection() {}

  /**
   * Creates catalog-complete immutable preimages for a create or replacement tax-registration
   * declaration.
   */
  public static AttestationOperationPreimages project(
      String operationKind,
      AttestationTaxRegistrationSnapshot requested,
      AttestationTaxRegistrationSnapshot persisted,
      AttestationEffectMutation effectMutation) {
    String checkedOperationKind = Objects.requireNonNull(operationKind, "operationKind");
    AttestationTaxRegistrationSnapshot checkedRequested =
        Objects.requireNonNull(requested, "requested");
    AttestationTaxRegistrationSnapshot checkedPersisted =
        Objects.requireNonNull(persisted, "persisted");
    AttestationEffectMutation checkedMutation =
        Objects.requireNonNull(effectMutation, "effectMutation");
    if (checkedMutation != AttestationEffectMutation.CREATE
        && checkedMutation != AttestationEffectMutation.AMEND) {
      throw new IllegalArgumentException("Tax registration effects must create or amend state.");
    }
    if (!checkedRequested.registrationId().equals(checkedPersisted.registrationId())) {
      throw new IllegalArgumentException(
          "Tax-registration attestation request and effect must retain registrationId.");
    }
    return new AttestationOperationPreimages(
        requestPreimage(checkedOperationKind, checkedRequested).encoded(),
        effectPreimage(checkedPersisted, checkedMutation).encoded());
  }

  private static AttestationPreimage requestPreimage(
      String operationKind, AttestationTaxRegistrationSnapshot snapshot) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(command(operationKind));
    facts.add(registrationRequest(snapshot));
    snapshot.taxCodes().forEach(code -> facts.add(codeRequest(snapshot.registrationId(), code)));
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage effectPreimage(
      AttestationTaxRegistrationSnapshot snapshot, AttestationEffectMutation mutation) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(registrationEffect(snapshot, mutation));
    snapshot
        .taxCodes()
        .forEach(code -> facts.add(codeEffect(snapshot.registrationId(), code, mutation)));
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage.Fact command(String operationKind) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            presentToken(operationKind),
            AttestationField.absent(),
            AttestationField.absent(),
            presentToken("cli")));
  }

  private static AttestationPreimage.Fact registrationRequest(
      AttestationTaxRegistrationSnapshot snapshot) {
    return new AttestationPreimage.Fact(
        0x0113,
        List.of(
            presentText(snapshot.registrationId()),
            presentText(snapshot.registrationName()),
            presentText(snapshot.jurisdiction()),
            optionalText(snapshot.registrationNumber()),
            presentText(snapshot.payableAccountCode()),
            presentText(snapshot.receivableAccountCode()),
            presentToken(token(snapshot.obligationFrequency())),
            present(AttestationNumericFieldValue.unsigned16(snapshot.dueDaysAfterPeriodEnd())),
            present(AttestationNumericFieldValue.booleanValue(true))));
  }

  private static AttestationPreimage.Fact registrationEffect(
      AttestationTaxRegistrationSnapshot snapshot, AttestationEffectMutation mutation) {
    return new AttestationPreimage.Fact(
        0x0013,
        List.of(
            present(AttestationNumericFieldValue.mutation(mutation.wireValue())),
            presentText(snapshot.registrationId()),
            presentText(snapshot.registrationName()),
            presentText(snapshot.jurisdiction()),
            optionalText(snapshot.registrationNumber()),
            presentText(snapshot.payableAccountCode()),
            presentText(snapshot.receivableAccountCode()),
            presentToken(token(snapshot.obligationFrequency())),
            present(AttestationNumericFieldValue.unsigned16(snapshot.dueDaysAfterPeriodEnd())),
            present(AttestationNumericFieldValue.booleanValue(true))));
  }

  private static AttestationPreimage.Fact codeRequest(
      String registrationId, AttestationTaxCodeSnapshot taxCode) {
    return new AttestationPreimage.Fact(
        0x0114,
        List.of(
            presentText(registrationId),
            presentText(taxCode.taxCode()),
            presentText(taxCode.taxCodeName()),
            rate(taxCode),
            presentToken(token(taxCode.inclusionMode())),
            presentToken(token(taxCode.applicationKind()))));
  }

  private static AttestationPreimage.Fact codeEffect(
      String registrationId,
      AttestationTaxCodeSnapshot taxCode,
      AttestationEffectMutation mutation) {
    return new AttestationPreimage.Fact(
        0x0014,
        List.of(
            present(AttestationNumericFieldValue.mutation(mutation.wireValue())),
            presentText(registrationId),
            presentText(taxCode.taxCode()),
            presentText(taxCode.taxCodeName()),
            rate(taxCode),
            presentToken(token(taxCode.inclusionMode())),
            presentToken(token(taxCode.applicationKind()))));
  }

  private static AttestationField rate(AttestationTaxCodeSnapshot taxCode) {
    return present(
        AttestationNumericFieldValue.scaled(
            6, false, BigInteger.valueOf(taxCode.ratePartsPerMillionOfWhole())));
  }

  private static String token(String value) {
    return Objects.requireNonNull(value, "value").toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }

  private static AttestationField presentText(String value) {
    return present(AttestationTextFieldValue.text(value));
  }

  private static AttestationField presentToken(String value) {
    return present(AttestationTextFieldValue.token(value));
  }

  private static AttestationField optionalText(@Nullable String value) {
    return Optional.ofNullable(value)
        .<AttestationField>map(AttestationTaxRegistrationMutationProjection::presentText)
        .orElseGet(AttestationField::absent);
  }
}
