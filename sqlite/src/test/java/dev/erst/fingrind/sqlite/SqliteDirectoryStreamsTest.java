package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Tests primary-failure preservation for directory traversal ownership. */
class SqliteDirectoryStreamsTest {
  @Test
  void directoryTraversalClosesAfterSuccessfulReading() throws Exception {
    AtomicBoolean closed = new AtomicBoolean();

    String result =
        SqliteDirectoryStreams.read(
            ignored -> directoryStream(List.of(Path.of("/entry")), () -> closed.set(true)),
            Path.of("/directory"),
            entries -> {
              assertEquals(List.of(Path.of("/entry")), entriesToList(entries));
              return "read";
            });

    assertEquals("read", result);
    assertTrue(closed.get());
  }

  @Test
  void closeFailureAfterSuccessfulReadingIsPrimary() {
    IOException closeFailure = new IOException("directory close failure");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                SqliteDirectoryStreams.read(
                    ignored ->
                        directoryStream(
                            List.of(),
                            () -> {
                              throw closeFailure;
                            }),
                    Path.of("/directory"),
                    ignored -> "read"));

    assertSame(closeFailure, failure);
  }

  @Test
  void readingFailureRemainsPrimaryWhenDirectoryCloseAlsoFails() {
    IOException readFailure = new IOException("directory read failure");
    IOException closeFailure = new IOException("directory close failure");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                SqliteDirectoryStreams.read(
                    ignored ->
                        directoryStream(
                            List.of(),
                            () -> {
                              throw closeFailure;
                            }),
                    Path.of("/directory"),
                    ignored -> {
                      throw readFailure;
                    }));

    assertSame(readFailure, failure);
    assertEquals(List.of(closeFailure), List.of(failure.getSuppressed()));
  }

  private static List<Path> entriesToList(DirectoryStream<Path> entries) {
    List<Path> paths = new ArrayList<>();
    for (Path entry : entries) {
      paths.add(entry);
    }
    return List.copyOf(paths);
  }

  private static DirectoryStream<Path> directoryStream(
      List<Path> entries, DirectoryCloseAction closeAction) {
    List<Path> checkedEntries = List.copyOf(entries);
    DirectoryCloseAction checkedCloseAction =
        java.util.Objects.requireNonNull(closeAction, "closeAction");
    return new DirectoryStream<>() {
      @Override
      public Iterator<Path> iterator() {
        return checkedEntries.iterator();
      }

      @Override
      public void close() throws IOException {
        checkedCloseAction.close();
      }
    };
  }

  /** Closes one fixture directory stream after its reader returns or fails. */
  @FunctionalInterface
  private interface DirectoryCloseAction {
    void close() throws IOException;
  }
}
