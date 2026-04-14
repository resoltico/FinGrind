package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.application.BookAdministrationRejection;
import java.nio.file.Path;
import java.util.Objects;

/** Result family for rekeying one FinGrind SQLite book. */
public sealed interface RekeyBookResult permits RekeyBookResult.Rekeyed, RekeyBookResult.Rejected {

  /** Successful rekey outcome for one selected book file. */
  record Rekeyed(Path bookFilePath) implements RekeyBookResult {
    public Rekeyed {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
    }
  }

  /** Deterministic refusal for rekey-book. */
  record Rejected(BookAdministrationRejection rejection) implements RekeyBookResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
