package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import org.jspecify.annotations.Nullable;

/** One entry-semantics violation item inside the CLI rejected-envelope detail payload. */
public record CliEntrySemanticsViolationPayload(
    String code, @Nullable String field, String message, String category, String repair) {
  /** Validates one entry-semantics violation payload item. */
  public CliEntrySemanticsViolationPayload {
    code = requireText(code, "code");
    field = requireOptionalText(field, "field");
    message = requireText(message, "message");
    category = requireText(category, "category");
    repair = requireText(repair, "repair");
  }
}
