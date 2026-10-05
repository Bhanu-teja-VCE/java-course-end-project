package com.greencorridor.engine;

/**
 * Simulated time. It runs "speed" times faster than the wall clock and stands still
 * while paused. Every simulation thread passes through {@link #awaitIfPaused()} on each
 * step, so pausing parks all of them on this object's monitor until {@link #setPaused}
 * is called with false, which wakes them with notifyAll().
 */
public class SimClock {

    private long baseSimMs;         // simulated time at the moment of the last speed/pause change
    private long baseRealNanos = System.nanoTime();
    private int speed = 1;
    private boolean paused;

    /** Current simulated time in milliseconds since the run started. */
    public synchronized long now() {
        if (paused) {
            return baseSimMs;
        }
        return baseSimMs + (System.nanoTime() - baseRealNanos) / 1000000L * speed;
    }

    public synchronized void setSpeed(int newSpeed) {
        if (newSpeed < 1) {
            throw new IllegalArgumentException("Speed must be at least 1");
        }
        rebase();
        speed = newSpeed;
    }

    public synchronized int getSpeed() {
        return speed;
    }

    public synchronized void setPaused(boolean pause) {
        if (pause == paused) {
            return;
        }
        rebase();
        paused = pause;
        if (!paused) {
            notifyAll();            // inter-thread communication: wake every parked thread
        }
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    /** Blocks the calling thread while the simulation is paused. */
    public synchronized void awaitIfPaused() throws InterruptedException {
        while (paused) {
            wait();
        }
    }

    private void rebase() {
        baseSimMs = now();
        baseRealNanos = System.nanoTime();
    }
}
