package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.TaxAdministrationStore;
import java.util.Objects;

/** Collaborators required to execute the typed steps of one atomic book workflow. */
public record BookWorkflowExecutionDependencies(
    BookAdministrationStore administrationStore,
    AccountCatalogStore accountCatalogStore,
    BookkeepingReadStore readStore,
    PostingValidationStore validationStore,
    PostingCommitStore commitStore,
    TaxAdministrationStore taxAdministrationStore,
    PostingIdGenerator postingIdGenerator) {
  /** Validates the complete workflow collaborator set. */
  public BookWorkflowExecutionDependencies {
    Objects.requireNonNull(administrationStore, "administrationStore");
    Objects.requireNonNull(accountCatalogStore, "accountCatalogStore");
    Objects.requireNonNull(readStore, "readStore");
    Objects.requireNonNull(validationStore, "validationStore");
    Objects.requireNonNull(commitStore, "commitStore");
    Objects.requireNonNull(taxAdministrationStore, "taxAdministrationStore");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
  }
}
