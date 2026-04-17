package dev.erst.fingrind.contract;

import java.nio.file.Path;
import java.util.Objects;

/** Result family for rekeying one FinGrind book. */
public sealed interface RekeyBookResult permits RekeyBookResult.Rekeyed, RekeyBookResult.Rejected {

  /** Successful rekey outcome for one selected book file. */
  record Rekeyed(Path bookFilePath) implements RekeyBookResult {
    /** Validates the selected book path. */
    public Rekeyed {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
    }
  }

  /** Deterministic refusal for rekey-book. */
  record Rejected(BookAdministrationRejection rejection) implements RekeyBookResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
