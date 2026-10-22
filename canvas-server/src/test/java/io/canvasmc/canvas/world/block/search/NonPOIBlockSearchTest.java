package io.canvasmc.canvas.world.block.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.VanillaFeature;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The section-skipping searches against copies of the searches they replace, on random worlds of a few chunks with
 * unloaded chunks, sections of every density and searches reaching past the build limits: the remove-block search must
 * find the block vanilla finds, and the repellent pre-filter may answer "nothing" only where the vanilla search finds
 * nothing.
 */
@VanillaFeature
public class NonPOIBlockSearchTest {

    private static final int MIN_Y = -16;
    private static final int HEIGHT = 48;
    private static final int CHUNK_RADIUS = 3;
    private static final int TRIALS = 600;
    private static final Block TARGET = Blocks.TURTLE_EGG;
    private static final List<BlockState> FILL = List.of(
        Blocks.STONE.defaultBlockState(),
        Blocks.DIRT.defaultBlockState(),
        Blocks.CAVE_AIR.defaultBlockState(),
        Blocks.AIR.defaultBlockState(),
        Blocks.TURTLE_EGG.defaultBlockState()
    );
    private static final BlockState REPELLENT_STATE = Blocks.SOUL_FIRE.defaultBlockState();
    private static final Predicate<BlockState> REPELLENT = state -> state.is(Blocks.SOUL_FIRE);

    private record World(LevelReader level, Long2ObjectOpenHashMap<ChunkAccess> loaded) {

        @Nullable ChunkAccess loadedChunk(final BlockPos pos) {
            return this.loaded.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        }
    }

