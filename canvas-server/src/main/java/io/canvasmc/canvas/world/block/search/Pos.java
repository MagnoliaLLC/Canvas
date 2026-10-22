package io.canvasmc.canvas.world.block.search;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * Original code from Lithium (https://github.com/CaffeineMC/lithium), by jcw780 and 2No2Name, licensed under
 * LGPL-3.0-only (https://www.gnu.org/licenses/lgpl-3.0.html).
 * <p>
 * <a
 * href="https://github.com/CaffeineMC/lithium/blob/11ea08adce6f94627d3eaaa65506a60e0451c8b6/common/src/main/java/net/caffeinemc/mods/lithium/common/util/Pos.java">Link
 * to file</a>
 */
public class Pos {

    public static class BlockCoord {
        public static int getYSize(LevelHeightAccessor view) {
            return view.getHeight();
        }
        public static int getMinY(LevelHeightAccessor view) {
            return view.getMinY();
        }
        public static int getMaxYInclusive(LevelHeightAccessor view) {
            return view.getMaxY();
        }
        public static int getMaxYExclusive(LevelHeightAccessor view) {
            return view.getMaxY() + 1;
        }

        public static int getMaxInSectionCoord(int sectionCoord) {
            return 15 + getMinInSectionCoord(sectionCoord);
        }

        public static int getMaxYInSectionIndex(LevelHeightAccessor view, int sectionIndex){
            return getMaxInSectionCoord(SectionYCoord.fromSectionIndex(view, sectionIndex));
        }

        public static int getMinInSectionCoord(int sectionCoord) {
            return SectionPos.sectionToBlockCoord(sectionCoord);
        }

        public static int getMinYInSectionIndex(LevelHeightAccessor view, int sectionIndex) {
            return getMinInSectionCoord(SectionYCoord.fromSectionIndex(view, sectionIndex));
        }
    }

    public static class ChunkCoord {
        public static int fromBlockCoord(int blockCoord) {
            return SectionPos.blockToSectionCoord(blockCoord);
        }

        public static int fromBlockSize(int i) {
            return i >> 4; //same method as fromBlockCoord, just be clear about coord/size semantic difference
        }
    }

    public static class SectionYCoord {
        public static int getNumYSections(LevelHeightAccessor view) {
            return view.getSectionsCount();
        }
        public static int getMinYSection(LevelHeightAccessor view) {
            return view.getMinSectionY();
        }
        public static int getMaxYSectionInclusive(LevelHeightAccessor view) {
            return view.getMaxSectionY();
        }
        public static int getMaxYSectionExclusive(LevelHeightAccessor view) {
            return view.getMaxSectionY() + 1;
        }

        public static int fromSectionIndex(LevelHeightAccessor view, int sectionCoord) {
            return sectionCoord + SectionYCoord.getMinYSection(view);
        }
        public static int fromBlockCoord(int blockCoord) {
            return SectionPos.blockToSectionCoord(blockCoord);
        }
    }

    public static class SectionYIndex {
        public static int getNumYSections(LevelHeightAccessor view) {
            return view.getSectionsCount();
        }
        public static int getMinYSectionIndex(LevelHeightAccessor view) {
            return 0;
        }
        public static int getMaxYSectionIndexInclusive(LevelHeightAccessor view) {
            return view.getSectionsCount() - 1;
        }
        public static int getMaxYSectionIndexExclusive(LevelHeightAccessor view) {
            return view.getSectionsCount();
        }


        public static int fromSectionCoord(LevelHeightAccessor view, int sectionCoord) {
            return sectionCoord - SectionYCoord.getMinYSection(view);
        }
        public static int fromBlockCoord(LevelHeightAccessor view, int blockCoord) {
            return fromSectionCoord(view, SectionPos.blockToSectionCoord(blockCoord));
        }
    }
}
