package io.canvasmc.canvas.world.chunk;

import java.util.Arrays;
import net.minecraft.util.RandomSource;

/**
 * The pick each section's next random tick and the chunk's next precipitation draw land on, so a tick in which nothing
 * lands costs a comparison instead of a draw per pick. Only the region owning the chunk touches it.
 */
public final class RandomTickSchedule {

    private static final int PICK_RANGE = 4096;
    private static final int PRECIPITATION_RANGE = 48;
    private static final int INDEX_BITS = 12;
    private static final int INDEX_MASK = (1 << INDEX_BITS) - 1;
    private static final long NEVER = Long.MAX_VALUE;
    private static final long STALE = Long.MIN_VALUE;
    private static final double[] LOG_MISS = new double[PICK_RANGE + 1];
    private static final double LOG_PRECIPITATION_MISS = Math.log1p(-1.0 / PRECIPITATION_RANGE);

    static {
        for (int ticking = 0; ticking <= PICK_RANGE; ticking++) {
            LOG_MISS[ticking] = Math.log1p(-(double) ticking / PICK_RANGE);
        }
    }

    private final short[] ticking;
    private final long[] nextLandings;
    private long picks;
    private long nextPrecipitation = STALE;
    private long nextEvent = STALE;
    private long tickStart;
    private long tickEnd;
    private int cursor;
    private int landedSection = -1;
    private long landedPick;

    public RandomTickSchedule(final int sections) {
        this.ticking = new short[sections];
        this.nextLandings = new long[sections];
        Arrays.fill(this.nextLandings, STALE);
    }

    public static int section(final int landing) {
        return landing >>> INDEX_BITS;
    }

    public static int index(final int landing) {
        return landing & INDEX_MASK;
    }

    public void count(final int section, final int ticking) {
        if (this.ticking[section] == ticking) {
            return;
        }

        this.ticking[section] = (short) ticking;
        this.nextLandings[section] = STALE;
        this.nextEvent = STALE;
    }

    public boolean idle(final int tickSpeed) {
        final long end = this.picks + tickSpeed;

        if (this.nextEvent < end) {
            return false;
        }

        this.picks = end;

        return true;
    }

    public int begin(final int tickSpeed, final RandomSource random) {
        this.tickStart = this.picks;
        this.tickEnd = this.picks + tickSpeed;
        this.picks = this.tickEnd;
        this.cursor = 0;
        this.landedSection = -1;

        long next = this.nextPrecipitation;

        if (next == STALE) {
            next = this.tickStart + misses(random, LOG_PRECIPITATION_MISS);
        }

        int landed = 0;

        while (next < this.tickEnd) {
            landed++;
            next += 1 + misses(random, LOG_PRECIPITATION_MISS);
        }

        this.nextPrecipitation = next;

        return landed;
    }

    /**
     * The next landing of the started tick, read with {@link #section(int)} and {@link #index(int)}, or {@code -1}
     * once it has none left.
     */
    public int nextLanding(final RandomSource random) {
        final long[] nextLandings = this.nextLandings;

        if (this.landedSection >= 0) {
            nextLandings[this.landedSection] = wait(this.landedPick + 1, this.ticking[this.landedSection], random);
            this.landedSection = -1;
        }

        for (int section = this.cursor; section < nextLandings.length; section++) {
            long next = nextLandings[section];

            if (next == STALE) {
                next = wait(this.tickStart, this.ticking[section], random);
                nextLandings[section] = next;
            }

            if (next < this.tickEnd) {
                this.cursor = section;
                this.landedSection = section;
                this.landedPick = next;

                return section << INDEX_BITS | uniformBelow(random, this.ticking[section]);
            }
        }

        this.cursor = nextLandings.length;
        this.nextEvent = this.earliest();

        return -1;
    }

    private long earliest() {
        long earliest = this.nextPrecipitation;

        for (final long next : this.nextLandings) {
            earliest = Math.min(earliest, next);
        }

        return earliest;
    }

    private static long wait(final long from, final int ticking, final RandomSource random) {
        return ticking == 0 ? NEVER : from + misses(random, LOG_MISS[ticking]);
    }

    private static long misses(final RandomSource random, final double logMiss) {
        return (long) (Math.log(1.0 - random.nextDouble()) / logMiss);
    }

    private static int uniformBelow(final RandomSource random, final int bound) {
        if (bound == 1) {
            return 0;
        }

        final int bits = Integer.SIZE - Integer.numberOfLeadingZeros(bound - 1);
        int value;

        do {
            value = (int) (random.nextLong() >>> (Long.SIZE - bits));
        } while (value >= bound);

        return value;
    }
}
