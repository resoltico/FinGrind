import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds the one shared Gradle build-state lease while one wrapper-launched Gradle process runs.
 *
 * <p>The operating-system lock, rather than the lock-file's existence, is authoritative. That
 * means a terminated launcher cannot leave a stale lease that requires a cleanup heuristic.
 */
public final class GradleInvocationLease {
    private static final long RETRY_DELAY_MILLIS = 100L;
    private static final Duration WAIT_REPORT_INTERVAL = Duration.ofSeconds(15);
    private static final Duration CHILD_STOP_GRACE_PERIOD = Duration.ofSeconds(10);
    private static final long CHILD_STOP_RETRY_DELAY_MILLIS = 100L;

    private GradleInvocationLease() {}

    public static void main(String[] arguments) {
        int exitCode;
        try {
            exitCode = run(arguments);
        } catch (IllegalArgumentException exception) {
            System.err.printf("FinGrind Gradle wrapper: %s%n", exception.getMessage());
            exitCode = 2;
        } catch (IOException exception) {
            System.err.printf(
                "FinGrind Gradle wrapper: unable to manage the shared build-state lease: %s%n",
                exception.getMessage()
            );
            exitCode = 1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("FinGrind Gradle wrapper: interrupted while waiting for the shared build-state lease.");
            exitCode = 130;
        }
        System.exit(exitCode);
    }

    private static int run(String[] arguments) throws IOException, InterruptedException {
        LeaseCommand command = LeaseCommand.parse(arguments);
        Path leaseFile = command.leaseFile();
        Path leaseDirectory = leaseFile.getParent();
        if (leaseDirectory == null) {
            throw new IllegalArgumentException("the shared build-state lease path must have a parent directory");
        }
        Files.createDirectories(leaseDirectory);

        try (FileChannel leaseChannel = FileChannel.open(leaseFile, CREATE, WRITE);
             FileLock ignored = acquireLease(leaseChannel, leaseFile)) {
            return runChild(command.childCommand());
        }
    }

    private static FileLock acquireLease(FileChannel leaseChannel, Path leaseFile)
        throws IOException, InterruptedException {
        boolean reportedWaiting = false;
        long nextWaitReportNanos = 0L;
        while (true) {
            try {
                FileLock lease = leaseChannel.tryLock();
                if (lease != null) {
                    if (reportedWaiting) {
                        System.err.printf(
                            "FinGrind Gradle wrapper: acquired the shared build-state lease at %s.%n",
                            leaseFile
                        );
                    }
                    return lease;
                }
            } catch (OverlappingFileLockException ignored) {
                // A wrapper process never takes the same lease twice, but retrying is safe if one does.
            }

            long now = System.nanoTime();
            if (!reportedWaiting || now >= nextWaitReportNanos) {
                System.err.printf(
                    "FinGrind Gradle wrapper: waiting for the shared build-state lease at %s.%n",
                    leaseFile
                );
                reportedWaiting = true;
                nextWaitReportNanos = now + WAIT_REPORT_INTERVAL.toNanos();
            }
            Thread.sleep(RETRY_DELAY_MILLIS);
        }
    }

    private static int runChild(List<String> childCommand) throws IOException, InterruptedException {
        Process child = new ProcessBuilder(childCommand).inheritIO().start();
        Thread shutdownHook = new Thread(() -> stopChild(child), "fingrind-gradle-lease-shutdown");
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(shutdownHook);
        try {
            return child.waitFor();
        } catch (InterruptedException exception) {
            stopChild(child);
            throw exception;
        } finally {
            try {
                runtime.removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // The JVM is already shutting down, so the hook owns child termination.
            }
        }
    }

    private static void stopChild(Process child) {
        ProcessHandle childHandle = child.toHandle();
        Set<ProcessHandle> processTree = new LinkedHashSet<>();
        processTree.add(childHandle);
        boolean interrupted = false;
        long gracefulStopDeadline = System.nanoTime() + CHILD_STOP_GRACE_PERIOD.toNanos();

        while (true) {
            collectLiveDescendants(processTree);
            if (!hasLiveProcess(processTree)) {
                break;
            }

            requestTreeTermination(childHandle, processTree, false);
            if (System.nanoTime() >= gracefulStopDeadline) {
                break;
            }
            interrupted |= pauseBeforeTreeTerminationRetry();
        }

        while (true) {
            collectLiveDescendants(processTree);
            if (!hasLiveProcess(processTree)) {
                break;
            }

            requestTreeTermination(childHandle, processTree, true);
            interrupted |= pauseBeforeTreeTerminationRetry();
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void collectLiveDescendants(Set<ProcessHandle> processTree) {
        List<ProcessHandle> knownProcesses = List.copyOf(processTree);
        for (ProcessHandle knownProcess : knownProcesses) {
            if (knownProcess.isAlive()) {
                knownProcess.descendants().forEach(processTree::add);
            }
        }
    }

    private static boolean hasLiveProcess(Set<ProcessHandle> processTree) {
        return processTree.stream().anyMatch(ProcessHandle::isAlive);
    }

    private static void requestTreeTermination(
        ProcessHandle childHandle,
        Set<ProcessHandle> processTree,
        boolean forcibly
    ) {
        List<ProcessHandle> knownProcesses = new ArrayList<>(processTree);
        for (int index = knownProcesses.size() - 1; index >= 0; index--) {
            ProcessHandle knownProcess = knownProcesses.get(index);
            if (!knownProcess.equals(childHandle) && knownProcess.isAlive()) {
                terminate(knownProcess, forcibly);
            }
        }
        if (childHandle.isAlive()) {
            terminate(childHandle, forcibly);
        }
    }

    private static void terminate(ProcessHandle process, boolean forcibly) {
        if (forcibly) {
            process.destroyForcibly();
        } else {
            process.destroy();
        }
    }

    private static boolean pauseBeforeTreeTerminationRetry() {
        try {
            Thread.sleep(CHILD_STOP_RETRY_DELAY_MILLIS);
            return false;
        } catch (InterruptedException ignored) {
            return true;
        }
    }

    private record LeaseCommand(Path leaseFile, List<String> childCommand) {
        private static LeaseCommand parse(String[] arguments) {
            if (arguments.length < 3 || !"--".equals(arguments[1])) {
                throw new IllegalArgumentException(
                    "expected <lease-file> -- <Gradle Java executable> [Gradle Java arguments...]"
                );
            }
            Path leaseFile = Path.of(arguments[0]).toAbsolutePath().normalize();
            List<String> childCommand = List.copyOf(Arrays.asList(arguments).subList(2, arguments.length));
            return new LeaseCommand(leaseFile, childCommand);
        }
    }
}
