package io.canvasmc.canvas.world.entities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A mob resting on a stone floor must wake however the floor goes: a block write, a section swapped into its chunk the way
 * FastAsyncWorldEdit applies an edit, or a section's states written in place with no block write. While nothing it rests
 * on changes, a section swapped elsewhere in its chunk included, it keeps resting.
 */
@VanillaFeature
public class RestFingerprintTest {

    private static final int MIN_SECTION_Y = -4;
    private static final int SECTIONS = 24;
    private static final int FLOOR_SECTION = 3 - MIN_SECTION_Y;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final double X = 8.5;
    private static final double Y = 64.0;
    private static final double Z = 8.5;
    private static final AABB BOX = new AABB(8.05, 64.0, 8.05, 8.95, 64.9, 8.95);
    private static final Vec3 VELOCITY = new Vec3(0.0, -0.0784000015258789, 0.0);
    private static final VarHandle SECTION_SLOT = MethodHandles.arrayElementVarHandle(LevelChunkSection[].class);

    private final PalettedContainerFactory factory = PalettedContainerFactory.create(RegistryHelper.registryAccess());
    private final LevelChunkSection[] sections = new LevelChunkSection[SECTIONS];
    private RestFingerprint rest;

    @BeforeEach
    public void restOnAStoneFloor() {
        for (int i = 0; i < SECTIONS; ++i) {
            this.sections[i] = this.section();
        }
        final LevelChunkSection floor = this.sections[FLOOR_SECTION];
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                floor.setBlockState(x, 15, z, Blocks.STONE.defaultBlockState(), false);
            }
        }
        final LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getSections()).thenReturn(this.sections);
        final Level level = mock(Level.class);
        when(level.getMinSectionY()).thenReturn(MIN_SECTION_Y);
        when(level.getMaxSectionY()).thenReturn(MIN_SECTION_Y + SECTIONS - 1);
        when(level.getChunkIfLoadedImmediately(anyInt(), anyInt())).thenReturn(chunk);
        this.rest = RestFingerprint.of(level, BOX, X, Y, Z, VELOCITY, 0.0, 0.0, 0.08);
        assertNotNull(this.rest);
        assertTrue(this.resting());
    }

    private LevelChunkSection section() {
        return new LevelChunkSection(this.factory.createForBlockStates(), this.factory.createForBiomes());
    }

    private boolean resting() {
        return this.rest.holds(X, Y, Z, VELOCITY, BOX);
    }

    private void swapIn(final int index) {
        assertTrue(SECTION_SLOT.compareAndSet(this.sections, index, this.sections[index], this.section()));
    }

    @Test
    public void restHoldsWhileNothingItRestsOnChanges() {
        this.swapIn(0);
        assertTrue(this.resting());
        assertTrue(this.resting());
    }

    @Test
    public void restEndsOnABlockWrite() {
        this.sections[FLOOR_SECTION].setBlockState(8, 15, 8, AIR);
        assertFalse(this.resting());
    }

    @Test
    public void restEndsWhenASectionIsSwappedIn() {
        this.swapIn(FLOOR_SECTION);
        assertFalse(this.resting());
    }

    @Test
    public void restEndsWhenStatesAreSetInPlace() {
        this.sections[FLOOR_SECTION].getStates().set(8, 15, 8, AIR);
        assertFalse(this.resting());
    }

    @Test
    public void restEndsWhenStatesAreWrittenUnchecked() {
        this.sections[FLOOR_SECTION].getStates().getAndSetUnchecked(8, 15, 8, AIR);
        assertFalse(this.resting());
    }
}
