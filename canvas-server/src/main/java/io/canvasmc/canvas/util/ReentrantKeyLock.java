package io.canvasmc.canvas.util;

import ca.spottedleaf.concurrentutil.lock.Notifier;
import ca.spottedleaf.concurrentutil.map.concurrent.longs.ConcurrentChainedLong2ReferenceHashTable;
import it.unimi.dsi.fastutil.longs.LongCollection;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Locks sets of keys, like {@link ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock} does an area. A set is taken
 * whole or not at all, in key order: a lock finding a key held gives back what it took and waits for the holder
 * <p>
 * Keys already held may be locked again, which does nothing, but never a set that is only partly held
 */
public final class ReentrantKeyLock {
    private final ConcurrentChainedLong2ReferenceHashTable<@Nullable Node> nodes = ConcurrentChainedLong2ReferenceHashTable.createWithCapacity(ConcurrentChainedLong2ReferenceHashTable.defaultCapacity(), 0.25f);
    private final ThreadLocal<int[]> holds = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * Gets if the current thread holds any key
     *
     * @return {@code true} if the current thread holds a key, {@code false} otherwise
     */
    public boolean isHeldByCurrentThread() {
        return this.holds.get()[0] != 0;
    }

    /**
     * Locks one key, waiting until no other thread holds it
     *
     * @param key
     *     the key
     *
     * @return the node to unlock with
     */
    public Node lock(final long key) {
        return this.lock(new long[] { key });
    }

    /**
     * Locks one key if no other thread holds it, without waiting
     *
     * @param key
     *     the key
     *
     * @return the node to unlock with, or {@code null} if another thread holds the key
     */
    public @Nullable Node tryLock(final long key) {
        final Thread currentThread = Thread.currentThread();
        final Node node = new Node(this, new long[] { key }, currentThread);
        final Node prev = this.nodes.putIfAbsent(key, node);
        if (prev == null) {
            node.length = 1;
            ++this.holds.get()[0];
            return node;
        }
        return prev.thread == currentThread ? node : null;
    }

    /**
     * Locks every key in the set, waiting until no other thread holds any of them
     *
     * @param keys
     *     the keys
     *
     * @return the node to unlock with
     *
     * @throws IllegalStateException
     *     if the current thread holds some of the keys but not all of them
     */
    public Node lock(final LongCollection keys) {
        final long[] sorted = keys.toLongArray();
        Arrays.sort(sorted);
        return this.lock(sorted);
    }

    private Node lock(final long[] keys) {
        final Thread currentThread = Thread.currentThread();

        for (;;) {
            final Node node = new Node(this, new long[keys.length], currentThread);
            Node park = null;
            int added = 0;
            boolean reentered = false;

            for (final long key : keys) {
                final Node prev = this.nodes.putIfAbsent(key, node);
                if (prev == null) {
                    node.keys[added++] = key;
                } else if (prev.thread == currentThread) {
                    reentered = true;
                } else {
                    park = prev;
                    break;
                }
            }

            if (park == null && (!reentered || added == 0)) {
                node.length = added;
                if (added != 0) {
                    ++this.holds.get()[0];
                }
                return node;
            }

            for (int i = 0; i < added; ++i) {
                if (this.nodes.remove(node.keys[i], node) != node) {
                    throw new IllegalStateException();
                }
            }
            // a fresh node per attempt, so a thread that saw this one is never left waiting on it
            node.notifier.notifyThreads();

            if (park == null || reentered) {
                throw new IllegalStateException("Improper lock usage: Should never lock keys that are partly held");
            }

            park.notifier.waitUntilReady();
        }
    }

    /**
     * Adds a key to the node, as if it had been locked with it
     *
     * @param node
     *     a node the current thread holds
     * @param key
     *     the key, which no thread may hold
     *
     * @throws IllegalStateException
     *     if the key is held, or the node isn't one the current thread holds
     */
    public void add(final Node node, final long key) {
        if (node.lock != this || node.thread != Thread.currentThread() || node.length == 0) {
            throw new IllegalStateException("Improper lock usage: Should only add to a node the current thread holds");
        }
        if (this.nodes.putIfAbsent(key, node) != null) {
            throw new IllegalStateException("Key " + key + " is already held");
        }
        if (node.length == node.keys.length) {
            node.keys = Arrays.copyOf(node.keys, node.length * 2);
        }
        node.keys[node.length++] = key;
    }

    /**
     * Unlocks the keys the node holds, waking the threads waiting for them
     *
     * @param node
     *     the node a lock returned
     */
    public void unlock(final Node node) {
        if (node.lock != this) {
            throw new IllegalStateException("Unlock target lock mismatch");
        }
        if (node.length == 0) {
            return;
        }

        for (int i = 0; i < node.length; ++i) {
            if (this.nodes.remove(node.keys[i], node) != node) {
                throw new IllegalStateException();
            }
        }
        --this.holds.get()[0];

        node.notifier.notifyThreads();
    }

    /**
     * The keys one lock call holds, given back to {@link #unlock(Node)}
     */
    public static final class Node {
        private final ReentrantKeyLock lock;
        private long[] keys;
        private final Thread thread;
        private final Notifier notifier = new Notifier(true);
        private int length;

        private Node(final ReentrantKeyLock lock, final long[] keys, final Thread thread) {
            this.lock = lock;
            this.keys = keys;
            this.thread = thread;
        }

        @Override
        public String toString() {
            return "Node{" +
                "keys=" + Arrays.toString(Arrays.copyOf(this.keys, this.length)) +
                ", thread=" + this.thread +
                '}';
        }
    }
}
