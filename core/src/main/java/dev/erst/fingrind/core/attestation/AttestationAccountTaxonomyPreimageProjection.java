package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.WireValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Projects an account snapshot's taxonomy classifications and account relationships into facts. */
final class AttestationAccountTaxonomyPreimageProjection {
  private AttestationAccountTaxonomyPreimageProjection() {}

  static void appendFacts(
      List<AttestationPreimage.Fact> facts,
      int recordType,
      AttestationAccountSnapshot snapshot,
      @Nullable AttestationEffectMutation mutation) {
    AccountTaxonomy taxonomy = snapshot.accountTaxonomy();
    appendClassifications(
        facts,
        recordType,
        snapshot,
        mutation,
        taxonomy
            .financialPositionLineClassification()
            .map(AttestationAccountTaxonomyPreimageProjection::token),
        taxonomy
            .profitAndLossLineClassification()
            .map(AttestationAccountTaxonomyPreimageProjection::token),
        taxonomy
            .cashFlowAssetClassification()
            .map(AttestationAccountTaxonomyPreimageProjection::token));
    appendRelationship(
        facts,
        recordType + 1,
        snapshot,
        mutation,
        "parent",
        taxonomy.parentAccountCode().map(AccountCode::value));
    appendRelationship(
        facts,
        recordType + 1,
        snapshot,
        mutation,
        "contra",
        taxonomy.contraOfAccountCode().map(AccountCode::value));
  }

  private static void appendClassifications(
      List<AttestationPreimage.Fact> facts,
      int recordType,
      AttestationAccountSnapshot snapshot,
      @Nullable AttestationEffectMutation mutation,
      Optional<String> financialPosition,
      Optional<String> profitAndLoss,
      Optional<String> cashFlowAsset) {
    appendClassification(
        facts,
        recordType,
        snapshot,
        mutation,
        ClassificationFamily.FINANCIAL_POSITION.wireToken(),
        financialPosition);
    appendClassification(
        facts,
        recordType,
        snapshot,
        mutation,
        ClassificationFamily.PROFIT_AND_LOSS.wireToken(),
        profitAndLoss);
    appendClassification(
        facts,
        recordType,
        snapshot,
        mutation,
        ClassificationFamily.CASH_FLOW_ASSET.wireToken(),
        cashFlowAsset);
  }

  private static void appendClassification(
      List<AttestationPreimage.Fact> facts,
      int recordType,
      AttestationAccountSnapshot snapshot,
      @Nullable AttestationEffectMutation mutation,
      String family,
      Optional<String> classification) {
    classification.ifPresent(
        value -> {
          List<AttestationField> fields = fields(snapshot.accountCode().value(), mutation);
          fields.add(AttestationPreimageProjectionFields.token(family));
          fields.add(AttestationPreimageProjectionFields.token(value));
          facts.add(new AttestationPreimage.Fact(recordType, fields));
        });
  }

  private static void appendRelationship(
      List<AttestationPreimage.Fact> facts,
      int recordType,
      AttestationAccountSnapshot snapshot,
      @Nullable AttestationEffectMutation mutation,
      String relationshipKind,
      Optional<String> targetAccountCode) {
    targetAccountCode.ifPresent(
        target -> {
          List<AttestationField> fields = fields(snapshot.accountCode().value(), mutation);
          fields.add(AttestationPreimageProjectionFields.token(relationshipKind));
          fields.add(AttestationPreimageProjectionFields.text(target));
          facts.add(new AttestationPreimage.Fact(recordType, fields));
        });
  }

  private static List<AttestationField> fields(
      String accountCode, @Nullable AttestationEffectMutation mutation) {
    List<AttestationField> fields = new ArrayList<>();
    if (mutation != null) {
      fields.add(
          AttestationPreimageProjectionFields.present(
              AttestationNumericFieldValue.mutation(mutation.wireValue())));
    }
    fields.add(AttestationPreimageProjectionFields.text(accountCode));
    return fields;
  }

  private static String token(WireValue value) {
    return Objects.requireNonNull(value, "value")
        .wireValue()
        .toLowerCase(java.util.Locale.ROOT)
        .replace('_', '-');
  }

  /** Closed taxonomy-family discriminator vocabulary for account-attestation facts. */
  private enum ClassificationFamily {
    FINANCIAL_POSITION,
    PROFIT_AND_LOSS,
    CASH_FLOW_ASSET;

    String wireToken() {
      return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
  }
}
