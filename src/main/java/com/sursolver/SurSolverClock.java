package com.sursolver;

import java.time.Duration;
import java.time.Instant;

/** Lets tests drive time without waiting. */
public interface SurSolverClock {
    /** @return the current time */
    Instant now();

    /**
     * Waits.
     *
     * @param duration how long
     * @throws InterruptedException when the thread is interrupted first
     */
    void sleep(Duration duration) throws InterruptedException;

    /** @return the real clock */
    static SurSolverClock system() {
        return SystemClock.INSTANCE;
    }
}

final class SystemClock implements SurSolverClock {
    static final SystemClock INSTANCE = new SystemClock();

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }
}
