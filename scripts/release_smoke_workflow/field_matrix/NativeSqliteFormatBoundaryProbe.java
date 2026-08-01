import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Test-only native SQLite3MC probe used by release smoke to mutate one isolated book-format marker.
 *
 * <p>The probe is intentionally outside the product JAR. Distribution assembly compiles it into one
 * private classpath JAR, and release smoke launches that JAR with the exact Java runtime and native
 * SQLite library reported by the archive under test. Field execution therefore never depends on an
 * ambient JDK or compiler.
 */
public final class NativeSqliteFormatBoundaryProbe {
  private static final int SQLITE_DONE = 101;
  private static final int SQLITE_OK = 0;
  private static final int SQLITE_OPEN_READWRITE = 0x0000_0002;
  private static final int SQLITE_ROW = 100;

  private NativeSqliteFormatBoundaryProbe() {}

  public static void main(String[] arguments) {
    try {
      Arguments parsed = Arguments.parse(arguments);
      System.out.println(run(parsed));
    } catch (ProbeFailure exception) {
      System.err.println("error: " + exception.getMessage());
      System.exit(1);
    } catch (RuntimeException exception) {
      System.err.println("error: native SQLite format-boundary probe failed unexpectedly");
      System.exit(1);
    }
  }

  private static int run(Arguments arguments) {
    byte[] key = readNormalizedKey(arguments.keyPath());
    try (Arena arena = Arena.ofConfined()) {
      Sqlite sqlite = Sqlite.load(arguments.libraryPath(), arena);
      MemorySegment database = sqlite.open(arguments.bookPath(), arena);
      try {
        sqlite.applyKey(database, key, arena);
        if (arguments.userVersionToWrite() != null) {
          sqlite.execute(
              database,
              "PRAGMA user_version = " + arguments.userVersionToWrite() + ";",
              arena);
        }
        return sqlite.readUserVersion(database, arena);
      } finally {
        sqlite.close(database);
      }
    } finally {
      Arrays.fill(key, (byte) 0);
    }
  }

  private static byte[] readNormalizedKey(Path keyPath) {
    final byte[] key;
    try {
      key = Files.readAllBytes(keyPath);
    } catch (IOException exception) {
      throw new ProbeFailure("could not read the isolated protected-book key", exception);
    }
    int length = key.length;
    if (length > 0 && key[length - 1] == '\n') {
      length--;
      if (length > 0 && key[length - 1] == '\r') {
        length--;
      }
    }
    if (length == 0) {
      Arrays.fill(key, (byte) 0);
      throw new ProbeFailure("the isolated protected-book key normalized to an empty secret");
    }
    if (length == key.length) {
      return key;
    }
    byte[] normalized = Arrays.copyOf(key, length);
    Arrays.fill(key, (byte) 0);
    return normalized;
  }

  private record Arguments(
      Path libraryPath, Path bookPath, Path keyPath, Integer userVersionToWrite) {
    private static Arguments parse(String[] rawArguments) {
      if (rawArguments.length != 7 && rawArguments.length != 8) {
        throw new ProbeFailure(
            "expected base64-encoded library, book, and key paths followed by "
                + "--read-user-version or --set-user-version <non-negative integer>");
      }
      Path libraryPath = null;
      Path bookPath = null;
      Path keyPath = null;
      Integer userVersionToWrite = null;
      boolean readOnly = false;
      for (int index = 0; index < rawArguments.length; index++) {
        String argument = rawArguments[index];
        switch (argument) {
          case "--library-base64" ->
              libraryPath = encodedPathValue(rawArguments, ++index, argument);
          case "--book-base64" -> bookPath = encodedPathValue(rawArguments, ++index, argument);
          case "--key-base64" -> keyPath = encodedPathValue(rawArguments, ++index, argument);
          case "--read-user-version" -> readOnly = true;
          case "--set-user-version" -> userVersionToWrite = versionValue(rawArguments, ++index);
          default -> throw new ProbeFailure("unsupported native SQLite format-boundary probe argument");
        }
      }
      if (libraryPath == null || bookPath == null || keyPath == null || readOnly == (userVersionToWrite != null)) {
        throw new ProbeFailure("native SQLite format-boundary probe arguments were incomplete or ambiguous");
      }
      if (!libraryPath.isAbsolute() || !Files.isRegularFile(libraryPath)) {
        throw new ProbeFailure("the reported archive SQLite library was not one readable absolute file");
      }
      if (!Files.isRegularFile(bookPath) || !Files.isRegularFile(keyPath)) {
        throw new ProbeFailure("the isolated protected book or its key was not one readable file");
      }
      return new Arguments(libraryPath, bookPath, keyPath, userVersionToWrite);
    }

    private static Path encodedPathValue(String[] arguments, int index, String option) {
      if (index >= arguments.length) {
        throw new ProbeFailure("missing value for " + option);
      }
      try {
        String path =
            new String(Base64.getDecoder().decode(arguments[index]), StandardCharsets.UTF_8);
        return Path.of(path);
      } catch (RuntimeException exception) {
        throw new ProbeFailure("invalid base64-encoded path supplied for " + option, exception);
      }
    }

    private static int versionValue(String[] arguments, int index) {
      if (index >= arguments.length) {
        throw new ProbeFailure("missing value for --set-user-version");
      }
      try {
        int version = Integer.parseInt(arguments[index]);
        if (version < 0) {
          throw new NumberFormatException("negative");
        }
        return version;
      } catch (NumberFormatException exception) {
        throw new ProbeFailure("--set-user-version must be one non-negative integer", exception);
      }
    }
  }

