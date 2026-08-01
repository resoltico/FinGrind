package dev.erst.fingrind.cli.json;

/** Shared sealed root for machine-readable CLI rejection detail payload families. */
public interface CliRejectionJsonModels {
  /** Closed root for detail payloads attached to deterministic business rejections. */
  sealed interface RejectionDetails extends CliEnvelopeJsonModels.EnvelopeDetails
      permits CliPostingRejectionJsonModels.PostingRejectionDetails,
          CliAccountRejectionJsonModels.AccountRejectionDetails,
          CliCloseRejectionJsonModels.CloseWindowRejectionDetails,
          CliQueryPlanRejectionJsonModels.QueryOrPlanRejectionDetails,
          CliTaxRejectionJsonModels.TaxRejectionDetails,
          CliMaintenanceRejectionJsonModels.MaintenanceRejectionDetails,
          CliBookLifecycleRejectionJsonModels.BackupAcknowledgementAuthorizationRejectedDetails,
          CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails {}
}
