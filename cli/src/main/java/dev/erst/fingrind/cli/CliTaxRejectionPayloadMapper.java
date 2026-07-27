package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxDefinitionViolation;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import org.jspecify.annotations.Nullable;

/** Maps tax-context rejections into CLI rejected envelopes. */
final class CliTaxRejectionPayloadMapper {
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);
  private static final String DECLARE_TAX_REGISTRATION_OPERATION =
      ProtocolCatalog.operationName(OperationId.DECLARE_TAX_REGISTRATION);
  private static final String LIST_TAX_REGISTRATIONS_OPERATION =
      ProtocolCatalog.operationName(OperationId.LIST_TAX_REGISTRATIONS);
  private static final String TAX_OBLIGATION_OPERATION =
      ProtocolCatalog.operationName(OperationId.TAX_OBLIGATION);

  private CliTaxRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.Envelope<?> declarationRejectedEnvelope(
      TaxDeclarationRejection rejection) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        TaxDeclarationRejection.wireCode(rejection),
        declarationMessage(rejection),
        declarationHint(rejection),
        null,
        null,
        declarationDetails(rejection),
        null,
        null,
        null,
        null);
  }

  static CliEnvelopeJsonModels.Envelope<?> queryRejectedEnvelope(
      OperationId operationId, TaxQueryRejection rejection) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        TaxQueryRejection.wireCode(rejection),
        queryMessage(rejection),
        queryHint(operationId, rejection),
        null,
        null,
        queryDetails(rejection),
        null,
        null,
        null,
        null);
  }

  private static String declarationMessage(TaxDeclarationRejection rejection) {
    return switch (rejection) {
      case TaxDeclarationRejection.BookNotInitialized _ ->
          "Tax registration declaration refused because the selected book is missing or not initialized.";
      case TaxDeclarationRejection.DefinitionViolations _ ->
          "Tax registration declaration refused because one or more requested tax-definition fields are invalid.";
    };
  }

  private static String declarationHint(TaxDeclarationRejection rejection) {
    return switch (rejection) {
      case TaxDeclarationRejection.BookNotInitialized _ ->
          "Run "
              + OPEN_BOOK_OPERATION
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case TaxDeclarationRejection.DefinitionViolations _ ->
          "Correct the rejected tax-definition fields, or use "
              + DECLARE_TAX_REGISTRATION_OPERATION
              + " request-template help before rerunning the declaration.";
    };
  }

  private static CliRejectionJsonModels.@Nullable RejectionDetails declarationDetails(
      TaxDeclarationRejection rejection) {
    return switch (rejection) {
      case TaxDeclarationRejection.BookNotInitialized _ -> null;
      case TaxDeclarationRejection.DefinitionViolations definitionViolations ->
          new CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails(
              definitionViolations.violations().stream()
                  .map(CliTaxRejectionPayloadMapper::definitionViolationDetails)
                  .toList());
    };
  }

  private static String queryMessage(TaxQueryRejection rejection) {
    return switch (rejection) {
      case TaxQueryRejection.BookNotInitialized _ ->
          "Tax query refused because the selected book is missing or not initialized.";
      case TaxQueryRejection.UnknownTaxRegistration _ ->
          "Tax query refused because the selected tax registration is not declared in this book.";
      case TaxQueryRejection.ObligationPeriodMismatch _ ->
          "Tax obligation query refused because the requested period does not match the declared filing cadence.";
    };
  }

  private static String queryHint(OperationId operationId, TaxQueryRejection rejection) {
    return switch (rejection) {
      case TaxQueryRejection.BookNotInitialized _ ->
          "Run "
              + OPEN_BOOK_OPERATION
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case TaxQueryRejection.UnknownTaxRegistration _ ->
          "Use "
              + LIST_TAX_REGISTRATIONS_OPERATION
              + " to confirm the taxRegistrationId, or declare the missing tax registration before rerunning "
              + operationName(operationId)
              + ".";
      case TaxQueryRejection.ObligationPeriodMismatch mismatch ->
          "Rerun "
              + TAX_OBLIGATION_OPERATION
              + " with one full "
              + mismatch.obligationFrequency().wireValue().toLowerCase(java.util.Locale.ROOT)
              + " period that matches the declared filing cadence.";
    };
  }

  private static CliRejectionJsonModels.@Nullable RejectionDetails queryDetails(
      TaxQueryRejection rejection) {
    return switch (rejection) {
      case TaxQueryRejection.BookNotInitialized _ -> null;
      case TaxQueryRejection.UnknownTaxRegistration unknownTaxRegistration ->
          new CliTaxRejectionJsonModels.UnknownTaxRegistrationDetails(
              unknownTaxRegistration.taxRegistrationId().value());
      case TaxQueryRejection.ObligationPeriodMismatch mismatch ->
          new CliTaxRejectionJsonModels.ObligationPeriodMismatchDetails(
              mismatch.obligationFrequency().wireValue(),
              mismatch.effectiveDateFrom().toString(),
              mismatch.effectiveDateTo().toString());
    };
  }

  private static CliTaxRejectionJsonModels.TaxDefinitionViolationDetails definitionViolationDetails(
      TaxDefinitionViolation violation) {
    return new CliTaxRejectionJsonModels.TaxDefinitionViolationDetails(
        violation.code(), violation.field(), violation.message());
  }

  private static String operationName(OperationId operationId) {
    return ProtocolCatalog.operationName(operationId);
  }
}
