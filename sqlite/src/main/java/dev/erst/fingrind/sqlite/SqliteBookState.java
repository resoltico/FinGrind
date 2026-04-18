package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.OpenBookResult;
import org.jspecify.annotations.Nullable;

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
    OpenBookResult openBookResult(int loadedUserVersion) {
      return new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized());
    }
  },
  FOREIGN_SQLITE {
    @Override
    void requireInitialized(int loadedUserVersion, int expectedUserVersion, String message) {
      throw new IllegalStateException("The selected SQLite file is not a FinGrind book.");
    }

    @Override
    OpenBookResult openBookResult(int loadedUserVersion) {
      return new OpenBookResult.Rejected(new BookAdministrationRejection.BookContainsSchema());
    }
  },
  UNSUPPORTED_FINGRIND_VERSION {
    @Override
    void requireInitialized(int loadedUserVersion, int expectedUserVersion, String message) {
      throw new IllegalStateException(
          "The selected FinGrind book format version "
              + loadedUserVersion
              + " is unsupported. Expected version "
              + expectedUserVersion
              + ".");
    }

    @Override
    OpenBookResult openBookResult(int loadedUserVersion) {
      throw new IllegalStateException(
          "The selected FinGrind book format version "
              + loadedUserVersion
              + " is unsupported. Expected version "
              + expectedUserVersion()
              + ".");
    }
  },
  INCOMPLETE_FINGRIND {
    @Override
    void requireInitialized(int loadedUserVersion, int expectedUserVersion, String message) {
      throw new IllegalStateException(
          "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.");
    }

    @Override
    OpenBookResult openBookResult(int loadedUserVersion) {
      throw new IllegalStateException(
          "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.");
    }
  };

  void requireInitialized(
      int loadedUserVersion, int expectedUserVersion, String notInitializedMessage) {
    // Initialized books satisfy this precondition without further action.
  }

  @Nullable OpenBookResult openBookResult(int loadedUserVersion) {
    return null;
  }

  private static int expectedUserVersion() {
    return SqlitePostingFactStore.BOOK_FORMAT_VERSION;
  }
}
