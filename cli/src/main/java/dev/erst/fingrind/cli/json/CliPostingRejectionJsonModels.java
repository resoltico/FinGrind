package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;

/** Posting-admission rejection details emitted by the CLI transport layer. */
public interface CliPostingRejectionJsonModels {
  /** Sealed category for posting lifecycle rejection payloads. */
  sealed interface PostingRejectionDetails extends CliRejectionJsonModels.RejectionDetails
      permits AccountStateViolationsDetails,
          EntrySemanticsViolationsDetails,
          PriorPostingDetails,
          PostingEffectiveDateBeforeBookStartDetails,
          PostingEffectiveDateInFutureDetails,
          FunctionalCurrencyMismatchDetails,
          OpeningPositionWindowClosedDetails,
          OpeningPositionNominalAccountDetails,
          SweptInterimResultViolationDetails {}

  record AccountStateViolationsDetails(List<CliAccountStateViolationPayload> violations)
      implements PostingRejectionDetails {
    public AccountStateViolationsDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }

  record EntrySemanticsViolationsDetails(List<CliEntrySemanticsViolationPayload> violations)
      implements PostingRejectionDetails {
    public EntrySemanticsViolationsDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }

  record PriorPostingDetails(String priorPostingId) implements PostingRejectionDetails {
    public PriorPostingDetails {
      priorPostingId = requireText(priorPostingId, "priorPostingId");
    }
  }

  record FunctionalCurrencyMismatchDetails(String functionalCurrency, String attemptedCurrency)
      implements PostingRejectionDetails {
    public FunctionalCurrencyMismatchDetails {
      functionalCurrency = requireText(functionalCurrency, "functionalCurrency");
      attemptedCurrency = requireText(attemptedCurrency, "attemptedCurrency");
    }
  }

  record OpeningPositionNominalAccountDetails(String accountCode, String accountType)
      implements PostingRejectionDetails {
    public OpeningPositionNominalAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
      accountType = requireText(accountType, "accountType");
    }
  }

  record OpeningPositionWindowClosedDetails(
      String firstBlockingPostingKind, String firstBlockingEffectiveDate)
      implements PostingRejectionDetails {
    public OpeningPositionWindowClosedDetails {
      firstBlockingPostingKind = requireText(firstBlockingPostingKind, "firstBlockingPostingKind");
      firstBlockingEffectiveDate =
          requireText(firstBlockingEffectiveDate, "firstBlockingEffectiveDate");
    }
  }

  record SweptInterimResultViolationDetails(
      String transferredThroughEffectiveDate, String attemptedEffectiveDate)
      implements PostingRejectionDetails {
    public SweptInterimResultViolationDetails {
      transferredThroughEffectiveDate =
          requireText(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
      attemptedEffectiveDate = requireText(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  record PostingEffectiveDateInFutureDetails(String attemptedEffectiveDate, String currentUtcDate)
      implements PostingRejectionDetails {
    public PostingEffectiveDateInFutureDetails {
      attemptedEffectiveDate = requireText(attemptedEffectiveDate, "attemptedEffectiveDate");
      currentUtcDate = requireText(currentUtcDate, "currentUtcDate");
    }
  }

  record PostingEffectiveDateBeforeBookStartDetails(
      String attemptedEffectiveDate, String bookStartEffectiveDate)
      implements PostingRejectionDetails {
    public PostingEffectiveDateBeforeBookStartDetails {
      attemptedEffectiveDate = requireText(attemptedEffectiveDate, "attemptedEffectiveDate");
      bookStartEffectiveDate = requireText(bookStartEffectiveDate, "bookStartEffectiveDate");
    }
  }
}
