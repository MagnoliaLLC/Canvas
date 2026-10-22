package io.canvasmc.canvas.world.block.search;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses CheckAndCacheBlockChecker to improve common block searches
 * <p>
 * Original code from Lithium (https://github.com/CaffeineMC/lithium), by jcw780 and 2No2Name, licensed under
 * LGPL-3.0-only (https://www.gnu.org/licenses/lgpl-3.0.html).
 * <p>
 * <a
 * href="https://github.com/CaffeineMC/lithium/blob/11ea08adce6f94627d3eaaa65506a60e0451c8b6/common/src/main/java/net/caffeinemc/mods/lithium/common/ai/non_poi_block_search/CommonBlockSearchesCheckAndCache.java">Link
 * to file</a>
 */
public class CommonBlockSearchesCheckAndCache {
    /**
     * Optimizes BlockPos::findClosestMatch
     * [Vanilla Copy] search order and chunk-loading - even though the latter is unlikely to be observable in vanilla.
     * <p>
     * Answers whether no block within {@code horizontalRange} and {@code verticalRange} of {@code origin} can match
     * {@code blockStatePredicate}: every chunk in range is loaded and none of their sections in range may hold a
     * matching state. With {@code shouldChunkLoad}, an unloaded chunk counts as a possible match, so the caller runs
     * its own search, loading chunks as it always did.
     */
    public static boolean hasNoMatch(LevelReader levelReader, BlockPos origin,
                                     int horizontalRange, int verticalRange,
                                     Predicate<BlockState> blockStatePredicate,
                                     boolean shouldChunkLoad) {
        CheckAndCacheBlockChecker checker = new CheckAndCacheBlockChecker(
                origin, horizontalRange, verticalRange, levelReader, blockStatePredicate, shouldChunkLoad);
        checker.initializeChunks();
        return checker.shouldStop();
    }
}
