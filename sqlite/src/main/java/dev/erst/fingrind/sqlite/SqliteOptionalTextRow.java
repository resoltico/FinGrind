package dev.erst.fingrind.sqlite;

import java.util.Objects;
import java.util.Optional;

/** Single-column text row shape that distinguishes empty, exact-one-row, and multi-row cases. */
record SqliteOptionalTextRow(Optional<String> value, boolean singleRow) {
  SqliteOptionalTextRow {
    Objects.requireNonNull(value, "value");
  }
}
