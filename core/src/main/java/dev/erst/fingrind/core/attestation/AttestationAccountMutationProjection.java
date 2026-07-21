package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.WireValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    addRequestTaxonomyFacts(facts, requested);
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage effectPreimage(
      AttestationAccountSnapshot persisted, AttestationEffectMutation effectMutation) {
    List<AttestationPreimage.Fact> facts = new ArrayList<>();
    facts.add(accountEffect(persisted, effectMutation));
    addEffectTaxonomyFacts(facts, persisted, effectMutation);
    return AttestationPreimage.of(facts);
  }

  private static AttestationPreimage.Fact command(String operationKind) {
    return new AttestationPreimage.Fact(
        0x0100,
        List.of(
            presentToken(operationKind),
            AttestationField.absent(),
            AttestationField.absent(),
            presentToken(CLI)));
  }

  private static AttestationPreimage.Fact accountRequest(AttestationAccountSnapshot snapshot) {
    return new AttestationPreimage.Fact(
        0x0110,
        List.of(
            presentText(snapshot.accountCode().value()),
            presentText(snapshot.accountName().value()),
            presentToken(token(snapshot.accountType())),
            presentToken(token(snapshot.accountTaxonomy().nodeKind())),
            optionalText(snapshot.accountTaxonomy().parentAccountCode().map(AccountCode::value)),
            optionalText(Optional.ofNullable(snapshot.unitOfMeasure()).map(unit -> unit.token()))));
  }

  private static AttestationPreimage.Fact accountEffect(
      AttestationAccountSnapshot snapshot, AttestationEffectMutation mutation) {
    return new AttestationPreimage.Fact(
        0x0010,
        List.of(
            present(AttestationNumericFieldValue.mutation(mutation.wireValue())),
            presentText(snapshot.accountCode().value()),
            presentText(snapshot.accountName().value()),
            presentToken(token(snapshot.accountType())),
            presentToken(token(snapshot.accountTaxonomy().nodeKind())),
            optionalText(snapshot.accountTaxonomy().parentAccountCode().map(AccountCode::value)),
            optionalText(Optional.ofNullable(snapshot.unitOfMeasure()).map(unit -> unit.token())),
            present(AttestationNumericFieldValue.booleanValue(snapshot.active()))));
  }

  private static void addRequestTaxonomyFacts(
      List<AttestationPreimage.Fact> facts, AttestationAccountSnapshot snapshot) {
    AccountTaxonomy taxonomy = snapshot.accountTaxonomy();
    addClassifications(
        facts,
        0x0111,
        snapshot,
        null,
        taxonomy
            .financialPositionLineClassification()
            .map(AttestationAccountMutationProjection::token),
        taxonomy.profitAndLossLineClassification().map(AttestationAccountMutationProjection::token),
        taxonomy.cashFlowAssetClassification().map(AttestationAccountMutationProjection::token));
    addRelationships(facts, 0x0112, snapshot, null);
  }

  private static void addEffectTaxonomyFacts(
      List<AttestationPreimage.Fact> facts,
      AttestationAccountSnapshot snapshot,
      AttestationEffectMutation mutation) {
    AccountTaxonomy taxonomy = snapshot.accountTaxonomy();
    addClassifications(
        facts,
        0x0011,
        snapshot,
        mutation,
        taxonomy
            .financialPositionLineClassification()
            .map(AttestationAccountMutationProjection::token),
        taxonomy.profitAndLossLineClassification().map(AttestationAccountMutationProjection::token),
        taxonomy.cashFlowAssetClassification().map(AttestationAccountMutationProjection::token));
    addRelationships(facts, 0x0012, snapshot, mutation);
  }

  private static void addClassifications(
      List<AttestationPreimage.Fact> facts,
      int recordType,
      AttestationAccountSnapshot snapshot,
      @org.jspecify.annotations.Nullable AttestationEffectMutation mutation,
      Optional<String> financialPosition,
      Optional<String> profitAndLoss,
      Optional<String> cashFlowAsset) {
    addClassification(
        facts,
        recordType,
        snapshot,
        mutation,
        ClassificationFamily.FINANCIAL_POSITION.token(),
        financialPosition);
    addClassification(facts, recordType, snapshot, mutation, "profit-and-loss", profitAndLoss);
    addClassification(facts, recordType, snapshot, mutation, "cash-flow-asset", cashFlowAsset);
  }

  /** Canonical taxonomy-field vocabulary used by immutable account snapshots. */
  private enum ClassificationFamily {
    FINANCIAL_POSITION(String.join("-", "financial", "position"));

    private final String token;

    ClassificationFamily(String token) {
      this.token = token;
    }

    String token() {
      return token;
    }
  }

  private static void addClassification(
      List<AttestationPreimage.Fact> facts,
      int recordType,
      AttestationAccountSnapshot snapshot,
      @org.jspecify.annotations.Nullable AttestationEffectMutation mutation,
      String family,
      Optional<String> classification) {
    classification.ifPresent(
        value -> {
          List<AttestationField> fields = new ArrayList<>();
          if (mutation != null) {
            fields.add(present(AttestationNumericFieldValue.mutation(mutation.wireValue())));
          }
          fields.add(presentText(snapshot.accountCode().value()));
          fields.add(presentToken(family));
          fields.add(presentToken(value));
          facts.add(new AttestationPreimage.Fact(recordType, fields));
        });
  }

  private static void addRelationships(
      List<AttestationPreimage.Fact> facts,
      int recordType,
      AttestationAccountSnapshot snapshot,
      @org.jspecify.annotations.Nullable AttestationEffectMutation mutation) {
    addRelationship(
        facts,
        recordType,
        snapshot,
        mutation,
        "parent",
        snapshot.accountTaxonomy().parentAccountCode().map(AccountCode::value));
    addRelationship(
        facts,
        recordType,
        snapshot,
        mutation,
        "contra",
        snapshot.accountTaxonomy().contraOfAccountCode().map(AccountCode::value));
  }

  private static void addRelationship(
      List<AttestationPreimage.Fact> facts,
      int recordType,
      AttestationAccountSnapshot snapshot,
      @org.jspecify.annotations.Nullable AttestationEffectMutation mutation,
      String relationshipKind,
      Optional<String> targetAccountCode) {
    targetAccountCode.ifPresent(
        target -> {
          List<AttestationField> fields = new ArrayList<>();
          if (mutation != null) {
            fields.add(present(AttestationNumericFieldValue.mutation(mutation.wireValue())));
          }
          fields.add(presentText(snapshot.accountCode().value()));
          fields.add(presentToken(relationshipKind));
          fields.add(presentText(target));
          facts.add(new AttestationPreimage.Fact(recordType, fields));
        });
  }

  private static String token(WireValue value) {
    return token(Objects.requireNonNull(value, "value").wireValue());
  }

  private static String token(String value) {
    return Objects.requireNonNull(value, "value")
        .toLowerCase(java.util.Locale.ROOT)
        .replace('_', '-');
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

  private static AttestationField optionalText(Optional<String> value) {
    return value
        .<AttestationField>map(AttestationAccountMutationProjection::presentText)
        .orElseGet(AttestationField::absent);
  }
}
