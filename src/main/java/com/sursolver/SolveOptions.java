package com.sursolver;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Per-call options of {@code solve}. Immutable; start from {@link #defaults()}.
 *
 * <pre>{@code
 * SolveOptions.defaults().solveTimeout(Duration.ofSeconds(120)).onTaskCreated(id -> log(id))
 * }</pre>
 */
public final class SolveOptions {
    private static final SolveOptions DEFAULTS = new SolveOptions(null, null, null);

    private final Duration pollInterval;
    private final Duration solveTimeout;
    private final Consumer<String> onTaskCreated;

    private SolveOptions(Duration pollInterval, Duration solveTimeout, Consumer<String> onTaskCreated) {
        this.pollInterval = pollInterval;
        this.solveTimeout = solveTimeout;
        this.onTaskCreated = onTaskCreated;
    }

    /** @return options that use the client's settings */
    public static SolveOptions defaults() {
        return DEFAULTS;
    }

    /**
     * @param pollInterval overrides the client's poll interval for this call
     * @return a copy with the override
     */
    public SolveOptions pollInterval(Duration pollInterval) {
        return new SolveOptions(pollInterval, solveTimeout, onTaskCreated);
    }

    /**
     * @param solveTimeout overrides the client's deadline for this call
     * @return a copy with the override
     */
    public SolveOptions solveTimeout(Duration solveTimeout) {
        return new SolveOptions(pollInterval, solveTimeout, onTaskCreated);
    }

    /**
     * @param onTaskCreated called once with the task id as soon as the task is accepted (and charged),
     *     so the id survives an interruption or a {@link SolveTimeoutException}
     * @return a copy with the callback
     */
    public SolveOptions onTaskCreated(Consumer<String> onTaskCreated) {
        return new SolveOptions(pollInterval, solveTimeout, onTaskCreated);
    }

    Duration pollInterval() {
        return pollInterval;
    }

    Duration solveTimeout() {
        return solveTimeout;
    }

    Consumer<String> onTaskCreated() {
        return onTaskCreated;
    }
}
