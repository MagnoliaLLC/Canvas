package io.canvasmc.canvas.permission;

import java.util.Map;
import net.kyori.adventure.util.TriState;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.jspecify.annotations.Nullable;

/**
 * A server-wide source of player permissions, consulted by every player's {@link Permissible} in place of a
 * permissible injected by reflection.
 * <p>
 * A player's permission is decided in this order: a permission set by a plugin's
 * {@link org.bukkit.permissions.PermissionAttachment} (and its children), then this resolver, then the vanilla
 * answer (Bukkit's default permissions, a registered permission's default, and operator status). This applies to
 * {@link Permissible#hasPermission(String)}, {@link Permissible#hasPermission(org.bukkit.permissions.Permission)},
 * {@link Permissible#isPermissionSet(String)} and {@link Permissible#getEffectivePermissions()}.
 * <p>
 * The resolver is called from any thread a permission is checked on: region threads, the global region thread,
 * asynchronous chat and network threads. It must answer from memory, must not block, must not touch world or
 * entity state, and must not throw.
 */
public interface PermissionResolver {

    /**
     * Resolves a permission for a player.
     *
     * @param player     the player
     * @param permission the permission, in lower case
     * @return {@link TriState#TRUE} or {@link TriState#FALSE} when the resolver decides it, {@link TriState#NOT_SET}
     *     to leave it to the vanilla answer
     */
    TriState permissionValue(Player player, String permission);

    /**
     * Every permission the resolver decides for a player where they stand, with its value.
     *
     * @param player the player
     * @return the permissions, in lower case, and their values
     */
    Map<String, Boolean> effectivePermissions(Player player);

    /**
     * Gets the resolver in use.
     *
     * @return the resolver, or {@code null} when none is set
     */
    static @Nullable PermissionResolver resolver() {
        return PermissionResolverHolder.CURRENT.get();
    }

    /**
     * Sets the resolver every player's permissions are read through, replacing the one in use.
     *
     * @param resolver the resolver
     */
    static void setResolver(final PermissionResolver resolver) {
        PermissionResolverHolder.CURRENT.set(resolver);
    }

    /**
     * Removes a resolver, if it is still the one in use, so a plugin disabling never removes another's.
     *
     * @param resolver the resolver to remove
     * @return whether it was in use and was removed
     */
    static boolean clearResolver(final PermissionResolver resolver) {
        return PermissionResolverHolder.CURRENT.compareAndSet(resolver, null);
    }
}
