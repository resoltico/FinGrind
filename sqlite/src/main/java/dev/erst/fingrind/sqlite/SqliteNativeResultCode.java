package dev.erst.fingrind.sqlite;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Canonical SQLite integer result-code catalog keyed by stable SQLite symbol name. */
final class SqliteNativeResultCode {
  private static final String CODE_SPEC =
      """
      OK 0
      ERROR 1
      INTERNAL 2
      PERM 3
      ABORT 4
      ABORT_ROLLBACK 516
      BUSY 5
      BUSY_RECOVERY 261
      BUSY_SNAPSHOT 517
      BUSY_TIMEOUT 773
      LOCKED 6
      LOCKED_SHAREDCACHE 262
      LOCKED_VTAB 518
      NOMEM 7
      READONLY 8
      READONLY_RECOVERY 264
      READONLY_CANTLOCK 520
      READONLY_ROLLBACK 776
      READONLY_DBMOVED 1032
      READONLY_CANTINIT 1288
      READONLY_DIRECTORY 1544
      INTERRUPT 9
      ROW 100
      DONE 101
      IOERR 10
      IOERR_READ 266
      IOERR_SHORT_READ 522
      IOERR_WRITE 778
      IOERR_FSYNC 1034
      IOERR_DIR_FSYNC 1290
      IOERR_TRUNCATE 1546
      IOERR_FSTAT 1802
      IOERR_UNLOCK 2058
      IOERR_RDLOCK 2314
      IOERR_DELETE 2570
      IOERR_BLOCKED 2826
      IOERR_NOMEM 3082
      IOERR_ACCESS 3338
      IOERR_CHECKRESERVEDLOCK 3594
      IOERR_LOCK 3850
      IOERR_CLOSE 4106
      IOERR_DIR_CLOSE 4362
      IOERR_SHMOPEN 4618
      IOERR_SHMSIZE 4874
      IOERR_SHMLOCK 5130
      IOERR_SHMMAP 5386
      IOERR_SEEK 5642
      IOERR_DELETE_NOENT 5898
      IOERR_MMAP 6154
      IOERR_GETTEMPPATH 6410
      IOERR_CONVPATH 6666
      IOERR_VNODE 6922
      IOERR_AUTH 7178
      IOERR_BEGIN_ATOMIC 7434
      IOERR_COMMIT_ATOMIC 7690
      IOERR_ROLLBACK_ATOMIC 7946
      IOERR_DATA 8202
      IOERR_CORRUPTFS 8458
      IOERR_IN_PAGE 8714
      IOERR_BADKEY 8970
      IOERR_CODEC 9226
      CORRUPT 11
      CORRUPT_VTAB 267
      CORRUPT_SEQUENCE 523
      CORRUPT_INDEX 779
      NOTFOUND 12
      FULL 13
      PROTOCOL 15
      EMPTY 16
      SCHEMA 17
      TOOBIG 18
      MISMATCH 20
      MISUSE 21
      NOLFS 22
      AUTH 23
      FORMAT 24
      RANGE 25
      NOTADB 26
      NOTICE 27
      NOTICE_RECOVER_WAL 283
      NOTICE_RECOVER_ROLLBACK 539
      NOTICE_RBU 795
      WARNING 28
      WARNING_AUTOINDEX 284
      CONSTRAINT 19
      CONSTRAINT_CHECK 275
      CONSTRAINT_COMMITHOOK 531
      CONSTRAINT_FOREIGNKEY 787
      CONSTRAINT_FUNCTION 1043
      CONSTRAINT_NOTNULL 1299
      CONSTRAINT_PRIMARYKEY 1555
      CONSTRAINT_TRIGGER 1811
      CONSTRAINT_UNIQUE 2067
      CONSTRAINT_VTAB 2323
      CONSTRAINT_ROWID 2579
      CONSTRAINT_PINNED 2835
      CONSTRAINT_DATATYPE 3091
      CANTOPEN 14
      CANTOPEN_NOTEMPDIR 270
      CANTOPEN_ISDIR 526
      CANTOPEN_FULLPATH 782
      CANTOPEN_CONVPATH 1038
      CANTOPEN_DIRTYWAL 1294
      CANTOPEN_SYMLINK 1550
      """;
  private static final Pattern FIELD_SEPARATOR = Pattern.compile("\\s+");
  private static final NativeResultCatalog CATALOG = NativeResultCatalog.load();

  private SqliteNativeResultCode() {}

  static int code(String symbol) {
    Integer code = CATALOG.codesBySymbol().get(symbol);
    return Objects.requireNonNull(code, "Unknown SQLite native result-code symbol: " + symbol);
  }

  static String resultName(int resultCode) {
    String symbol = CATALOG.symbolsByCode().get(resultCode);
    return symbol == null ? "SQLITE_" + resultCode : "SQLITE_" + symbol;
  }

  static boolean matchesAny(int resultCode, String... symbols) {
    for (String symbol : symbols) {
      if (code(symbol) == resultCode) {
        return true;
      }
    }
    return false;
  }

  private record NativeResultCatalog(
      Map<String, Integer> codesBySymbol, Map<Integer, String> symbolsByCode) {
    static NativeResultCatalog load() {
      Map<String, Integer> codesBySymbol = new ConcurrentHashMap<>();
      Map<Integer, String> symbolsByCode = new ConcurrentHashMap<>();
      for (String line : CODE_SPEC.strip().lines().toList()) {
        List<String> parts = FIELD_SEPARATOR.splitAsStream(line).toList();
        String symbol = parts.get(0);
        int code = Integer.parseInt(parts.get(1));
        codesBySymbol.put(symbol, code);
        symbolsByCode.put(code, symbol);
      }
      return new NativeResultCatalog(Map.copyOf(codesBySymbol), Map.copyOf(symbolsByCode));
    }
  }
}
