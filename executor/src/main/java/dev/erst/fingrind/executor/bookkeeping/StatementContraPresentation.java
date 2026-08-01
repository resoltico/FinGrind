package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;
import java.util.Optional;

/** Formats the explicit account-taxonomy relationship for financial statement readers. */
final class StatementContraPresentation {
  private StatementContraPresentation() {}

  static String lineName(String lineName, Optional<String> contraOfLineCode) {
    Objects.requireNonNull(lineName, "lineName");
    Objects.requireNonNull(contraOfLineCode, "contraOfLineCode");
    return contraOfLineCode
        .map(parentCode -> "Less: %s (reduces %s)".formatted(lineName, parentCode))
        .orElse(lineName);
  }
}
