package io.canvasmc.canvas.world.inventory;

/**
 * Told whenever a held stack changes, so checks over what it holds can be skipped.
 */
public interface StackHolder {

    void canvas$stackChanged();
}
