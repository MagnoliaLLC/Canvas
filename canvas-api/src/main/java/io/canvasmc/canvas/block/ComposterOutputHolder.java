package io.canvasmc.canvas.block;

import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Holds the server-wide {@link ComposterOutput}.
 */
final class ComposterOutputHolder {

    static final AtomicReference<@Nullable ComposterOutput> CURRENT = new AtomicReference<>();

    private ComposterOutputHolder() {
    }
}
