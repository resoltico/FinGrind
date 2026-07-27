package dev.erst.fingrind.sqlite;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.function.Function;

/**
 * Test-only SQLite database wrapper that can redirect or fail individual prepared statements.
 *
 * <p>The wrapper delegates every unmodified operation to one real native database handle so tests
 * can drive edge-result shapes without forking production code.
 */
final class SqliteStatementRedirectingDatabase extends SqliteNativeDatabase {
  private final SqliteNativeDatabase delegate;
  private final Function<String, SqliteNativeStatement> prepareOverride;
  private final Runnable closeAction;

  SqliteStatementRedirectingDatabase(
      SqliteNativeDatabase delegate, Function<String, SqliteNativeStatement> prepareOverride) {
    this(delegate, prepareOverride, () -> {});
  }

  SqliteStatementRedirectingDatabase(
      SqliteNativeDatabase delegate,
      Function<String, SqliteNativeStatement> prepareOverride,
      Runnable closeAction) {
    super(
        Objects.requireNonNull(delegate, "delegate").handle(),
        Objects.requireNonNull(delegate, "delegate").sqliteApi());
    this.delegate = delegate;
    this.prepareOverride = Objects.requireNonNull(prepareOverride, "prepareOverride");
    this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
  }

  /**
   * Creates a non-owning statement wrapper whose override receives only a borrowed preparation view
   * of the store-owned database.
   */
  static SqliteStatementRedirectingDatabase borrowing(
      SqliteNativeDatabase delegate, BorrowedStatementRedirector prepareOverride) {
    Objects.requireNonNull(prepareOverride, "prepareOverride");
    BorrowedNativeDatabase borrowedDelegate = new BorrowedNativeDatabase(delegate);
    return new SqliteStatementRedirectingDatabase(
        delegate, sql -> prepareOverride.prepare(borrowedDelegate, sql));
  }

  @Override
  MemorySegment handle() {
    return delegate.handle();
  }

  @Override
  SqliteNativeApi sqliteApi() {
    return delegate.sqliteApi();
  }

  @Override
  SqliteNativeStatement prepare(String sql) {
    return prepareOverride.apply(sql);
  }

  @Override
  void executeStatement(String sql) {
    delegate.executeStatement(sql);
  }

  @Override
  public void close() {
    closeAction.run();
  }

  /** Redirects one prepared statement through the explicitly non-owning delegate view. */
  @FunctionalInterface
  interface BorrowedStatementRedirector {
    SqliteNativeStatement prepare(BorrowedNativeDatabase delegate, String sql);
  }

  /** Non-owning preparation view over the database handle retained by the store lifecycle. */
  static final class BorrowedNativeDatabase {
    private final SqliteNativeDatabase delegate;

    private BorrowedNativeDatabase(SqliteNativeDatabase delegate) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    SqliteNativeStatement prepare(String sql) {
      return delegate.prepare(sql);
    }
  }
}
