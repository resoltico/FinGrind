package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookInspectionJsonModels;
import dev.erst.fingrind.cli.json.CliCloseTargetReadinessPayload;
import dev.erst.fingrind.cli.json.CliSuccessPayload;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.runtime.BookMigrationPolicy;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import java.nio.file.Path;

/** Maps book-inspection and identity payloads into CLI JSON models. */
final class CliBookInspectionPayloadMapper {
  private CliBookInspectionPayloadMapper() {}

  static CliSuccessPayload bookInspectionPayload(Path bookFilePath, BookInspection inspection) {
    BookInspection.Status status = inspection.status();
    return switch (inspection) {
      case BookInspection.Missing missing ->
          new CliBookInspectionJsonModels.MissingBookInspectionPayload(
              absolutePath(bookFilePath),
              status.wireValue(),
              status.compatibleWithCurrentBinary(),
              status.canInitializeWithOpenBook(),
              missing.supportedBookFormatVersion(),
              migrationPolicyPayload(missing.migrationPolicy()));
      case BookInspection.Existing existing ->
          new CliBookInspectionJsonModels.ExistingBookInspectionPayload(
              absolutePath(bookFilePath),
              status.wireValue(),
              status.compatibleWithCurrentBinary(),
              status.canInitializeWithOpenBook(),
              existing.applicationId(),
              existing.detectedBookFormatVersion(),
              existing.supportedBookFormatVersion(),
              migrationPolicyPayload(existing.migrationPolicy()));
      case BookInspection.Initialized initialized ->
          new CliBookInspectionJsonModels.InitializedBookInspectionPayload(
              absolutePath(bookFilePath),
              status.wireValue(),
              status.compatibleWithCurrentBinary(),
              status.canInitializeWithOpenBook(),
              initialized.applicationId(),
              initialized.detectedBookFormatVersion(),
              initialized.supportedBookFormatVersion(),
              migrationPolicyPayload(initialized.migrationPolicy()),
              initialized.initializedAt().toString(),
              bookIdentityPayload(initialized.bookIdentity()),
              closeReadinessPayload(initialized.closeReadiness()));
    };
  }

  static CliBookInspectionJsonModels.MigrationPolicyPayload migrationPolicyPayload(
      BookMigrationPolicy migrationPolicy) {
    return new CliBookInspectionJsonModels.MigrationPolicyPayload(
        migrationPolicy.mode().wireValue(),
        migrationPolicy.inPlaceUpgradeSupported(),
        migrationPolicy.olderFormatsAccepted(),
        migrationPolicy.newerFormatsAccepted(),
        migrationPolicy.supportedBookFormatVersion());
  }

  static CliBookInspectionJsonModels.BookIdentityPayload bookIdentityPayload(
      BookIdentity bookIdentity) {
    return new CliBookInspectionJsonModels.BookIdentityPayload(
        bookIdentity.entityName().value(),
        bookIdentity.bookDoctrine().accountingKernelProfileId().value(),
        bookIdentity.bookDoctrine().accountingBasis().wireValue(),
        bookIdentity.bookDoctrine().accountingFrameworkPosition().wireValue(),
        bookIdentity.bookDoctrine().entityForm().wireValue(),
        bookIdentity.bookDoctrine().bookTemplateId().wireValue(),
        bookIdentity.bookDoctrine().inventoryCostingDoctrine() == null
            ? null
            : bookIdentity.bookDoctrine().inventoryCostingDoctrine().wireValue(),
        bookIdentity.functionalCurrency().code(),
        bookIdentity.fiscalYearStart().wireValue(),
        bookIdentity.bookStartEffectiveDate().toString());
  }

  static CliBookInspectionJsonModels.CloseReadinessPayload closeReadinessPayload(
      BookInspection.CloseReadiness closeReadiness) {
    return new CliBookInspectionJsonModels.CloseReadinessPayload(
        closeTargetReadinessPayload(closeReadiness.interimResultTarget()),
        closeTargetReadinessPayload(closeReadiness.retainedAccumulatedTarget()));
  }

  static CliCloseTargetReadinessPayload closeTargetReadinessPayload(
      BookInspection.CloseTargetReadiness closeTargetReadiness) {
    return new CliCloseTargetReadinessPayload(
        closeTargetReadiness.ready(),
        closeTargetReadiness.requiredFinancialPositionLineClassification().wireValue(),
        closeTargetReadiness.accountCode() == null
            ? null
            : closeTargetReadiness.accountCode().value(),
        closeTargetReadiness.blockingCode(),
        closeTargetReadiness.blockingMessage(),
        closeTargetReadiness.candidateAccountCodes().stream().map(AccountCode::value).toList());
  }

  private static String absolutePath(Path bookFilePath) {
    return CliPublicPaths.absoluteValue(bookFilePath);
  }
}
