package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import org.jspecify.annotations.Nullable;

/** Declare-account JSON payload emitted inside administration success envelopes. */
public record CliDeclareAccountPayload(
    String outcome,
    CliBookQueryJsonModels.DeclaredAccountPayload account,
    @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable AttestationCommitPayload attestationCommit)
    implements CliSuccessPayload {
  public CliDeclareAccountPayload {
    outcome = requireText(outcome, "outcome");
    java.util.Objects.requireNonNull(account, "account");
  }
}
