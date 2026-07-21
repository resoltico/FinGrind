package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.WireValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Projects an account-registry request and its persisted semantic account effect. */
public final class AttestationAccountMutationProjection {
  private static final String CLI = "cli";

  private AttestationAccountMutationProjection() {}

  /**
   * Creates catalog-complete immutable preimages for one admitted account-registry mutation.
   *
   * <p>The request state reflects the caller's declared account definition; the effect state is the
   * exact registry snapshot that will be persisted in the same transaction. This distinction
   * preserves reactivation, retained lifecycle identity, and account-definition amendments.
   */
  public static AttestationOperationPreimages project(
      AttestationAccountMutationIntent mutationIntent,
      String operationKind,
      AttestationAccountSnapshot requested,
      AttestationAccountSnapshot persisted,
      AttestationEffectMutation effectMutation) {
    AttestationAccountMutationIntent checkedMutationIntent =
        Objects.requireNonNull(mutationIntent, "mutationIntent");
    String checkedOperationKind = Objects.requireNonNull(operationKind, "operationKind");
    AttestationAccountSnapshot checkedRequested = Objects.requireNonNull(requested, "requested");
    AttestationAccountSnapshot checkedPersisted = Objects.requireNonNull(persisted, "persisted");
    AttestationEffectMutation checkedEffectMutation =
        Objects.requireNonNull(effectMutation, "effectMutation");
    checkedMutationIntent.requireCompatible(checkedEffectMutation);
    if (!checkedRequested.accountCode().equals(checkedPersisted.accountCode())) {
      throw new IllegalArgumentException(
          "Account attestation request and effect must retain accountCode.");
    }
    return new AttestationOperationPreimages(
        requestPreimage(checkedOperationKind, checkedRequested).encoded(),
        effectPreimage(checkedPersisted, checkedEffectMutation).encoded());
  }

  private static AttestationPreimage requestPreimage(
      String operationKind, AttestationAccountSnapshot requested) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(command(operationKind));
    facts.add(accountRequest(requested));
    AttestationAccountTaxonomyPreimageProjection.appendFacts(facts, 0x0111, requested, null);
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage effectPreimage(
      AttestationAccountSnapshot persisted, AttestationEffectMutation effectMutation) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(accountEffect(persisted, effectMutation));
    AttestationAccountTaxonomyPreimageProjection.appendFacts(
        facts, 0x0011, persisted, effectMutation);
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage.Fact command(String operationKind) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            AttestationPreimageProjectionFields.token(operationKind),
            AttestationField.absent(),
            AttestationField.absent(),
            AttestationPreimageProjectionFields.token(CLI)));
  }

  private static AttestationPreimage.Fact accountRequest(AttestationAccountSnapshot snapshot) {
    return new AttestationPreimage.Fact(
        0x0110,
        List.of(
            AttestationPreimageProjectionFields.text(snapshot.accountCode().value()),
            AttestationPreimageProjectionFields.text(snapshot.accountName().value()),
            AttestationPreimageProjectionFields.token(token(snapshot.accountType())),
            AttestationPreimageProjectionFields.token(token(snapshot.accountTaxonomy().nodeKind())),
            AttestationPreimageProjectionFields.optionalText(
                snapshot
                    .accountTaxonomy()
                    .parentAccountCode()
                    .map(value -> value.value())
                    .orElse(null)),
            snapshot.unitOfMeasure() == null
                ? AttestationField.absent()
                : AttestationPreimageProjectionFields.token(snapshot.unitOfMeasure().token())));
  }

  private static AttestationPreimage.Fact accountEffect(
      AttestationAccountSnapshot snapshot, AttestationEffectMutation mutation) {
    return new AttestationPreimage.Fact(
        0x0010,
        List.of(
            AttestationPreimageProjectionFields.present(
                AttestationNumericFieldValue.mutation(mutation.wireValue())),
            AttestationPreimageProjectionFields.text(snapshot.accountCode().value()),
            AttestationPreimageProjectionFields.text(snapshot.accountName().value()),
            AttestationPreimageProjectionFields.token(token(snapshot.accountType())),
            AttestationPreimageProjectionFields.token(token(snapshot.accountTaxonomy().nodeKind())),
            AttestationPreimageProjectionFields.optionalText(
                snapshot
                    .accountTaxonomy()
                    .parentAccountCode()
                    .map(value -> value.value())
                    .orElse(null)),
            snapshot.unitOfMeasure() == null
                ? AttestationField.absent()
                : AttestationPreimageProjectionFields.token(snapshot.unitOfMeasure().token()),
            AttestationPreimageProjectionFields.present(
                AttestationNumericFieldValue.booleanValue(snapshot.active()))));
  }

  private static String token(WireValue value) {
    return Objects.requireNonNull(value, "value")
        .wireValue()
        .toLowerCase(java.util.Locale.ROOT)
        .replace('_', '-');
  }
}
