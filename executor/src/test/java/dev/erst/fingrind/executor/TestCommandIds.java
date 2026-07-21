package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.CommandId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Produces deterministic canonical command identifiers from scenario labels in executor tests. */
public final class TestCommandIds {
  private TestCommandIds() {}

  public static CommandId fromLabel(String label) {
    return new CommandId(UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8)).toString());
  }
}
