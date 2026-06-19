package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import org.jspecify.annotations.Nullable;

/** One account-state violation item inside the CLI rejected-envelope detail payload. */
public record CliAccountStateViolationPayload(
    String code,
    String field,
    String message,
    String category,
    String repair,
    String accountCode,
    @Nullable String accountNodeKind) {
  /** Validates one account-state violation payload item. */
  public CliAccountStateViolationPayload {
    code = requireText(code, "code");
    field = requireText(field, "field");
    message = requireText(message, "message");
    category = requireText(category, "category");
    repair = requireText(repair, "repair");
    accountCode = requireText(accountCode, "accountCode");
    accountNodeKind = requireOptionalText(accountNodeKind, "accountNodeKind");
  }
}
