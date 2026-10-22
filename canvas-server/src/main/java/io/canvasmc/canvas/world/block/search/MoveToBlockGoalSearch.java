package io.canvasmc.canvas.world.block.search;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

/**
 * MoveToBlockGoal's block search; it never loads a chunk, as the goal's target check reads only loaded chunks.
 * <p>
 * Original code from Lithium (https://github.com/CaffeineMC/lithium), by jcw780 and 2No2Name, licensed under
 * LGPL-3.0-only (https://www.gnu.org/licenses/lgpl-3.0.html).
 * <p>
 * <a
 * href="https://github.com/CaffeineMC/lithium/blob/11ea08adce6f94627d3eaaa65506a60e0451c8b6/common/src/main/java/net/caffeinemc/mods/lithium/mixin/ai/non_poi_block_search/MoveToBlockGoalMixin.java">Link
 * to file</a>
 */
public final class MoveToBlockGoalSearch {

    private MoveToBlockGoalSearch() {
    }

    public static BlockPos.@Nullable MutableBlockPos findNearestBlock(
        final LevelReader level,
        final BlockPos center,
        final int searchRange,
        final int verticalSearchStart,
        final int verticalSearchRange,
        final Predicate<BlockPos> withinHome,
        final Predicate<BlockState> requiredBlock,
        final BiPredicate<ChunkAccess, BlockPos.MutableBlockPos> isValidTarget
    ) {
        final CheckAndCacheBlockChecker checker = new CheckAndCacheBlockChecker(
            center, searchRange - 1, Math.max(verticalSearchRange, Math.abs(verticalSearchStart)), level, requiredBlock, false
        );
        final LongArrayList sortedChunksMaybeWithBlock = new LongArrayList(checker.getChunkSize());
        checker.initializeChunks(sortedChunksMaybeWithBlock::add);

        if (checker.shouldStop()) {
            return null;
        }

        final int minY = level.getMinY();
        final int maxY = level.getMaxY();

        sortedChunksMaybeWithBlock.sort(
            (final long chunkLong0, final long chunkLong1) -> NonPOISearchDistances.MoveToBlockGoalDistances.getMinimumSortOrderOfChunk(center, chunkLong0)
                - NonPOISearchDistances.MoveToBlockGoalDistances.getMinimumSortOrderOfChunk(center, chunkLong1)
        );

        final int minSectionY = checker.minSectionY;

        final BlockPos.MutableBlockPos foundPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos currentPos = new BlockPos.MutableBlockPos();

        for (int layer = verticalSearchStart; layer <= verticalSearchRange; layer = layer > 0 ? -layer : 1 - layer) {
            final int y = center.getY() + layer;

            if (y < minY || y > maxY) {
                continue;
            }

            final int chunkY = SectionPos.blockToSectionCoord(y);
            final int ySectionIndex = chunkY - minSectionY;

            int closestFound = Integer.MAX_VALUE;
            int ringMax = searchRange - 1;

            for (int i = 0, size = sortedChunksMaybeWithBlock.size(); i < size; i++) {
                final long chunkPos = sortedChunksMaybeWithBlock.getLong(i);
                final int chunkX = ChunkPos.getX(chunkPos);
                final int chunkZ = ChunkPos.getZ(chunkPos);

                if (closestFound < NonPOISearchDistances.MoveToBlockGoalDistances.getMinimumSortOrderOfChunk(center, chunkX, chunkZ)) {
                    break;
                }

                if (!checker.checkCachedSection(chunkX, chunkY, chunkZ)) {
                    continue;
                }

                final ChunkAccess chunkAccess = checker.getCachedChunkAccess(chunkPos);
                final int chunkBlockX = SectionPos.sectionToBlockCoord(chunkX);
                int xMin = Math.max(center.getX() - ringMax, chunkBlockX);
                int xMax = Math.min(center.getX() + ringMax, chunkBlockX + 15);
                final int chunkBlockZ = SectionPos.sectionToBlockCoord(chunkZ);
                final int zMin = Math.max(center.getZ() - ringMax, chunkBlockZ);
                int zMax = Math.min(center.getZ() + ringMax, chunkBlockZ + 15);
                final LevelChunkSection levelChunkSection = chunkAccess.getSections()[ySectionIndex];
                for (int z = zMin; z <= zMax; z++) {
                    for (int x = xMin; x <= xMax; x++) {
                        final int dX = x - center.getX();
                        final int dZ = z - center.getZ();
                        final int ring = NonPOISearchDistances.MoveToBlockGoalDistances.getRing(dX, dZ);
                        final int currentDistance = NonPOISearchDistances.MoveToBlockGoalDistances.getVanillaSortOrderInt(ring, dX, dZ);
                        if (currentDistance < closestFound
                            && withinHome.test(currentPos.set(x, y, z))
                            && requiredBlock.test(levelChunkSection.getBlockState(x & 15, y & 15, z & 15))
                            && isValidTarget.test(chunkAccess, currentPos)) {
                            ringMax = ring;
                            xMin = Math.max(center.getX() - ringMax, chunkBlockX);
                            xMax = Math.min(center.getX() + ringMax, chunkBlockX + 15);
                            zMax = Math.min(center.getZ() + ringMax, chunkBlockZ + 15);
                            foundPos.set(x, y, z);
                            closestFound = currentDistance;
                        }
                    }
                }
            }

            if (closestFound < Integer.MAX_VALUE) {
                return foundPos;
            }
        }

        return null;
    }
}
