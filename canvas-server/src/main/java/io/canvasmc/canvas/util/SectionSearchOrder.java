package io.canvasmc.canvas.util;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The order in which a breadth-first walk from a center section reaches every section of a box, enqueuing
 * neighbors as -z, -x, -y, +y, +x, +z, grouped by walk distance.
 */
public final class SectionSearchOrder {

    private static final int MAX_CACHED_VOLUME = 16384;
    private static final int MAX_CACHED_ORDERS = 256;
    private static final ConcurrentHashMap<Box, SectionSearchOrder> CACHE = new ConcurrentHashMap<>();

    private final long[] sections;
    private final int[] layerStarts;

    private SectionSearchOrder(final long[] sections, final int[] layerStarts) {
        this.sections = sections;
        this.layerStarts = layerStarts;
    }

    public static SectionSearchOrder of(final int lengthX, final int lengthY, final int lengthZ,
                                        final int centerX, final int centerY, final int centerZ) {
        final Box box = new Box(lengthX, lengthY, lengthZ, centerX, centerY, centerZ);

        if ((long) lengthX * lengthY * lengthZ > MAX_CACHED_VOLUME) {
            return walk(box);
        }

        final SectionSearchOrder cached = CACHE.get(box);

        if (cached != null) {
            return cached;
        }

        final SectionSearchOrder walked = walk(box);

        if (CACHE.size() < MAX_CACHED_ORDERS) {
            CACHE.putIfAbsent(box, walked);
        }

        return walked;
    }

    public static int x(final long section) {
        return (int) (section >>> 40);
    }

    public static int y(final long section) {
        return (int) (section & 0xFFFFFL);
    }

    public static int z(final long section) {
        return (int) ((section >>> 20) & 0xFFFFFL);
    }

    /**
     * A lower bound on the squared block distance from a source inside the center section (clamped on y)
     * to any section at this walk distance or beyond.
     */
    public static double minDistanceSquared(final int layer) {
        if (layer <= 4) {
            return 0.0;
        }

        final double gaps = 16.0 * (layer - 4);

        return gaps * gaps / 3.0;
    }

    public long[] sections() {
        return this.sections;
    }

    public int layers() {
        return this.layerStarts.length - 1;
    }

    public int layerStart(final int layer) {
        return this.layerStarts[layer];
    }

    private static long pack(final int x, final int y, final int z) {
        return ((long) x << 40) | ((long) z << 20) | y;
    }

    private static SectionSearchOrder walk(final Box box) {
        final int lengthX = box.lengthX();
        final int lengthY = box.lengthY();
        final int lengthZ = box.lengthZ();
        final long[] sections = new long[lengthX * lengthY * lengthZ];
        final boolean[] seen = new boolean[sections.length];
        final int[] layerStarts = new int[lengthX + lengthY + lengthZ];
        int layers = 0;
        int tail = 0;

        sections[tail++] = pack(box.centerX(), box.centerY(), box.centerZ());
        seen[(box.centerX() * lengthY + box.centerY()) * lengthZ + box.centerZ()] = true;

        for (int start = 0, end = tail; start < end; start = end, end = tail) {
            layerStarts[layers++] = start;

            for (int i = start; i < end; ++i) {
                final int x = x(sections[i]);
                final int y = y(sections[i]);
                final int z = z(sections[i]);

                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        for (int dy = -1; dy <= 1; ++dy) {
                            if ((dx & 1) + (dy & 1) + (dz & 1) != 1) {
                                continue;
                            }

                            final int neighborX = x + dx;
                            final int neighborY = y + dy;
                            final int neighborZ = z + dz;

                            if (neighborX < 0 || neighborX >= lengthX || neighborY < 0 || neighborY >= lengthY || neighborZ < 0 || neighborZ >= lengthZ) {
                                continue;
                            }

                            final int index = (neighborX * lengthY + neighborY) * lengthZ + neighborZ;

                            if (!seen[index]) {
                                seen[index] = true;
                                sections[tail++] = pack(neighborX, neighborY, neighborZ);
                            }
                        }
                    }
                }
            }
        }

        layerStarts[layers] = tail;

        return new SectionSearchOrder(sections, Arrays.copyOf(layerStarts, layers + 1));
    }

    private record Box(int lengthX, int lengthY, int lengthZ, int centerX, int centerY, int centerZ) {
    }
}
