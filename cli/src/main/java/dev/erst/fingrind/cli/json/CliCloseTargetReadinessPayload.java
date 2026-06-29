package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One close-target readiness row for initialized-book inspection payloads. */
public record CliCloseTargetReadinessPayload(
    boolean ready,
    String requiredFinancialPositionLineClassification,
    @Nullable String accountCode,
    @Nullable String blockingCode,
    @Nullable String blockingMessage,
    List<String> candidateAccountCodes) {
  public CliCloseTargetReadinessPayload {
    requiredFinancialPositionLineClassification =
        requireText(
            requiredFinancialPositionLineClassification,
            "requiredFinancialPositionLineClassification");
    accountCode = CliJsonModelValidation.requireOptionalText(accountCode, "accountCode");
    blockingCode = CliJsonModelValidation.requireOptionalText(blockingCode, "blockingCode");
    blockingMessage =
        CliJsonModelValidation.requireOptionalText(blockingMessage, "blockingMessage");
    candidateAccountCodes =
        CliJsonModelValidation.copyList(candidateAccountCodes, "candidateAccountCodes");
  }
}
