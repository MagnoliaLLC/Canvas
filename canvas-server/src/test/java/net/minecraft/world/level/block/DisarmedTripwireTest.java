package net.minecraft.world.level.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

/**
 * A disarmed wire, which vanilla only holds while shears cut it, keeps its state: a changed neighbor neither connects nor
 * disconnects it, and no wire connects to it. An armed wire still follows its neighbors.
 */
@VanillaFeature
public class DisarmedTripwireTest {

    private static final BlockState ARMED = Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.EAST, true);
    private static final BlockState DISARMED = ARMED.setValue(TripWireBlock.DISARMED, true);

    private static BlockState neighborChanged(final BlockState wire, final BlockState neighbor) {
        return wire.updateShape(null, null, BlockPos.ZERO, Direction.EAST, BlockPos.ZERO.east(), neighbor, null);
    }

    @Test
    public void disarmedWireKeepsItsConnections() {
        assertEquals(DISARMED, neighborChanged(DISARMED, Blocks.STONE.defaultBlockState()));
        assertEquals(DISARMED.setValue(TripWireBlock.EAST, false), neighborChanged(DISARMED.setValue(TripWireBlock.EAST, false), Blocks.TRIPWIRE.defaultBlockState()));
    }

    @Test
    public void armedWireFollowsItsNeighbors() {
        assertFalse(neighborChanged(ARMED, Blocks.STONE.defaultBlockState()).getValue(TripWireBlock.EAST));
        assertTrue(neighborChanged(ARMED.setValue(TripWireBlock.EAST, false), Blocks.TRIPWIRE.defaultBlockState()).getValue(TripWireBlock.EAST));
    }

    @Test
    public void noWireConnectsToADisarmedOne() {
        final TripWireBlock wire = (TripWireBlock) Blocks.TRIPWIRE;

        assertFalse(wire.shouldConnectTo(DISARMED, Direction.EAST));
        assertTrue(wire.shouldConnectTo(ARMED, Direction.EAST));
    }
}
