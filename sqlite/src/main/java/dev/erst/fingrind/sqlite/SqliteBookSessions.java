package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseResolver;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/** Public factory for opening narrow SQLite-backed FinGrind workflow sessions. */
public final class SqliteBookSessions {
  private SqliteBookSessions() {}

  /** Opens one administration session using the default create-if-missing intent. */
  public static SqliteAdministrationSession openAdministration(
      Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return openAdministration(bookPath, bookPassphrase, SqliteBookSessionMode.READ_WRITE_CREATE);
  }

  /** Opens one administration session for the supplied intent. */
  public static SqliteAdministrationSession openAdministration(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return openResolvedAdministration(bookPath, bookPassphrase, sessionMode).requireAccepted();
  }

  /** Opens and primes one administration session for explicit result handling. */
  public static ContractDecision<SqliteAdministrationSession> openResolvedAdministration(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return project(
        openResolvedStore(bookPath, bookPassphrase, sessionMode),
        SqliteCapabilitySessions::administration);
  }

  /**
   * Opens one administration session by resolving one contract-level protected-book access tuple.
   */
  public static SqliteAdministrationSession openAdministration(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedAdministration(bookAccess, sessionMode, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  /** Opens and primes one administration session for explicit result handling. */
  public static ContractDecision<SqliteAdministrationSession> openResolvedAdministration(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return project(
        openResolvedStore(bookAccess, sessionMode, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::administration);
  }

  /** Opens one read session for the supplied passphrase. */
  public static SqliteReadSession openRead(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return openResolvedRead(bookPath, bookPassphrase).requireAccepted();
  }

  /** Opens and primes one read session for explicit result handling. */
  public static ContractDecision<SqliteReadSession> openResolvedRead(
      Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return project(
        openResolvedStore(bookPath, bookPassphrase, SqliteBookSessionMode.READ_ONLY),
        SqliteCapabilitySessions::read);
  }

  /** Opens one read session by resolving one contract-level protected-book access tuple. */
  public static SqliteReadSession openRead(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedRead(bookAccess, passphraseResolver, passphraseIntent).requireAccepted();
  }

  /** Opens and primes one read session for explicit result handling. */
  public static ContractDecision<SqliteReadSession> openResolvedRead(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return project(
        openResolvedStore(
            bookAccess, SqliteBookSessionMode.READ_ONLY, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::read);
  }

  /** Opens one posting session using the default create-if-missing intent. */
  public static SqlitePostingSession openPosting(
      Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return openPosting(bookPath, bookPassphrase, SqliteBookSessionMode.READ_WRITE_CREATE);
  }

  /** Opens one posting session for the supplied intent. */
  public static SqlitePostingSession openPosting(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return openResolvedPosting(bookPath, bookPassphrase, sessionMode).requireAccepted();
  }

  /** Opens and primes one posting session for explicit result handling. */
  public static ContractDecision<SqlitePostingSession> openResolvedPosting(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return project(
        openResolvedStore(bookPath, bookPassphrase, sessionMode),
        SqliteCapabilitySessions::posting);
  }

  /** Opens one posting session by resolving one contract-level protected-book access tuple. */
  public static SqlitePostingSession openPosting(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedPosting(bookAccess, sessionMode, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  /** Opens and primes one posting session for explicit result handling. */
  public static ContractDecision<SqlitePostingSession> openResolvedPosting(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return project(
        openResolvedStore(bookAccess, sessionMode, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::posting);
  }

  /** Opens one period-result-transfer session. */
  public static SqlitePeriodResultTransferSession openPeriodResultTransfer(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedPeriodResultTransfer(bookAccess, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  /** Opens and primes one period-result-transfer session for explicit result handling. */
  public static ContractDecision<SqlitePeriodResultTransferSession>
      openResolvedPeriodResultTransfer(
          BookAccess bookAccess,
          SqlitePassphraseResolver passphraseResolver,
          SqlitePassphraseIntent passphraseIntent) {
    return project(
        openResolvedStore(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            passphraseIntent),
        SqliteCapabilitySessions::periodResultTransfer);
  }

  /** Opens one plan-execution session. */
  public static SqlitePlanExecutionSession openPlanExecution(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedPlanExecution(bookAccess, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  /** Opens and primes one plan-execution session for explicit result handling. */
  public static ContractDecision<SqlitePlanExecutionSession> openResolvedPlanExecution(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return project(
        openResolvedStore(
            bookAccess, SqliteBookSessionMode.PLAN_EXECUTION, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::planExecution);
  }

  /** Opens one rekey session. */
  public static SqliteRekeySession openRekey(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedRekey(bookAccess, passphraseResolver, passphraseIntent).requireAccepted();
  }

  /** Opens and primes one rekey session for explicit result handling. */
  public static ContractDecision<SqliteRekeySession> openResolvedRekey(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return project(
        openResolvedStore(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            passphraseIntent),
        SqliteCapabilitySessions::rekey);
  }

  static SqlitePostingFactStore openStore(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return openStore(bookPath, bookPassphrase, SqliteBookSessionMode.READ_WRITE_CREATE);
  }

  static SqlitePostingFactStore openStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return openResolvedStore(bookPath, bookPassphrase, sessionMode).requireAccepted();
  }

  static ContractDecision<SqlitePostingFactStore> openResolvedStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return SqlitePostingFactStore.openResolved(
            bookPath,
            bookPassphrase,
            toStoreAccessMode(Objects.requireNonNull(sessionMode, "sessionMode")))
        .fold(ContractDecision::accepted, ContractDecision::rejected);
  }

  static SqlitePostingFactStore openStore(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedStore(bookAccess, sessionMode, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  static ContractDecision<SqlitePostingFactStore> openResolvedStore(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    Objects.requireNonNull(passphraseIntent, "passphraseIntent");
    return passphraseResolver
        .resolve(bookAccess, passphraseIntent)
        .fold(
            bookPassphrase ->
                openResolvedStore(bookAccess.bookFilePath(), bookPassphrase, sessionMode),
            ContractDecision::rejected);
  }

  private static <T> ContractDecision<T> project(
      ContractDecision<SqlitePostingFactStore> decision,
      Function<SqlitePostingFactStore, T> projector) {
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(projector, "projector");
    return decision.fold(
        accepted -> ContractDecision.accepted(projector.apply(accepted)),
        ContractDecision::rejected);
  }

  private static SqliteStoreAccessMode toStoreAccessMode(SqliteBookSessionMode sessionMode) {
    return switch (sessionMode) {
      case READ_ONLY -> SqliteStoreAccessMode.READ_ONLY;
      case READ_WRITE_EXISTING -> SqliteStoreAccessMode.READ_WRITE_EXISTING;
      case READ_WRITE_CREATE -> SqliteStoreAccessMode.READ_WRITE_CREATE;
      case PLAN_EXECUTION -> SqliteStoreAccessMode.PLAN_EXECUTION;
    };
  }
}
