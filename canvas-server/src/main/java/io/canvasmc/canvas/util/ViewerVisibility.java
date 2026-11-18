package io.canvasmc.canvas.util;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.booleans.BooleanPredicate;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What one viewer sees of the targets that don't follow their own default: the visibility the viewer is held at and
 * the plugins holding it there. A hide, a show or a default reset for a target, and the player info it sends, run under
 * that target's entry, as do the join, quit, profile and listing info sent to the viewer about it, so the changes for
 * one pair made from different regions apply in one order and reach the viewer's connection in that order. The batch a
 * joining viewer is sent is built outside any entry, so the viewer rechecks each target in it under its entry.
 *
 * @implNote Callbacks run inside {@link ConcurrentHashMap#compute}, so they must not block or touch this viewer's
 *     entries.
 */
public final class ViewerVisibility {
    private final Map<UUID, Forced> entries = new ConcurrentHashMap<>();

    /**
     * Gets if no target is held away from its default
     *
     * @return {@code true} if every target follows its default, {@code false} otherwise
     */
    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /**
     * Gets if the viewer currently sees the target
     *
     * @param target
     *     the target's id
     * @param visibleByDefault
     *     the target's default
     *
     * @return {@code true} if the target is visible to the viewer, {@code false} otherwise
     */
    public boolean canSee(final UUID target, final boolean visibleByDefault) {
        if (this.entries.isEmpty()) {
            return visibleByDefault;
        }

        final Forced forced = this.entries.get(target);
        return forced == null ? visibleByDefault : forced.visible();
    }

    /**
     * Records that a plugin wants the target visible or hidden. The default is read under the entry, and a held
     * visibility that matches it is left over from before a default change whose reset hasn't reached this viewer
     * yet, so it counts as no entry.
     *
     * @param target
     *     the target's id
     * @param visible
     *     whether the plugin wants the target visible
     * @param visibleByDefault
     *     reads the target's default
     * @param plugin
     *     gets the plugin's reference, {@code null} for none
     * @param sender
     *     sends the target's player info when what the viewer sees changes
     *
     * @return {@code true} if what the viewer sees changed, {@code false} otherwise
     */
    public boolean change(final UUID target, final boolean visible, final BooleanSupplier visibleByDefault, final Supplier<? extends @Nullable Object> plugin, final BooleanConsumer sender) {
        final boolean[] changed = new boolean[1];

        this.entries.compute(target, (uuid, forced) -> {
            final boolean byDefault = visibleByDefault.getAsBoolean();
            if (forced == null || forced.visible() == byDefault) {
                if (visible == byDefault) {
                    return forced;
                }

                final ObjectOpenHashSet<@Nullable Object> plugins = new ObjectOpenHashSet<>();
                plugins.add(plugin.get());
                changed[0] = true;
                sender.accept(visible);
                return new Forced(visible, plugins);
            }

            if (forced.visible() == visible) {
                forced.plugins().add(plugin.get());
                return forced;
            }

            forced.plugins().remove(plugin.get());
            if (!forced.plugins().isEmpty()) {
                return forced;
            }

            changed[0] = true;
            sender.accept(visible);
            return null;
        });

        return changed[0];
    }

    /**
     * Drops the target's entry after its default changed
     *
     * @param target
     *     the target's id
     * @param visible
     *     the target's new default
     * @param sender
     *     sends the target's player info when what the viewer sees changes
     *
     * @return {@code true} if what the viewer sees changed, {@code false} otherwise
     */
    public boolean reset(final UUID target, final boolean visible, final BooleanConsumer sender) {
        final boolean[] changed = new boolean[1];

        this.entries.compute(target, (uuid, forced) -> {
            if ((forced == null ? !visible : forced.visible()) != visible) {
                changed[0] = true;
                sender.accept(visible);
            }

            return null;
        });

        return changed[0];
    }

    /**
     * Runs the action under the target's entry with what the viewer sees of it
     *
     * @param target
     *     the target's id
     * @param visibleByDefault
     *     reads the target's default
     * @param keep
     *     the action, returning {@code false} to drop the entry
     *
     * @return {@code true} if the target was visible to the viewer, {@code false} otherwise
     */
    public boolean apply(final UUID target, final BooleanSupplier visibleByDefault, final BooleanPredicate keep) {
        final boolean[] visible = new boolean[1];

        this.entries.compute(target, (uuid, forced) -> {
            visible[0] = forced == null ? visibleByDefault.getAsBoolean() : forced.visible();
            return keep.test(visible[0]) ? forced : null;
        });

        return visible[0];
    }

    /**
     * Runs the action under the target's entry, leaving the entry as it was
     *
     * @param target
     *     the target's id
     * @param action
     *     the action
     */
    public void run(final UUID target, final Runnable action) {
        this.entries.compute(target, (uuid, forced) -> {
            action.run();
            return forced;
        });
    }

    /**
     * Drops the target's entry, sending nothing
     *
     * @param target
     *     the target's id
     */
    public void remove(final UUID target) {
        this.entries.remove(target);
    }

    private record Forced(boolean visible, ObjectOpenHashSet<@Nullable Object> plugins) {
    }
}