    private static World world(final Random random, final PalettedContainerFactory factory) {
        final LevelHeightAccessor height = LevelHeightAccessor.create(MIN_Y, HEIGHT);
        final Long2ObjectOpenHashMap<ChunkAccess> all = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<ChunkAccess> loaded = new Long2ObjectOpenHashMap<>();
        final double unloadedChance = random.nextInt(3) == 0 ? 0.0 : 0.15;
        final double targetChance = random.nextInt(4) == 0 ? 0.0 : 0.02 + random.nextDouble() * 0.3;
        final double repellentChance = random.nextInt(3) == 0 ? 0.0 : random.nextDouble() * 0.02;
        for (int chunkX = -CHUNK_RADIUS; chunkX <= CHUNK_RADIUS; ++chunkX) {
            for (int chunkZ = -CHUNK_RADIUS; chunkZ <= CHUNK_RADIUS; ++chunkZ) {
                final ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, height, factory, null);
                fill(chunk, random, targetChance, repellentChance);
                all.put(chunk.coordinateKey, chunk);
                if (random.nextDouble() >= unloadedChance) {
                    loaded.put(chunk.coordinateKey, chunk);
                }
            }
        }
        return new World(level(height, all, loaded), loaded);
    }

    private static void fill(final ProtoChunk chunk, final Random random, final double targetChance, final double repellentChance) {
        for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); ++sectionIndex) {
            final int mode = random.nextInt(6);
            if (mode <= 1) {
                continue;
            }
            final int writes = mode == 5 ? 0 : 1 + random.nextInt(mode * 40);
            for (int write = 0; write < writes; ++write) {
                set(chunk, sectionIndex, random.nextInt(16), random.nextInt(16), random.nextInt(16), random, targetChance, repellentChance);
            }
            if (mode == 5) {
                final int fromY = random.nextInt(16);
                final int toY = Math.min(15, fromY + random.nextInt(3));
                for (int y = fromY; y <= toY; ++y) {
                    for (int z = 0; z < 16; ++z) {
                        for (int x = 0; x < 16; ++x) {
                            set(chunk, sectionIndex, x, y, z, random, targetChance, repellentChance);
                        }
                    }
                }
            }
        }
    }

    private static void set(
        final ProtoChunk chunk,
        final int sectionIndex,
        final int x,
        final int y,
        final int z,
        final Random random,
        final double targetChance,
        final double repellentChance
    ) {
        final double roll = random.nextDouble();
        final BlockState state = roll < targetChance ? TARGET.defaultBlockState()
            : roll < targetChance + repellentChance ? REPELLENT_STATE
            : FILL.get(random.nextInt(FILL.size()));
        chunk.getSection(sectionIndex).setBlockState(x, y, z, state);
    }

    private static LevelReader level(
        final LevelHeightAccessor height,
        final Long2ObjectOpenHashMap<ChunkAccess> all,
        final Long2ObjectOpenHashMap<ChunkAccess> loaded
    ) {
        final InvocationHandler handler = (proxy, method, args) -> {
            final int arguments = args == null ? 0 : args.length;
            switch (method.getName()) {
                case "getMinY" -> {
                    if (arguments == 0) {
                        return height.getMinY();
                    }
                }
                case "getHeight" -> {
                    if (arguments == 0) {
                        return height.getHeight();
                    }
                }
                case "getChunkIfLoadedImmediately" -> {
                    if (arguments == 2 && args[0] instanceof Integer x && args[1] instanceof Integer z) {
                        return loaded.get(ChunkPos.pack(x, z));
                    }
                }
                case "hasChunk" -> {
                    if (arguments == 2 && args[0] instanceof Integer x && args[1] instanceof Integer z) {
                        return loaded.containsKey(ChunkPos.pack(x, z));
                    }
                }
                case "getChunk" -> {
                    if (arguments == 4 && args[0] instanceof Integer x && args[1] instanceof Integer z && args[3] instanceof Boolean load) {
                        return load ? all.get(ChunkPos.pack(x, z)) : loaded.get(ChunkPos.pack(x, z));
                    }
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "toString" -> {
                    return "test level";
                }
                default -> {
                }
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class}, handler);
    }

    /**
     * {@code MoveToBlockGoal#findNearestBlock} with {@code RemoveBlockGoal#isValidTarget}, as Canvas runs them: the
     * loaded check ({@code hasChunkAt}) and the target check's chunk are the loaded chunk lookup.
     */
    private static @Nullable BlockPos vanillaFindNearestBlock(
        final World world,
        final BlockPos mobPos,
        final int searchRange,
        final int verticalSearchStart,
        final int verticalSearchRange,
        final Predicate<BlockPos> withinHome
    ) {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = verticalSearchStart; y <= verticalSearchRange; y = y > 0 ? -y : 1 - y) {
            for (int r = 0; r < searchRange; r++) {
                for (int x = 0; x <= r; x = x > 0 ? -x : 1 - x) {
                    for (int z = x < r && x > -r ? r : 0; z <= r; z = z > 0 ? -z : 1 - z) {
                        pos.setWithOffset(mobPos, x, y - 1, z);
                        if (world.loadedChunk(pos) == null) {
                            continue;
                        }
                        if (withinHome.test(pos) && vanillaIsValidTarget(world, pos)) {
                            return pos.immutable();
                        }
                    }
                }
            }
        }

        return null;
    }

    private static boolean vanillaIsValidTarget(final World world, final BlockPos pos) {
        final ChunkAccess chunk = world.loadedChunk(pos);
        return chunk != null
            && chunk.getBlockState(pos).is(TARGET)
            && chunk.getBlockState(pos.above()).isAir()
            && chunk.getBlockState(pos.above(2)).isAir();
    }

    private static boolean isValidTargetAbove(final ChunkAccess chunkAccess, final BlockPos.MutableBlockPos mutable) {
        return chunkAccess.getBlockState(mutable.move(0, 1, 0)).isAir()
            && chunkAccess.getBlockState(mutable.move(0, 1, 0)).isAir();
    }

    private static BlockPos randomMobPos(final Random random) {
        return new BlockPos(
            random.nextInt(48) - 16,
            MIN_Y - 4 + random.nextInt(HEIGHT + 8),
            random.nextInt(48) - 16
        );
    }

    @Test
    void removeBlockSearchFindsTheVanillaTarget() {
        final PalettedContainerFactory factory = PalettedContainerFactory.create(RegistryHelper.registryAccess());
        final Random random = new Random(0x5EA4C4L);
        final BiPredicate<ChunkAccess, BlockPos.MutableBlockPos> isValidTarget = NonPOIBlockSearchTest::isValidTargetAbove;
        int found = 0;
        int searches = 0;

        for (int trial = 0; trial < TRIALS; ++trial) {
            final World world = world(random, factory);
            for (int search = 0; search < 4; ++search) {
                final BlockPos mobPos = randomMobPos(random);
                final int searchRange = random.nextInt(3) == 0 ? 1 + random.nextInt(24) : 24;
                final int verticalSearchStart = random.nextInt(5) == 0 ? -random.nextInt(4) : 0;
                final int verticalSearchRange = random.nextInt(6);
                final Predicate<BlockPos> withinHome;
                if (random.nextBoolean()) {
                    withinHome = pos -> true;
                } else {
                    final BlockPos home = mobPos.offset(random.nextInt(21) - 10, random.nextInt(7) - 3, random.nextInt(21) - 10);
                    final int homeRadius = 1 + random.nextInt(20);
                    withinHome = pos -> home.distSqr(pos) < homeRadius * homeRadius;
                }

                final BlockPos expected = vanillaFindNearestBlock(world, mobPos, searchRange, verticalSearchStart, verticalSearchRange, withinHome);
                final BlockPos.MutableBlockPos actual = MoveToBlockGoalSearch.findNearestBlock(
                    world.level(),
                    mobPos.below(),
                    searchRange,
                    verticalSearchStart,
                    verticalSearchRange,
                    withinHome,
                    state -> state.is(TARGET),
                    isValidTarget
                );
                assertEquals(expected, actual == null ? null : actual.immutable(), "trial " + trial + ", search " + search + " from " + mobPos);
                found += expected == null ? 0 : 1;
                ++searches;
            }
        }

        assertTrue(found > searches / 10 && found < searches - searches / 10, "searches found " + found + " of " + searches);
    }

    @Test
    void repellentPreFilterAnswersNothingOnlyWhenVanillaFindsNothing() {
        final PalettedContainerFactory factory = PalettedContainerFactory.create(RegistryHelper.registryAccess());
        final Random random = new Random(0x4E9E1L);
        int skipped = 0;
        int searches = 0;

        for (int trial = 0; trial < TRIALS; ++trial) {
            final World world = world(random, factory);
            for (int search = 0; search < 4; ++search) {
                final BlockPos origin = randomMobPos(random);
                final boolean noMatch = CommonBlockSearchesCheckAndCache.hasNoMatch(world.level(), origin, 8, 4, REPELLENT, true);
                if (noMatch) {
                    final Optional<BlockPos> vanilla = world.level().findBlocksInBoxByManhattanDistance(origin, 8, 4).filterState(REPELLENT).findFirst();
                    assertEquals(Optional.empty(), vanilla, "trial " + trial + ", search " + search + " from " + origin);
                    ++skipped;
                }
                ++searches;
            }
        }

        assertTrue(skipped > searches / 20, "the pre-filter answered nothing in " + skipped + " of " + searches);
    }
}
