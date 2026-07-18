package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.ContraAccountInvalid;
import dev.erst.fingrind.core.AccountTaxonomy;
import org.jspecify.annotations.Nullable;

/** Projects declaration-time account rejections into their machine detail payloads. */
final class CliDeclaredAccountRejectionDetails {
  private CliDeclaredAccountRejectionDetails() {}

  static CliRejectionJsonModels.@Nullable RejectionDetails details(
      BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.AccountTypeConflict conflict ->
          new CliRejectionJsonModels.AccountTypeConflictDetails(
              conflict.accountCode().value(),
              conflict.existingAccountType().wireValue(),
              conflict.requestedAccountType().wireValue());
      case BookAdministrationRejection.AccountTaxonomyConflict conflict ->
          new CliRejectionJsonModels.AccountTaxonomyConflictDetails(
              conflict.accountCode().value(),
              taxonomyDetails(conflict.existingAccountTaxonomy()),
              taxonomyDetails(conflict.requestedAccountTaxonomy()));
      case ContraAccountInvalid conflict ->
          new CliRejectionJsonModels.ContraAccountDetails(
              conflict.accountCode().value(),
              conflict.contraOfAccountCode().value(),
              conflict.violation().wireValue());
      case BookAdministrationRejection.ParentAccountMissing conflict ->
          new CliRejectionJsonModels.ParentAccountDetails(
              conflict.accountCode().value(), conflict.parentAccountCode().value());
      case BookAdministrationRejection.ParentAccountInactive conflict ->
          new CliRejectionJsonModels.ParentAccountDetails(
              conflict.accountCode().value(), conflict.parentAccountCode().value());
      case BookAdministrationRejection.ParentAccountTypeConflict conflict ->
          new CliRejectionJsonModels.ParentAccountTypeConflictDetails(
              conflict.accountCode().value(),
              conflict.requestedAccountType().wireValue(),
              conflict.parentAccountCode().value(),
              conflict.parentAccountType().wireValue());
      case BookAdministrationRejection.ParentAccountNotHeader conflict ->
          new CliRejectionJsonModels.ParentAccountNodeKindDetails(
              conflict.accountCode().value(),
              conflict.parentAccountCode().value(),
              conflict.parentAccountNodeKind().wireValue());
      case BookAdministrationRejection.ParentAccountTaxonomyConflict conflict ->
          new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
              conflict.accountCode().value(),
              taxonomyDetails(conflict.requestedAccountTaxonomy()),
              conflict.parentAccountCode().value(),
              taxonomyDetails(conflict.parentAccountTaxonomy()));
      case BookAdministrationRejection.AccountHierarchyCycle conflict ->
          new CliRejectionJsonModels.ParentAccountDetails(
              conflict.accountCode().value(), conflict.parentAccountCode().value());
      default -> null;
    };
  }

  private static CliRejectionJsonModels.AccountTaxonomyDetails taxonomyDetails(
      AccountTaxonomy accountTaxonomy) {
    return new CliRejectionJsonModels.AccountTaxonomyDetails(
        accountTaxonomy.nodeKind().wireValue(),
        accountTaxonomy.parentAccountCode().map(accountCode -> accountCode.value()).orElse(null),
        accountTaxonomy
            .financialPositionLineClassification()
            .map(classification -> classification.wireValue())
            .orElse(null),
        accountTaxonomy
            .profitAndLossLineClassification()
            .map(classification -> classification.wireValue())
            .orElse(null));
  }
}
