package io.canvasmc.canvas.world.chunk;

import java.util.Arrays;
import net.minecraft.util.RandomSource;

/**
 * When the random ticks and precipitation draws of one chunk land, kept as the pick each next lands on, so a tick in
 * which nothing lands costs a comparison instead of a draw per pick.
 * <p>
 * Every tick gives each section {@code tickSpeed} picks, each landing on one of its ticking blocks with probability
 * {@code ticking / 4096}, and the chunk {@code tickSpeed} precipitation draws, each landing with probability
 * {@code 1 / 48}. The misses before the next landing are drawn from the geometric distribution such picks make, and a
 * section whose ticking count moved draws its wait again from its next unspent pick: misses carry no memory, so the
 * landings keep the distribution of drawing every pick, sections take their picks in order within a tick, and a count
 * moved by a random tick applies to the picks after it, as when every pick read the count.
 * <p>
 * Only the region owning the chunk touches its schedule.
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

    /**
     * The section of a landing {@link #nextLanding(RandomSource)} answered.
     */
    public static int section(final int landing) {
        return landing >>> INDEX_BITS;
    }

    /**
     * The index into the section's ticking block list of a landing {@link #nextLanding(RandomSource)} answered.
     */
    public static int index(final int landing) {
        return landing & INDEX_MASK;
    }

    /**
     * Records a section's ticking block count, called on every block write in the section; a count that moved has its
     * section's wait drawn again from the section's next unspent pick.
     */
    public void count(final int section, final int ticking) {
        if (this.ticking[section] == ticking) {
            return;
        }

        this.ticking[section] = (short) ticking;
        this.nextLandings[section] = STALE;
        this.nextEvent = STALE;
    }

    /**
     * Spends a tick of {@code tickSpeed} picks in which nothing lands, answering {@code false} without spending it when
     * something might, for {@link #begin(int, RandomSource)} to run.
     */
    public boolean idle(final int tickSpeed) {
        final long end = this.picks + tickSpeed;

        if (this.nextEvent < end) {
            return false;
        }

        this.picks = end;

        return true;
    }

    /**
     * Starts a tick of {@code tickSpeed} picks, answering how many of its precipitation draws land; its random ticks
     * follow from {@link #nextLanding(RandomSource)}.
     */
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
     * The next random tick of the tick {@link #begin(int, RandomSource)} started, as a section and an index into its
     * ticking block list ({@link #section(int)}, {@link #index(int)}), or {@code -1} once the tick has none left. The
     * caller ticks each landing before asking for the next, so a count the tick moved applies to the picks after it.
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

    /**
     * The misses before a landing whose miss probability has the natural logarithm {@code logMiss}: the inverse of the
     * geometric distribution's tail, {@code P(misses >= n) = (1 - p)^n}, at a uniform draw in (0, 1].
     */
    private static long misses(final RandomSource random, final double logMiss) {
        return (long) (Math.log(1.0 - random.nextDouble()) / logMiss);
    }

    /**
     * A uniform draw below {@code bound}, taken from the high bits of a draw and redrawn past the bound, so no index is
     * favored.
     */
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
