package dev.erst.fingrind.cli;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Derives one exact request-field label from normalized invalid-request messages when possible. */
final class CliRequestFailureArguments {
  private static final List<Pattern> FIELD_PATTERNS =
      List.of(
          Pattern.compile("^Missing required field: (?<field>.+)$"),
          Pattern.compile("^Field must be a string when present: (?<field>.+)$"),
          Pattern.compile("^Field must be a string: (?<field>.+)$"),
          Pattern.compile("^Field must be an integer when present: (?<field>.+)$"),
          Pattern.compile("^Field must be an object: (?<field>.+)$"),
          Pattern.compile("^Field must be an array: (?<field>.+)$"),
          Pattern.compile("^Field is no longer accepted: (?<field>.+)$"),
          Pattern.compile("^Unexpected field: (?<field>.+)$"),
          Pattern.compile("^Unsupported value for (?<field>[^:]+): .+$"),
          Pattern.compile(
              "^Command '.*' requires request field (?<field>[^ ]+) to be '.+', but the request carries '.+'\\.$"));

  private CliRequestFailureArguments() {}

  static @Nullable String extract(String message) {
    for (Pattern pattern : FIELD_PATTERNS) {
      Matcher matcher = pattern.matcher(message);
      if (matcher.matches()) {
        return matcher.group("field");
      }
    }
    return null;
  }
}
