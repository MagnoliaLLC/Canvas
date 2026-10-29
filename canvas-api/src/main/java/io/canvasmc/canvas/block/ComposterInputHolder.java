package io.canvasmc.canvas.block;

import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Holds the server-wide {@link ComposterInput}.
 */
final class ComposterInputHolder {

    static final AtomicReference<@Nullable ComposterInput> CURRENT = new AtomicReference<>();

    private ComposterInputHolder() {
    }
}
