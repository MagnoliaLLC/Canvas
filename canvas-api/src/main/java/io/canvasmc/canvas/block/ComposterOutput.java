package io.canvasmc.canvas.block;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A server-wide source of what a ready composter gives through its bottom face in place of bone meal: the item a
 * hopper or a hopper minecart beneath it takes. A click on the composter is not asked; it drops bone meal as ever.
 * <p>
 * Both methods are called on the composter's region thread, from the tick of a hopper or hopper minecart beside it,
 * every time one looks at the composter. They must answer from memory, must not block, and must not throw.
 */
public interface ComposterOutput {

    /**
     * What a ready composter gives through its bottom face. Asked every time a hopper or a hopper minecart looks at
     * it, whether or not one takes it, so it must be cheap and change nothing. The item returned is copied.
     *
     * @param composter the composter, at its ready level
     * @return the item, an empty stack for nothing to take, or {@code null} for bone meal
     */
    @Nullable ItemStack output(Block composter);

    /**
     * Told once a hopper or a hopper minecart has taken the item {@link #output(Block)} gave, just before the
     * composter is emptied. A take that was refused or put back is not told.
     *
     * @param composter the composter
     */
    void taken(Block composter);

    /**
     * Gets the composter output in use.
     *
     * @return the output, or {@code null} when none is set
     */
    static @Nullable ComposterOutput current() {
        return ComposterOutputHolder.CURRENT.get();
    }

    /**
     * Sets what every ready composter gives through its bottom face, replacing the output in use.
     *
     * @param output the output
     */
    static void set(final ComposterOutput output) {
        ComposterOutputHolder.CURRENT.set(output);
    }

    /**
     * Removes an output, if it is still the one in use, so a plugin disabling never removes another's.
     *
     * @param output the output to remove
     * @return whether it was in use and was removed
     */
    static boolean clear(final ComposterOutput output) {
        return ComposterOutputHolder.CURRENT.compareAndSet(output, null);
    }
}
