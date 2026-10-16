package io.canvasmc.canvas.util;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryCondition;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import org.jspecify.annotations.Nullable;

/**
 * The memory conditions a behavior built from pure memory reads checks before anything else, tested
 * straight off the brain's memory slots. A failed test means the behavior cannot start, and nothing it
 * would have run has run: no closure, no random draw, no write.
 */
public final class MemoryGate {

    private static final byte REGISTERED = 0;
    private static final byte PRESENT = 1;
    private static final byte ABSENT = 2;
    private static final AtomicInteger NEXT_INDEX = new AtomicInteger();

    private final int[] indexes;
    private final byte[] statuses;

    private MemoryGate(final int[] indexes, final byte[] statuses) {
        this.indexes = indexes;
        this.statuses = statuses;
    }

    /**
     * The next memory type's slot index, handed out once per type as each is constructed.
     */
    public static int nextIndex() {
        return NEXT_INDEX.getAndIncrement();
    }

    public static @Nullable MemoryGate of(final List<MemoryCondition<?, ?>> conditions) {
        if (conditions.isEmpty()) {
            return null;
        }

        final int[] indexes = new int[conditions.size()];
        final byte[] statuses = new byte[conditions.size()];
        for (int i = 0; i < indexes.length; ++i) {
            final MemoryCondition<?, ?> condition = conditions.get(i);
            indexes[i] = condition.memory().canvas$index;
            switch (condition) {
                case MemoryCondition.Present<?> _ -> statuses[i] = PRESENT;
                case MemoryCondition.Absent<?> _ -> statuses[i] = ABSENT;
                case MemoryCondition.Registered<?> _ -> statuses[i] = REGISTERED;
                default -> {
                    // a condition this does not know how to test
                    return null;
                }
            }
        }
        return new MemoryGate(indexes, statuses);
    }

    public boolean test(final Brain<?> brain) {
        final MemorySlot<?>[] slots = brain.canvas$memorySlots();
        for (int i = 0; i < this.indexes.length; ++i) {
            final int index = this.indexes[i];
            final MemorySlot<?> slot = index < slots.length ? slots[index] : null;
            if (slot == null) {
                return false;
            }

            final byte status = this.statuses[i];
            if (status == PRESENT ? !slot.hasValue() : status == ABSENT && slot.hasValue()) {
                return false;
            }
        }
        return true;
    }
}
