package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Closes runtime-owned resources in a defined order without losing any primary failure. */
final class SqliteRuntimeCloseSequence {
  private SqliteRuntimeCloseSequence() {}

  /**
   * One runtime-owned close action whose failure retains the enclosing sequence's error semantics.
   */
  @FunctionalInterface
  interface CloseAction {
    /** Closes one resource and may report an unchecked cleanup failure. */
    void close();
  }

  static void closeAll(List<? extends CloseAction> closeActions) {
    RuntimeException failure = null;
    for (CloseAction closeAction : List.copyOf(closeActions)) {
      try {
        Objects.requireNonNull(closeAction, "closeAction").close();
      } catch (RuntimeException closeFailure) {
        if (failure == null) {
          failure = closeFailure;
        } else {
          failure.addSuppressed(closeFailure);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  static void closeAllReverse(List<? extends CloseAction> closeActions) {
    List<CloseAction> reverse = new ArrayList<>(List.copyOf(closeActions));
    java.util.Collections.reverse(reverse);
    closeAll(reverse);
  }

  static void closeAllPreservingFailure(
      List<? extends CloseAction> closeActions, Throwable primaryFailure) {
    Throwable checkedPrimaryFailure = Objects.requireNonNull(primaryFailure, "primaryFailure");
    for (CloseAction closeAction : List.copyOf(closeActions)) {
      try {
        Objects.requireNonNull(closeAction, "closeAction").close();
      } catch (RuntimeException | Error closeFailure) {
        checkedPrimaryFailure.addSuppressed(closeFailure);
      }
    }
  }

  static void closeAllReversePreservingFailure(
      List<? extends CloseAction> closeActions, Throwable primaryFailure) {
    List<CloseAction> reverse = new ArrayList<>(List.copyOf(closeActions));
    java.util.Collections.reverse(reverse);
    closeAllPreservingFailure(reverse, primaryFailure);
  }

  static CloseAction coordinationControlCloseAction(
      SqliteCoordinationControlFiles.LockedControlFile controlFile) {
    SqliteCoordinationControlFiles.LockedControlFile checkedControlFile =
        Objects.requireNonNull(controlFile, "controlFile");
    return () -> {
      try {
        checkedControlFile.close();
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Failed to release one FinGrind publication capability witness lock.", exception);
      }
    };
  }
}
