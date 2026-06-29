package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PostingId;

/** Loads journal lines and evidence for one posting row while materializing SQLite results. */
@FunctionalInterface
interface SqlitePostingAttachmentLoader {
  /** Returns the journal lines and evidence that belong to the supplied posting. */
  SqlitePostingAttachments load(PostingId postingId);
}
