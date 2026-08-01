package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PostingId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Produces deterministic canonical posting identifiers from scenario labels in executor tests. */
public final class ScenarioPostingIdentifiers {
  private ScenarioPostingIdentifiers() {}

  public static PostingId fromLabel(String label) {
    return new PostingId(
        UUID.nameUUIDFromBytes(
                ("fingrind-test-postingid:" + label).getBytes(StandardCharsets.UTF_8))
            .toString());
  }
}
