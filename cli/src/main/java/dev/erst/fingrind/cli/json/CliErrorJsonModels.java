package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;

import java.util.List;

/** Error-detail JSON records emitted by the CLI transport layer. */
public interface CliErrorJsonModels {

  /** Sealed marker for machine-readable CLI failure detail payloads. */
  sealed interface ErrorDetails permits InvalidJsonDetails, InvalidRequestDetails {}

  record InvalidJsonDetails(String parseMessage, int line, int column) implements ErrorDetails {
    public InvalidJsonDetails {
      parseMessage = CliJsonModelValidation.requireText(parseMessage, "parseMessage");
      if (line <= 0) {
        throw new IllegalArgumentException("line must be positive.");
      }
      if (column <= 0) {
        throw new IllegalArgumentException("column must be positive.");
      }
    }
  }

  record InvalidRequestDetails(List<String> violations) implements ErrorDetails {
    public InvalidRequestDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }
}
