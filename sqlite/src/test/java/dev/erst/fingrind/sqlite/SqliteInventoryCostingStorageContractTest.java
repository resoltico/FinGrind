package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Storage-contract coverage for the durable inventory movement ledger and on-hand state. */
class SqliteInventoryCostingStorageContractTest extends SqliteInventoryCostingFixtureSupport {
  @Test
  void inventoryLedger_assignsPerAccountSequencesAndVerifiesPersistedOnHandReplay() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-sequencing.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertCanonicalInitializedBookMetadata(database);
          insertInventoryAccount(database, "1400", "Inventory");
          insertInventoryAccount(database, "1410", "Spare Inventory");
          insertPostingFactRow(database, "posting-1", "idem-1");
          insertPostingFactRow(
              database,
              "posting-2",
              "idem-2",
              "2026-04-08",
              "2026-04-08T10:15:30Z",
              "STANDARD",
              PostingOriginKind.SALE_SETTLED);
          insertPostingFactRow(database, "posting-3", "idem-3");

          int firstSequence =
              SqliteInventoryCostingWriter.insertInventoryMovement(
                  database,
                  "movement-1",
                  new AccountCode("1400"),
                  LocalDate.parse("2026-04-07"),
                  InventoryMovementKind.ACQUISITION,
                  10L,
                  1_000L,
                  new PostingId("posting-1"));
          int secondSequence =
              SqliteInventoryCostingWriter.insertInventoryMovement(
                  database,
                  "movement-2",
                  new AccountCode("1400"),
                  LocalDate.parse("2026-04-08"),
                  InventoryMovementKind.DISPOSAL,
                  -4L,
                  -400L,
                  new PostingId("posting-2"));
          int thirdSequence =
              SqliteInventoryCostingWriter.insertInventoryMovement(
                  database,
                  "movement-3",
                  new AccountCode("1410"),
                  LocalDate.parse("2026-04-07"),
                  InventoryMovementKind.ACQUISITION,
                  2L,
                  300L,
                  new PostingId("posting-3"));

          assertEquals(1, firstSequence);
          assertEquals(2, secondSequence);
          assertEquals(1, thirdSequence);
          assertEquals(
              2,
              queryInt(
                  database,
                  """
                  select count(distinct account_sequence)
                  from inventory_movement
                  where inventory_account = '1400'
                  """));

          SqliteInventoryCostingWriter.upsertInventoryOnHand(
              database, new AccountCode("1400"), 6L, 600L, LocalDate.parse("2026-04-08"));
          SqliteInventoryCostingWriter.upsertInventoryOnHand(
              database, new AccountCode("1410"), 2L, 300L, LocalDate.parse("2026-04-07"));

