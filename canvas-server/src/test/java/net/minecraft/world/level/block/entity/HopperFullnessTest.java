package net.minecraft.world.level.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

/**
 * Hopper fullness checks read plain containers off their item lists: the answer must match the slot-by-slot vanilla check for
 * every fill, single and double chests alike.
 */
@VanillaFeature
public class HopperFullnessTest {

    private static final List<Item> ITEMS = List.of(Items.STONE, Items.ENDER_PEARL, Items.DIAMOND_SWORD, Items.SNOWBALL, Items.OAK_LOG, Items.BUCKET);
    private static final int TRIALS = 4000;

    private static boolean vanillaFull(final Container container) {
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            final ItemStack itemStack = container.getItem(slot);
            if (itemStack.getCount() < itemStack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hopperFull(final Container container) throws ReflectiveOperationException {
        final Method method = HopperBlockEntity.class.getDeclaredMethod("isFullContainer", Container.class, Direction.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, container, Direction.DOWN);
    }

    private static void fill(final BaseContainerBlockEntity container, final Random random) {
        final int mode = random.nextInt(4);
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            final Item item = ITEMS.get(random.nextInt(ITEMS.size()));
            final int max = new ItemStack(item).getMaxStackSize();
            final int count = switch (mode) {
                case 0 -> max;
                case 1 -> random.nextInt(50) == 0 ? random.nextInt(max + 1) : max;
                case 2 -> random.nextInt(max + 1);
                default -> slot == container.getContainerSize() - 1 ? max - 1 : max;
            };
            container.setItem(slot, count == 0 ? ItemStack.EMPTY : new ItemStack(item, count));
        }
    }

    private static void check(final String name, final Supplier<BaseContainerBlockEntity> factory) throws ReflectiveOperationException {
        final Random random = new Random(name.hashCode());
        int full = 0;
        for (int trial = 0; trial < TRIALS; ++trial) {
            final BaseContainerBlockEntity first = factory.get();
            final BaseContainerBlockEntity second = factory.get();
            fill(first, random);
            fill(second, random);
            assertEquals(vanillaFull(first), hopperFull(first), name + " single, trial " + trial);
            final CompoundContainer compound = new CompoundContainer(first, second);
            final boolean expected = vanillaFull(compound);
            assertEquals(expected, hopperFull(compound), name + " double, trial " + trial);
            full += expected ? 1 : 0;
        }
        if (full == 0 || full == TRIALS) {
            throw new AssertionError(name + " never covered both answers (" + full + " of " + TRIALS + " full)");
        }
    }

    @Test
    void chestsMatchTheSlotCheck() throws ReflectiveOperationException {
        check("chest", () -> new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
        check("trapped chest", () -> new TrappedChestBlockEntity(BlockPos.ZERO, Blocks.TRAPPED_CHEST.defaultBlockState()));
    }

    @Test
    void barrelsDispensersAndHoppersMatchTheSlotCheck() throws ReflectiveOperationException {
        check("barrel", () -> new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState()));
        check("dispenser", () -> new DispenserBlockEntity(BlockPos.ZERO, Blocks.DISPENSER.defaultBlockState()));
        check("dropper", () -> new DropperBlockEntity(BlockPos.ZERO, Blocks.DROPPER.defaultBlockState()));
        check("hopper", () -> new HopperBlockEntity(BlockPos.ZERO, Blocks.HOPPER.defaultBlockState()));
    }
}