  private static final class Sqlite {
    private final MethodHandle close;
    private final MethodHandle columnInt;
    private final MethodHandle execute;
    private final MethodHandle finalizeStatement;
    private final MethodHandle key;
    private final MethodHandle open;
    private final MethodHandle prepare;
    private final MethodHandle step;

    private Sqlite(
        MethodHandle close,
        MethodHandle columnInt,
        MethodHandle execute,
        MethodHandle finalizeStatement,
        MethodHandle key,
        MethodHandle open,
        MethodHandle prepare,
        MethodHandle step) {
      this.close = close;
      this.columnInt = columnInt;
      this.execute = execute;
      this.finalizeStatement = finalizeStatement;
      this.key = key;
      this.open = open;
      this.prepare = prepare;
      this.step = step;
    }

    private static Sqlite load(Path libraryPath, Arena arena) {
      try {
        SymbolLookup symbols = SymbolLookup.libraryLookup(libraryPath, arena);
        return new Sqlite(
            downcall(symbols, "sqlite3_close_v2", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            downcall(symbols, "sqlite3_column_int", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)),
            downcall(
                symbols,
                "sqlite3_exec",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)),
            downcall(symbols, "sqlite3_finalize", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
            downcall(symbols, "sqlite3_key", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT)),
            downcall(
                symbols,
                "sqlite3_open_v2",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS)),
            downcall(
                symbols,
                "sqlite3_prepare_v2",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS)),
            downcall(symbols, "sqlite3_step", FunctionDescriptor.of(JAVA_INT, ADDRESS)));
      } catch (IllegalArgumentException exception) {
        throw new ProbeFailure("could not load the reported archive SQLite library", exception);
      }
    }

    private static MethodHandle downcall(
        SymbolLookup symbols, String name, FunctionDescriptor descriptor) {
      try {
        return java.lang.foreign.Linker.nativeLinker()
            .downcallHandle(symbols.findOrThrow(name), descriptor);
      } catch (NoSuchElementException | IllegalArgumentException exception) {
        throw new ProbeFailure("the reported archive SQLite library omitted required native support", exception);
      }
    }

    private MemorySegment open(Path bookPath, Arena arena) {
      MemorySegment databaseReference = arena.allocate(ADDRESS);
      int result = invokeInt(open, arena.allocateFrom(bookPath.toString()), databaseReference, SQLITE_OPEN_READWRITE, MemorySegment.NULL);
      MemorySegment database = databaseReference.get(ADDRESS, 0);
      if (result != SQLITE_OK) {
        if (database.address() != 0) {
          close(database);
        }
        throw failure("open the isolated protected book", result);
      }
      return database;
    }

    private void applyKey(MemorySegment database, byte[] keyBytes, Arena arena) {
      MemorySegment keyBuffer = arena.allocate(keyBytes.length);
      ByteBuffer nativeBytes = keyBuffer.asByteBuffer();
      nativeBytes.put(keyBytes);
      try {
        requireSuccess(invokeInt(key, database, keyBuffer, keyBytes.length), "apply the isolated protected-book key");
      } finally {
        keyBuffer.fill((byte) 0);
      }
    }

    private void execute(MemorySegment database, String statement, Arena arena) {
      requireSuccess(
          invokeInt(
              execute,
              database,
              arena.allocateFrom(statement),
              MemorySegment.NULL,
              MemorySegment.NULL,
              MemorySegment.NULL),
          "write the isolated protected-book format marker");
    }

    private int readUserVersion(MemorySegment database, Arena arena) {
      MemorySegment statementReference = arena.allocate(ADDRESS);
      requireSuccess(
          invokeInt(
              prepare,
              database,
              arena.allocateFrom("PRAGMA user_version;"),
              -1,
              statementReference,
              MemorySegment.NULL),
          "prepare the isolated protected-book format query");
      MemorySegment statement = statementReference.get(ADDRESS, 0);
      try {
        requireResult(invokeInt(step, statement), SQLITE_ROW, "read the isolated protected-book format query");
        int value = invokeInt(columnInt, statement, 0);
        requireResult(invokeInt(step, statement), SQLITE_DONE, "finish the isolated protected-book format query");
        return value;
      } finally {
        if (statement.address() != 0) {
          requireSuccess(invokeInt(finalizeStatement, statement), "finalize the isolated protected-book format query");
        }
      }
    }

    private void close(MemorySegment database) {
      requireSuccess(invokeInt(close, database), "close the isolated protected-book connection");
    }

    private static int invokeInt(MethodHandle handle, Object... arguments) {
      try {
        return (int) handle.invokeWithArguments(arguments);
      } catch (Throwable throwable) {
        throw new ProbeFailure("could not invoke the archive SQLite native interface", throwable);
      }
    }

    private static void requireSuccess(int result, String action) {
      requireResult(result, SQLITE_OK, action);
    }

    private static void requireResult(int result, int expected, String action) {
      if (result != expected) {
        throw failure(action, result);
      }
    }

    private static ProbeFailure failure(String action, int result) {
      return new ProbeFailure(action + " through the archive SQLite library (result " + result + ")");
    }
  }

  private static final class ProbeFailure extends RuntimeException {
    private ProbeFailure(String message) {
      super(Objects.requireNonNull(message, "message"));
    }

    private ProbeFailure(String message, Throwable cause) {
      super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }
  }
}
