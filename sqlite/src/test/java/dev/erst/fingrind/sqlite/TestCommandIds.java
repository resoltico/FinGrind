package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CommandId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Creates deterministic canonical command identifiers for SQLite test fixtures. */
final class TestCommandIds {
  private TestCommandIds() {}

  static CommandId fromLabel(String label) {
    return new CommandId(UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8)).toString());
  }
}
