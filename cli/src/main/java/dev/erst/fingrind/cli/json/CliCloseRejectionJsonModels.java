package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

/** Period-close rejection details emitted by the CLI transport layer. */
public interface CliCloseRejectionJsonModels {
  /** Sealed category for close-window rejection payloads. */
  sealed interface CloseWindowRejectionDetails extends CliRejectionJsonModels.RejectionDetails
      permits InterimResultSweepStartDetails,
          InterimResultSweepFutureDateDetails,
          InterimResultSweepFiscalYearDetails,
          FiscalYearCloseStartDetails,
          FiscalYearCloseEndDetails,
          FiscalYearCloseTransferredThroughDetails,
          FiscalYearCloseFutureDateDetails {}

  record InterimResultSweepStartDetails(String requiredEffectiveDateFrom)
      implements CloseWindowRejectionDetails {
    public InterimResultSweepStartDetails {
      requiredEffectiveDateFrom =
          requireText(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  record InterimResultSweepFutureDateDetails(String attemptedEffectiveDateTo)
      implements CloseWindowRejectionDetails {
    public InterimResultSweepFutureDateDetails {
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  record InterimResultSweepFiscalYearDetails(
      String attemptedEffectiveDateFrom, String attemptedEffectiveDateTo, String fiscalYearStart)
      implements CloseWindowRejectionDetails {
    public InterimResultSweepFiscalYearDetails {
      attemptedEffectiveDateFrom =
          requireText(attemptedEffectiveDateFrom, "attemptedEffectiveDateFrom");
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      fiscalYearStart = requireText(fiscalYearStart, "fiscalYearStart");
    }
  }

  record FiscalYearCloseStartDetails(String requiredEffectiveDateFrom)
      implements CloseWindowRejectionDetails {
    public FiscalYearCloseStartDetails {
      requiredEffectiveDateFrom =
          requireText(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  record FiscalYearCloseEndDetails(String requiredEffectiveDateTo)
      implements CloseWindowRejectionDetails {
    public FiscalYearCloseEndDetails {
      requiredEffectiveDateTo = requireText(requiredEffectiveDateTo, "requiredEffectiveDateTo");
    }
  }

  record FiscalYearCloseTransferredThroughDetails(
      String attemptedEffectiveDateTo, String transferredThroughEffectiveDate)
      implements CloseWindowRejectionDetails {
    public FiscalYearCloseTransferredThroughDetails {
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      transferredThroughEffectiveDate =
          requireText(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    }
  }

  record FiscalYearCloseFutureDateDetails(String attemptedEffectiveDateTo)
      implements CloseWindowRejectionDetails {
    public FiscalYearCloseFutureDateDetails {
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }
}
