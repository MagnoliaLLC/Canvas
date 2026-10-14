package io.canvasmc.canvas.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dense index per entity class queried, so a chunk can find its collection for a class by array position.
 */
public final class EntityClassIndex {

    private static final AtomicInteger NEXT = new AtomicInteger();
    private static final ClassValue<Integer> INDEX = new ClassValue<>() {
        @Override
        protected Integer computeValue(final Class<?> type) {
            return NEXT.getAndIncrement();
        }
    };

    private EntityClassIndex() {
    }

    public static int of(final Class<?> type) {
        return INDEX.get(type);
    }
}
