package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

/** Declare-account JSON payload emitted inside administration success envelopes. */
public record CliDeclareAccountPayload(
    String outcome, CliBookQueryJsonModels.DeclaredAccountPayload account)
    implements CliSuccessPayload {
  public CliDeclareAccountPayload {
    outcome = requireText(outcome, "outcome");
    java.util.Objects.requireNonNull(account, "account");
  }
}