          assertTrue(SqliteBookIntegrityVerifier.hasConsistentInventoryOnHand(database));
        });
  }

  @Test
  void inventoryLedger_allowsOpeningOnlyForAnOpeningPositionPosting() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-opening-origin.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertCanonicalInitializedBookMetadata(database);
          insertInventoryAccount(database, "1400", "Inventory");
          insertInventoryAccount(database, "1410", "Spare Inventory");
          insertPostingFactRow(
              database,
              "posting-opening",
              "idem-opening",
              "2026-04-07",
              "2026-04-07T10:15:30Z",
              "OPENING_BALANCE",
              PostingOriginKind.OPENING_POSITION);
          insertPostingFactRow(
              database,
              "posting-standard",
              "idem-standard",
              "2026-04-07",
              "2026-04-07T10:15:31Z",
              "STANDARD",
              PostingOriginKind.DIRECT_JOURNAL);

          assertEquals(
              1,
              SqliteInventoryCostingWriter.insertInventoryMovement(
                  database,
                  "movement-opening",
                  new AccountCode("1400"),
                  LocalDate.parse("2026-04-07"),
                  InventoryMovementKind.OPENING,
                  2L,
                  300L,
                  new PostingId("posting-opening")));

          SqliteNativeException wrongOrigin =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      SqliteInventoryCostingWriter.insertInventoryMovement(
                          database,
                          "movement-wrong-origin",
                          new AccountCode("1410"),
                          LocalDate.parse("2026-04-07"),
                          InventoryMovementKind.OPENING,
                          2L,
                          300L,
                          new PostingId("posting-standard")));
          assertEquals(SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), wrongOrigin.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", wrongOrigin.resultName());
        });
  }

  @Test
  void inventoryLedger_allowsEveryMatchingTypedPostingOriginAndRejectsRawPostingOrigins() {
    List<InventoryOriginCase> cases =
        List.of(
            new InventoryOriginCase(
                InventoryMovementKind.OPENING,
                "OPENING_BALANCE",
                PostingOriginKind.OPENING_POSITION,
                1L,
                100L),
            new InventoryOriginCase(
                InventoryMovementKind.ACQUISITION,
                "STANDARD",
                PostingOriginKind.PURCHASE_SETTLED,
                1L,
                100L),
            new InventoryOriginCase(
                InventoryMovementKind.ACQUISITION,
                "STANDARD",
                PostingOriginKind.PURCHASE_ON_CREDIT,
                1L,
                100L),
            new InventoryOriginCase(
                InventoryMovementKind.CAPITALIZATION,
                "STANDARD",
                PostingOriginKind.INVENTORY_CAPITALIZATION_SETTLED,
                0L,
                100L),
            new InventoryOriginCase(
                InventoryMovementKind.CAPITALIZATION,
                "STANDARD",
                PostingOriginKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
                0L,
                100L),
            new InventoryOriginCase(
                InventoryMovementKind.COUNT_INCREASE,
                "STANDARD",
                PostingOriginKind.INVENTORY_COUNT_INCREASE,
                1L,
                100L),
            new InventoryOriginCase(
                InventoryMovementKind.DISPOSAL,
                "STANDARD",
                PostingOriginKind.SALE_SETTLED,
                -1L,
                -100L),
            new InventoryOriginCase(
                InventoryMovementKind.DISPOSAL,
                "STANDARD",
                PostingOriginKind.SALE_ON_CREDIT,
                -1L,
                -100L),
            new InventoryOriginCase(
                InventoryMovementKind.WRITE_DOWN,
                "STANDARD",
                PostingOriginKind.INVENTORY_WRITE_DOWN,
                0L,
                -100L),
            new InventoryOriginCase(
                InventoryMovementKind.SHRINKAGE,
                "STANDARD",
                PostingOriginKind.INVENTORY_SHRINKAGE,
                -1L,
                -100L),
            new InventoryOriginCase(
                InventoryMovementKind.REVERSAL_COMP,
                "STANDARD",
                PostingOriginKind.REVERSAL,
                1L,
                100L));
    Path bookPath = tempDirectory.resolve("inventory-ledger-typed-origins.sqlite");

    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertCanonicalInitializedBookMetadata(database);
          for (int index = 0; index < cases.size(); index++) {
            InventoryOriginCase current = cases.get(index);
            String accountCode = "14%02d".formatted(index);
            String postingId = "posting-origin-%02d".formatted(index);
            insertInventoryAccount(database, accountCode, "Inventory " + index);
            insertPostingFactRow(
                database,
                postingId,
                "idem-origin-%02d".formatted(index),
                "2026-04-07",
                "2026-04-07T10:15:%02dZ".formatted(index),
                current.postingKind(),
                current.postingOriginKind());

            assertEquals(
                1,
                insertTypedInventoryMovement(
                    database,
                    "movement-origin-%02d".formatted(index),
                    accountCode,
                    LocalDate.parse("2026-04-07"),
                    current.movementKind(),
                    current.quantityDelta(),
                    current.costDeltaMinor(),
                    postingId));
          }

          insertInventoryAccount(database, "1499", "Raw Inventory");
          insertPostingFactRow(
              database,
              "posting-raw",
              "idem-raw",
              "2026-04-07",
              "2026-04-07T10:15:30Z",
              "STANDARD",
              PostingOriginKind.DIRECT_JOURNAL);

          insertInventoryAccount(database, "1498", "Mismatched Inventory");
          insertPostingFactRow(
              database,
              "posting-mismatched",
              "idem-mismatched",
              "2026-04-07",
              "2026-04-07T10:15:31Z",
              "STANDARD",
              PostingOriginKind.SALE_SETTLED);

          SqliteNativeException rawOrigin =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      SqliteInventoryCostingWriter.insertInventoryMovement(
                          database,
                          "movement-raw",
                          new AccountCode("1499"),
                          LocalDate.parse("2026-04-07"),
                          InventoryMovementKind.ACQUISITION,
                          1L,
                          100L,
                          new PostingId("posting-raw")));
          assertEquals(SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), rawOrigin.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", rawOrigin.resultName());

          SqliteNativeException mismatchedTypedOrigin =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      SqliteInventoryCostingWriter.insertInventoryMovement(
                          database,
                          "movement-mismatched",
                          new AccountCode("1498"),
                          LocalDate.parse("2026-04-07"),
                          InventoryMovementKind.ACQUISITION,
                          1L,
                          100L,
                          new PostingId("posting-mismatched")));
          assertEquals(
              SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
              mismatchedTypedOrigin.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", mismatchedTypedOrigin.resultName());
          assertEquals(cases.size(), queryInt(database, "select count(*) from inventory_movement"));
        });
  }

  @Test
  void inventoryLedger_treatsEmptyReplayAndOnHandAsConsistent() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-empty.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> assertTrue(SqliteBookIntegrityVerifier.hasConsistentInventoryOnHand(database)));
  }

  @Test
  void inventoryLedger_rejectsOutOfOrderDuplicateAndMutatingWrites() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-guards.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertCanonicalInitializedBookMetadata(database);
          insertInventoryAccount(database, "1400", "Inventory");
          insertPostingFactRow(database, "posting-1", "idem-1");
          insertPostingFactRow(
              database, "posting-2", "idem-2", "2026-04-06", "2026-04-06T10:15:30Z");
          insertPostingFactRow(
              database,
              "posting-capitalization",
              "idem-capitalization",
              "2026-04-07",
              "2026-04-07T10:15:31Z",
              "STANDARD",
              PostingOriginKind.INVENTORY_CAPITALIZATION_SETTLED);

          assertEquals(
              1,
              SqliteInventoryCostingWriter.insertInventoryMovement(
                  database,
                  "movement-1",
                  new AccountCode("1400"),
                  LocalDate.parse("2026-04-07"),
                  InventoryMovementKind.ACQUISITION,
                  10L,
                  1_000L,
                  new PostingId("posting-1")));

          SqliteNativeException nonCanonicalDuplicateSequence =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      database.executeStatement(
                          """
                          insert into inventory_movement (
                              movement_id,
                              inventory_account,
                              effective_date,
                              account_sequence,
                              kind,
                              quantity_delta,
                              cost_delta_minor,
                              posting_id
                          ) values (
                              'movement-duplicate',
                              '1400',
                              '2026-04-07',
                              1,
                              'CAPITALIZATION',
                              0,
                              50,
                              'posting-capitalization'
                          )
                          """));
          assertEquals(
              SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
              nonCanonicalDuplicateSequence.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", nonCanonicalDuplicateSequence.resultName());

          SqliteNativeException nonCanonicalFirstSequence =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      database.executeStatement(
                          """
                          insert into inventory_movement (
                              movement_id,
                              inventory_account,
                              effective_date,
                              account_sequence,
                              kind,
                              quantity_delta,
                              cost_delta_minor,
                              posting_id
                          ) values (
                              'movement-skip-first',
                              '1400',
                              '2026-04-07',
                              99,
                              'CAPITALIZATION',
                              0,
                              50,
                              'posting-capitalization'
                          )
                          """));
          assertEquals(
              SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
              nonCanonicalFirstSequence.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", nonCanonicalFirstSequence.resultName());

          insertPostingFactRow(
              database,
              "posting-3",
              "idem-3",
              "2026-04-07",
              "2026-04-07T11:15:30Z",
              "STANDARD",
              PostingOriginKind.INVENTORY_CAPITALIZATION_SETTLED);
          SqliteNativeException nonCanonicalNextSequence =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      database.executeStatement(
                          """
                          insert into inventory_movement (
                              movement_id,
                              inventory_account,
                              effective_date,
                              account_sequence,
                              kind,
                              quantity_delta,
                              cost_delta_minor,
                              posting_id
                          ) values (
                              'movement-skip-next',
                              '1400',
                              '2026-04-07',
                              3,
                              'CAPITALIZATION',
                              0,
                              50,
                              'posting-3'
                          )
                          """));
          assertEquals(
              SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"),
              nonCanonicalNextSequence.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", nonCanonicalNextSequence.resultName());

          SqliteNativeException outOfOrderInsert =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      SqliteInventoryCostingWriter.insertInventoryMovement(
                          database,
                          "movement-2",
                          new AccountCode("1400"),
                          LocalDate.parse("2026-04-06"),
                          InventoryMovementKind.ACQUISITION,
                          1L,
                          100L,
                          new PostingId("posting-2")));
          assertEquals(
              SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), outOfOrderInsert.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", outOfOrderInsert.resultName());
          assertEquals(1, queryInt(database, "select count(*) from inventory_movement"));

          SqliteNativeException rejectedUpdate =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      database.executeStatement(
                          """
                          update inventory_movement
                          set cost_delta_minor = 999
                          where movement_id = 'movement-1'
                          """));
          assertEquals(
              SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), rejectedUpdate.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", rejectedUpdate.resultName());

          SqliteNativeException rejectedDelete =
              assertThrows(
                  SqliteNativeException.class,
                  () ->
                      database.executeStatement(
                          "delete from inventory_movement where movement_id = 'movement-1'"));
          assertEquals(
              SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), rejectedDelete.resultCode());
          assertEquals("SQLITE_CONSTRAINT_TRIGGER", rejectedDelete.resultName());
        });
  }

  @Test
  void inventoryLedger_requiresExactlyOneReturnedAccountSequenceRow() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-return-shapes.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteStatementRedirectingDatabase noRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteInventoryCostingSql.INSERT_INVENTORY_MOVEMENT.equals(sql)
                              ? """
                                select 1 as account_sequence
                                where 0
                                  and ?1 is not null
                                  and ?2 is not null
                                  and ?3 is not null
                                  and ?4 is not null
                                  and ?5 is not null
                                  and ?6 is not null
                                  and ?7 is not null
                                  and ?8 is not null
                                """
                              : sql));
          IllegalStateException noRowFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteInventoryCostingWriter.insertInventoryMovement(
                          noRowDatabase,
                          "movement-1",
                          new AccountCode("1400"),
                          LocalDate.parse("2026-04-07"),
                          InventoryMovementKind.ACQUISITION,
                          10L,
                          1_000L,
                          new PostingId("posting-1")));
          assertEquals(
              "SQLite inventory movement insert returned no account sequence.",
              noRowFailure.getMessage());

          SqliteStatementRedirectingDatabase extraRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteInventoryCostingSql.INSERT_INVENTORY_MOVEMENT.equals(sql)
                              ? """
                                select 1 as account_sequence
                                where ?1 is not null
                                union all
                                select 2
                                where ?2 is not null
                                  and ?3 is not null
                                  and ?4 is not null
                                  and ?5 is not null
                                  and ?6 is not null
                                  and ?7 is not null
                                  and ?8 is not null
                                """
                              : sql));
          IllegalStateException extraRowFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteInventoryCostingWriter.insertInventoryMovement(
                          extraRowDatabase,
                          "movement-2",
                          new AccountCode("1400"),
                          LocalDate.parse("2026-04-07"),
                          InventoryMovementKind.ACQUISITION,
                          10L,
                          1_000L,
                          new PostingId("posting-1")));
          assertEquals(
              "SQLite inventory movement insert returned more than one account sequence.",
              extraRowFailure.getMessage());
        });
  }

  @Test
  void inventoryLedger_zeroReplayAndZeroPersistedStateRemainConsistent() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-zero-replay.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          insertInventoryAccount(database, "1400", "Inventory");
          insertPostingFactRow(database, "posting-1", "idem-1");
          insertPostingFactRow(
              database,
              "posting-2",
              "idem-2",
              "2026-04-08",
              "2026-04-08T10:15:30Z",
              "STANDARD",
              PostingOriginKind.SALE_SETTLED);
          SqliteInventoryCostingWriter.insertInventoryMovement(
              database,
              "movement-1",
              new AccountCode("1400"),
              LocalDate.parse("2026-04-07"),
              InventoryMovementKind.ACQUISITION,
              10L,
              1_000L,
              new PostingId("posting-1"));
          SqliteInventoryCostingWriter.insertInventoryMovement(
              database,
              "movement-2",
              new AccountCode("1400"),
              LocalDate.parse("2026-04-08"),
              InventoryMovementKind.DISPOSAL,
              -10L,
              -1_000L,
              new PostingId("posting-2"));
          SqliteInventoryCostingWriter.upsertInventoryOnHand(
              database, new AccountCode("1400"), 0L, 0L, LocalDate.parse("2026-04-08"));

          assertTrue(SqliteBookIntegrityVerifier.hasConsistentInventoryOnHand(database));
        });
  }

  @Test
  void inventoryLedger_rejectsInvalidReplay() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-invalid-replay.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          insertInventoryAccount(database, "1400", "Inventory");
          insertPostingFactRow(database, "posting-1", "idem-1");
          insertPostingFactRow(
              database,
              "posting-2",
              "idem-2",
              "2026-04-08",
              "2026-04-08T10:15:30Z",
              "STANDARD",
              PostingOriginKind.SALE_SETTLED);
          SqliteInventoryCostingWriter.insertInventoryMovement(
              database,
              "movement-1",
              new AccountCode("1400"),
              LocalDate.parse("2026-04-07"),
              InventoryMovementKind.ACQUISITION,
              1L,
              100L,
              new PostingId("posting-1"));
          SqliteInventoryCostingWriter.insertInventoryMovement(
              database,
              "movement-2",
              new AccountCode("1400"),
              LocalDate.parse("2026-04-08"),
              InventoryMovementKind.DISPOSAL,
              -2L,
              -200L,
              new PostingId("posting-2"));

          assertFalse(SqliteBookIntegrityVerifier.hasConsistentInventoryOnHand(database));
        });
  }

  @Test
  void inventoryLedger_rejectsInvalidPersistedRowsDuringComparison() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-invalid-persisted.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteStatementRedirectingDatabase redirectedDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteInventoryCostingSql.LOAD_INVENTORY_ON_HAND_ROWS.equals(sql)
                              ? """
                                select '1400' as inventory_account, 0 as quantity, 5 as cost_pool_minor, '2026-04-07' as last_movement_date
                                union all
                                select '9999', 0, 0, '2026-04-07'
                                """
                              : sql));
          assertFalse(SqliteBookIntegrityVerifier.hasConsistentInventoryOnHand(redirectedDatabase));
        });
  }

  @Test
  void inventoryLedger_rejectsNonContiguousReplaySequencesDuringVerification() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-noncontiguous-replay.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteStatementRedirectingDatabase redirectedDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqliteInventoryCostingSql.LOAD_INVENTORY_MOVEMENT_REPLAY_ROWS.equals(sql)
                              ? """
                                select '1400' as inventory_account, '2026-04-07' as effective_date, 1 as account_sequence, 10 as quantity_delta, 1000 as cost_delta_minor
                                union all
                                select '1400', '2026-04-08', 3, -4, -400
                                """
                              : sql));
          assertFalse(SqliteBookIntegrityVerifier.hasConsistentInventoryOnHand(redirectedDatabase));
        });
  }

  @Test
  void inventoryLedger_rejectsPersistedLastMovementDateMismatch() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-last-movement-date-mismatch.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          insertInventoryAccount(database, "1400", "Inventory");
          insertPostingFactRow(database, "posting-1", "idem-1");
          SqliteInventoryCostingWriter.insertInventoryMovement(
              database,
              "movement-1",
              new AccountCode("1400"),
              LocalDate.parse("2026-04-07"),
              InventoryMovementKind.ACQUISITION,
              10L,
              1_000L,
              new PostingId("posting-1"));
          SqliteInventoryCostingWriter.upsertInventoryOnHand(
              database, new AccountCode("1400"), 10L, 1_000L, LocalDate.parse("2026-04-08"));

          assertFalse(SqliteBookIntegrityVerifier.hasConsistentInventoryOnHand(database));
        });
  }

  @Test
  void inventoryLedger_mismatchMakesInitializedBookSemanticsIncomplete() {
    Path bookPath = tempDirectory.resolve("inventory-ledger-mismatch.sqlite");
    initializeBookOnDisk(bookPath);
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          insertInventoryAccount(database, "1400", "Inventory");
          insertPostingFactRow(database, "posting-1", "idem-1");
          SqliteInventoryCostingWriter.insertInventoryMovement(
              database,
              "movement-1",
              new AccountCode("1400"),
              LocalDate.parse("2026-04-07"),
              InventoryMovementKind.ACQUISITION,
              10L,
              1_000L,
              new PostingId("posting-1"));
          SqliteInventoryCostingWriter.upsertInventoryOnHand(
              database, new AccountCode("1400"), 9L, 1_000L, LocalDate.parse("2026-04-07"));

          assertFalse(SqliteBookIntegrityVerifier.hasConsistentInventoryOnHand(database));
          assertEquals(
              SqliteBookState.INCOMPLETE_FINGRIND,
              SqliteBookContract.BOOK_STATE_READER.bookState(database));
        });
  }

  private record InventoryOriginCase(
      InventoryMovementKind movementKind,
      String postingKind,
      PostingOriginKind postingOriginKind,
      long quantityDelta,
      long costDeltaMinor) {}
}
