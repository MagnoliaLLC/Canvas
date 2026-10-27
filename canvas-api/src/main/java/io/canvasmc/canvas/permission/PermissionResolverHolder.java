package io.canvasmc.canvas.permission;

import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Holds the server-wide {@link PermissionResolver}.
 */
final class PermissionResolverHolder {

    static final AtomicReference<@Nullable PermissionResolver> CURRENT = new AtomicReference<>();

    private PermissionResolverHolder() {
    }
}
