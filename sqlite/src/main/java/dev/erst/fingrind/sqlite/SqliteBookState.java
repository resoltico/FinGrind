package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import java.util.Optional;

/** Stable on-disk lifecycle state derived from one selected SQLite book file. */
enum SqliteBookState {
  BLANK_SQLITE {
    @Override
    void requireInitialized(int loadedUserVersion, int expectedUserVersion, String message) {
      throw new IllegalStateException(message);
    }
  },
  INITIALIZED_FINGRIND {
    @Override
    Optional<BookOpeningOutcome> openBookResult(int loadedUserVersion) {
      return Optional.of(
          new BookOpeningOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookAlreadyInitialized()));
    }
  },
  FOREIGN_SQLITE {
    @Override
    void requireInitialized(int loadedUserVersion, int expectedUserVersion, String message) {
      throw new IllegalStateException("The selected SQLite file is not a FinGrind book.");
    }

    @Override
    Optional<BookOpeningOutcome> openBookResult(int loadedUserVersion) {
      return Optional.of(
          new BookOpeningOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookContainsSchema()));
    }
  },
  UNSUPPORTED_FINGRIND_VERSION {
    @Override
    void requireInitialized(int loadedUserVersion, int expectedUserVersion, String message) {
      throw unsupportedBookFormatVersionFailure(loadedUserVersion, expectedUserVersion);
    }

    @Override
    Optional<BookOpeningOutcome> openBookResult(int loadedUserVersion) {
      throw unsupportedBookFormatVersionFailure(loadedUserVersion, expectedUserVersion());
    }
  },
  INCOMPLETE_FINGRIND {
    @Override
    void requireInitialized(int loadedUserVersion, int expectedUserVersion, String message) {
      throw new IllegalStateException(
          "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.");
    }

    @Override
    Optional<BookOpeningOutcome> openBookResult(int loadedUserVersion) {
      throw new IllegalStateException(
          "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.");
    }
  };

  void requireInitialized(
      int loadedUserVersion, int expectedUserVersion, String notInitializedMessage) {
    // Initialized books satisfy this precondition without further action.
  }

  Optional<BookOpeningOutcome> openBookResult(int loadedUserVersion) {
    return Optional.empty();
  }

  private static int expectedUserVersion() {
    return SqliteBookContract.FORMAT_VERSION;
  }

  private static ContractFailureException unsupportedBookFormatVersionFailure(
      int loadedUserVersion, int expectedUserVersion) {
    return new ContractFailureException(
        ContractErrors.unsupportedBookFormatVersionFailure(loadedUserVersion, expectedUserVersion));
  }
}
