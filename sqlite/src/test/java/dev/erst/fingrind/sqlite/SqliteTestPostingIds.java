package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PostingId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Creates deterministic canonical posting identifiers for SQLite test fixtures. */
final class SqliteTestPostingIds {
  private static final String LABEL_PREFIX = "fingrind-test-postingid:";

  private SqliteTestPostingIds() {}

  static PostingId fromLabel(String label) {
    return new PostingId(valueForLabel(label));
  }

  static String valueForLabel(String label) {
    return UUID.nameUUIDFromBytes((LABEL_PREFIX + label).getBytes(StandardCharsets.UTF_8))
        .toString();
  }
}
