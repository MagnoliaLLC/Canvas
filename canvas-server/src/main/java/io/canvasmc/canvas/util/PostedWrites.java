package io.canvasmc.canvas.util;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The tasks a connection posted to its event loop that haven't finished. A queue drained on the event loop writes
 * straight to the channel, while one drained from a tick thread posts its writes to the event loop, so a write made on
 * the event loop while a posted one still waits would reach the wire ahead of it. A drain on the event loop writes
 * inline only while this is empty, and otherwise posts behind the waiting tasks, as does a close.
 *
 * @implNote A task counts from before it is handed over until it has run, the running one included. A post orders only
 *     against the posts that returned before it, so the caller makes each post and its check of {@link #isEmpty()}
 *     under the same lock.
 */
public final class PostedWrites {
    private final AtomicInteger pending = new AtomicInteger();

    /**
     * Gets if every posted task has finished
     *
     * @return {@code true} if no posted task is waiting or running, {@code false} otherwise
     */
    public boolean isEmpty() {
        return this.pending.get() == 0;
    }

    /**
     * Posts the task through the executor, counted until it has run
     *
     * @param executor
     *     the event loop, or a way to post to it
     * @param task
     *     the task
     *
     * @throws RejectedExecutionException
     *     if the executor rejects the task, which then doesn't count
     */
    public void post(final Executor executor, final Runnable task) {
        this.pending.getAndIncrement();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    this.pending.getAndDecrement();
                }
            });
        } catch (final RejectedExecutionException exception) {
            this.pending.getAndDecrement();
            throw exception;
        }
    }
}
