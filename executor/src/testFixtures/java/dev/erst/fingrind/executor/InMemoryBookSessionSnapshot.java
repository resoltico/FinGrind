package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Immutable rollback snapshot for one in-memory executor fixture session. */
record InMemoryBookSessionSnapshot(
    boolean initialized,
    Instant initializedAt,
    BookIdentity bookIdentity,
    Map<AccountCode, RegisteredAccount> accountsByCode,
    Map<IdempotencyKey, CommittedPosting> postingsByIdempotencyKey,
    Map<PostingId, CommittedPosting> postingsByPostingId,
    Map<PostingId, CommittedPosting> reversalsByPriorPostingId,
    List<TransferredPeriodResult> transferredPeriodResults) {}
