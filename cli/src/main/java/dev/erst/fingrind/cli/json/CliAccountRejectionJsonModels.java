package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Account-registry rejection details emitted by the CLI transport layer. */
public interface CliAccountRejectionJsonModels {
  /** Sealed category for account-registry rejection payloads. */
  sealed interface AccountRejectionDetails extends CliRejectionJsonModels.RejectionDetails
      permits AccountTypeConflictDetails,
          AccountTaxonomyConflictDetails,
          ContraAccountDetails,
          AccountCodeDetails,
          AccountDependenciesDetails,
          ParentAccountDetails,
          ParentAccountTypeConflictDetails,
          ParentAccountNodeKindDetails,
          ParentAccountTaxonomyConflictDetails,
          ReservedResultClassificationDetails,
          CloseTargetAccountCandidateMissingDetails,
          CloseTargetAccountCandidateAmbiguousDetails {}

  record AccountTypeConflictDetails(
      String accountCode, String existingAccountType, String requestedAccountType)
      implements AccountRejectionDetails {
    public AccountTypeConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingAccountType = requireText(existingAccountType, "existingAccountType");
      requestedAccountType = requireText(requestedAccountType, "requestedAccountType");
    }
  }

  record AccountTaxonomyConflictDetails(
      String accountCode,
      AccountTaxonomyDetails existingAccountTaxonomy,
      AccountTaxonomyDetails requestedAccountTaxonomy)
      implements AccountRejectionDetails {
    public AccountTaxonomyConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingAccountTaxonomy = requireValue(existingAccountTaxonomy, "existingAccountTaxonomy");
      requestedAccountTaxonomy = requireValue(requestedAccountTaxonomy, "requestedAccountTaxonomy");
    }
  }

  record ContraAccountDetails(String accountCode, String contraOfAccountCode, String violation)
      implements AccountRejectionDetails {
    public ContraAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
      contraOfAccountCode = requireText(contraOfAccountCode, "contraOfAccountCode");
      violation = requireText(violation, "violation");
    }
  }

  record AccountCodeDetails(String accountCode) implements AccountRejectionDetails {
    public AccountCodeDetails {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record AccountDependenciesDetails(String accountCode, List<String> dependencies)
      implements AccountRejectionDetails {
    public AccountDependenciesDetails {
      accountCode = requireText(accountCode, "accountCode");
      dependencies = copyList(dependencies, "dependencies");
      if (dependencies.isEmpty()) {
        throw new IllegalArgumentException("dependencies must not be empty.");
      }
    }
  }

  record ParentAccountDetails(String accountCode, String parentAccountCode)
      implements AccountRejectionDetails {
    public ParentAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
      parentAccountCode = requireText(parentAccountCode, "parentAccountCode");
    }
  }

  record ParentAccountTypeConflictDetails(
      String accountCode,
      String requestedAccountType,
      String parentAccountCode,
      String parentAccountType)
      implements AccountRejectionDetails {
    public ParentAccountTypeConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      requestedAccountType = requireText(requestedAccountType, "requestedAccountType");
      parentAccountCode = requireText(parentAccountCode, "parentAccountCode");
      parentAccountType = requireText(parentAccountType, "parentAccountType");
    }
  }

  record ParentAccountNodeKindDetails(
      String accountCode, String parentAccountCode, String parentAccountNodeKind)
      implements AccountRejectionDetails {
    public ParentAccountNodeKindDetails {
      accountCode = requireText(accountCode, "accountCode");
      parentAccountCode = requireText(parentAccountCode, "parentAccountCode");
      parentAccountNodeKind = requireText(parentAccountNodeKind, "parentAccountNodeKind");
    }
  }

  record ParentAccountTaxonomyConflictDetails(
      String accountCode,
      AccountTaxonomyDetails requestedAccountTaxonomy,
      String parentAccountCode,
      AccountTaxonomyDetails parentAccountTaxonomy)
      implements AccountRejectionDetails {
    public ParentAccountTaxonomyConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      requestedAccountTaxonomy = requireValue(requestedAccountTaxonomy, "requestedAccountTaxonomy");
      parentAccountCode = requireText(parentAccountCode, "parentAccountCode");
      parentAccountTaxonomy = requireValue(parentAccountTaxonomy, "parentAccountTaxonomy");
    }
  }

  record AccountTaxonomyDetails(
      String accountNodeKind,
      @Nullable String parentAccountCode,
      @Nullable String financialPositionLineClassification,
      @Nullable String profitAndLossLineClassification) {
    public AccountTaxonomyDetails {
      accountNodeKind = requireText(accountNodeKind, "accountNodeKind");
      parentAccountCode = requireOptionalText(parentAccountCode, "parentAccountCode");
      financialPositionLineClassification =
          requireOptionalText(
              financialPositionLineClassification, "financialPositionLineClassification");
      profitAndLossLineClassification =
          requireOptionalText(profitAndLossLineClassification, "profitAndLossLineClassification");
    }
  }

  record ReservedResultClassificationDetails(
      String accountCode, String financialPositionLineClassification)
      implements AccountRejectionDetails {
    public ReservedResultClassificationDetails {
      accountCode = requireText(accountCode, "accountCode");
      financialPositionLineClassification =
          requireText(financialPositionLineClassification, "financialPositionLineClassification");
    }
  }

  record CloseTargetAccountCandidateMissingDetails(
      String requiredFinancialPositionLineClassification,
      List<String> inactiveCandidateAccountCodes)
      implements AccountRejectionDetails {
    public CloseTargetAccountCandidateMissingDetails {
      requiredFinancialPositionLineClassification =
          requireText(
              requiredFinancialPositionLineClassification,
              "requiredFinancialPositionLineClassification");
      inactiveCandidateAccountCodes =
          copyList(inactiveCandidateAccountCodes, "inactiveCandidateAccountCodes");
    }
  }

  record CloseTargetAccountCandidateAmbiguousDetails(
      String requiredFinancialPositionLineClassification, List<String> candidateAccountCodes)
      implements AccountRejectionDetails {
    public CloseTargetAccountCandidateAmbiguousDetails {
      requiredFinancialPositionLineClassification =
          requireText(
              requiredFinancialPositionLineClassification,
              "requiredFinancialPositionLineClassification");
      candidateAccountCodes = copyList(candidateAccountCodes, "candidateAccountCodes");
    }
  }
}
