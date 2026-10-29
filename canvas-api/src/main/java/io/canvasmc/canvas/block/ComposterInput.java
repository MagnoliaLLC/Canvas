package io.canvasmc.canvas.block;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A server-wide judge of what a composter lets a hopper, a dropper or a crafter put in through its top face. An item
 * refused stays where it was. A player or a villager composting is not asked; their composting fires its own event.
 * <p>
 * It is called on the composter's region thread, from the tick of whatever is putting the item in, every time one
 * would. It must answer from memory, must not block, and must not throw.
 */
public interface ComposterInput {

    /**
     * Whether a composter takes an item through its top face. Asked every time a hopper, a dropper or a crafter would
     * put one in, whether or not it goes in, so it must be cheap and change nothing.
     *
     * @param composter the composter, below its full level
     * @param item      the item, a view of the stack being put in, which must not be changed
     * @return whether it may go in
     */
    boolean accepts(Block composter, ItemStack item);

    /**
     * Gets the composter input in use.
     *
     * @return the input, or {@code null} when none is set
     */
    static @Nullable ComposterInput current() {
        return ComposterInputHolder.CURRENT.get();
    }

    /**
     * Sets what every composter lets a hopper, a dropper or a crafter put in, replacing the input in use.
     *
     * @param input the input
     */
    static void set(final ComposterInput input) {
        ComposterInputHolder.CURRENT.set(input);
    }

    /**
     * Removes an input, if it is still the one in use, so a plugin disabling never removes another's.
     *
     * @param input the input to remove
     * @return whether it was in use and was removed
     */
    static boolean clear(final ComposterInput input) {
        return ComposterInputHolder.CURRENT.compareAndSet(input, null);
    }
}
