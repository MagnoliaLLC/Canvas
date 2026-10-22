package io.canvasmc.canvas.world.chunk;

import io.canvasmc.canvas.util.FasterRandomSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schedule against the distribution of drawing every pick: each section's {@code tickSpeed} picks landing with
 * probability {@code ticking / 4096} at the count each pick sees, the chunk's precipitation draws landing with
 * probability {@code 1 / 48}. Seeds are fixed, so a run passes or fails the same way every time; the statistical
 * bounds sit near a one in a million chance of a correct schedule failing them.
 */
@Tag("World")
class RandomTickScheduleTest {

    private static final int TICK_SPEED = 3;
    private static final int PICK_RANGE = 4096;
    private static final double CRITICAL_Z = 4.75;

    @Test
    void fixedCountsLandAsEveryPickWould() {
        final int[] counts = {0, 1, 37, 300, 2048, 4095};
        final Chunk chunk = new Chunk(counts);
        final RandomSource random = new FasterRandomSource(1L);
        final int ticks = 1_000_000;
        final long[][] perSection = new long[counts.length][TICK_SPEED + 1];
        final long[] totals = new long[counts.length * TICK_SPEED + 1];
        final long[] precipitation = new long[TICK_SPEED + 1];
        final long[] indexes37 = new long[37];
        final long[] indexes2048 = new long[16];
        final int[] landed = new int[counts.length];

        for (int tick = 0; tick < ticks; tick++) {
            final int precipitations = chunk.tick(random, TICK_SPEED, landed, (section, index) -> {
                if (section == 2) {
                    indexes37[index]++;
                } else if (section == 4) {
                    indexes2048[index * 16 / 2048]++;
                }
            });
            precipitation[precipitations]++;
            int total = 0;

            for (int section = 0; section < counts.length; section++) {
                perSection[section][landed[section]]++;
                total += landed[section];
            }

            totals[total]++;
        }

        double[] totalExpected = {1.0};

        for (int section = 0; section < counts.length; section++) {
            final double[] expected = binomial(TICK_SPEED, (double) counts[section] / PICK_RANGE);
            assertChiSquare("landings per tick of section " + section, perSection[section], expected);
            totalExpected = convolve(totalExpected, expected);
        }

        assertChiSquare("landings per tick of the chunk", totals, totalExpected);
        assertChiSquare("precipitation per tick", precipitation, binomial(TICK_SPEED, 1.0 / 48));
        assertChiSquare("index landed in a section of 37", indexes37, uniform(37));
        assertChiSquare("index landed in a section of 2048", indexes2048, uniform(16));
    }

    @Test
    void everyPickOfAFullSectionLands() {
        final Chunk chunk = new Chunk(4096, 0, 4096);
        final RandomSource random = new FasterRandomSource(2L);
        final int[] landed = new int[3];

        for (int tick = 0; tick < 1000; tick++) {
            chunk.tick(random, TICK_SPEED, landed, (section, index) -> {
            });
            assertEquals(TICK_SPEED, landed[0]);
            assertEquals(0, landed[1]);
            assertEquals(TICK_SPEED, landed[2]);
        }
    }

    @Test
    void aCountAnEarlierSectionMovesAppliesToTheTicksLaterPicks() {
        final Chunk chunk = new Chunk(4096, 0);
        final RandomSource random = new FasterRandomSource(3L);
        final int[] landed = new int[2];
        final long[] later = new long[TICK_SPEED + 1];

        for (int tick = 0; tick < 200_000; tick++) {
            chunk.tick(random, TICK_SPEED, landed, (section, index) -> {
                if (section == 0) {
                    chunk.set(1, 1024);
                }
            });
            later[landed[1]]++;
            chunk.set(1, 0);
        }

        assertChiSquare("landings of a section an earlier one filled", later, binomial(TICK_SPEED, 0.25));
    }

    @Test
    void aCountALaterSectionMovesWaitsForTheNextTick() {
        final Chunk chunk = new Chunk(0, 4096);
        final RandomSource random = new FasterRandomSource(4L);
        final int[] landed = new int[2];

        chunk.tick(random, TICK_SPEED, landed, (section, index) -> {
            if (section == 1) {
                chunk.set(0, 4096);
            }
        });
        assertEquals(0, landed[0], "a section already passed this tick landed in it");
        assertEquals(TICK_SPEED, landed[1]);

        chunk.tick(random, TICK_SPEED, landed, (section, index) -> {
        });
        assertEquals(TICK_SPEED, landed[0], "the section's next tick did not use its new count");
    }

    @Test
    void aCountASectionsOwnLandingMovesAppliesToItsLaterPicks() {
        final Chunk chunk = new Chunk(2048);
        final RandomSource random = new FasterRandomSource(5L);
        final int[] landed = new int[1];
        final long[] filled = new long[TICK_SPEED + 1];

        for (int tick = 0; tick < 200_000; tick++) {
            chunk.tick(random, TICK_SPEED, landed, (section, index) -> chunk.set(0, 4096));
            filled[landed[0]]++;
            chunk.set(0, 2048);
        }

        assertChiSquare("landings of a section its first landing filled", filled, new double[]{0.125, 0.125, 0.25, 0.5});

        final long[] emptied = new long[TICK_SPEED + 1];
        chunk.set(0, 1024);

        for (int tick = 0; tick < 200_000; tick++) {
            chunk.tick(random, TICK_SPEED, landed, (section, index) -> chunk.set(0, 0));
            emptied[landed[0]]++;
            chunk.set(0, 1024);
        }

        final double none = Math.pow(0.75, TICK_SPEED);
        assertChiSquare("landings of a section its first landing emptied", emptied, new double[]{none, 1 - none, 0, 0});
    }

    @Test
    void noPicksLandNothing() {
        final Chunk chunk = new Chunk(4096, 4096);
        final RandomSource random = new FasterRandomSource(6L);
        final int[] landed = new int[2];

        for (int tick = 0; tick < 10_000; tick++) {
            assertEquals(0, chunk.tick(random, 0, landed, (section, index) -> {
            }));
            assertEquals(0, landed[0] + landed[1]);
        }
    }

    @Test
    void matchesThePerPickLoopWhileRandomTicksMoveCounts() {
        final int sections = 8;
        final int ticks = 400_000;
        final int batches = 100;
        final double[] scheduleMeans = new double[batches];
        final double[] referenceMeans = new double[batches];
        final double[] scheduleCounts = new double[batches];
        final double[] referenceCounts = new double[batches];

        final Chunk chunk = new Chunk(new int[sections]);
        final RandomSource random = new FasterRandomSource(7L);
        final RandomSource rules = new FasterRandomSource(8L);
        final int[] landed = new int[sections];
        final int[] reference = new int[sections];
        final RandomSource referenceRandom = new FasterRandomSource(9L);
        final RandomSource referenceRules = new FasterRandomSource(10L);

        for (int tick = 0; tick < ticks; tick++) {
            final int batch = tick * batches / ticks;
            drift(rules, chunk::set, sections);
            chunk.tick(random, TICK_SPEED, landed, (section, index) -> move(rules, chunk::set, chunk.ticking, sections));
            scheduleMeans[batch] += Arrays.stream(landed).sum();
            scheduleCounts[batch] += Arrays.stream(chunk.ticking).sum();

            drift(referenceRules, (section, count) -> reference[section] = count, sections);
            referenceMeans[batch] += perPickTick(reference, referenceRandom, TICK_SPEED,
                () -> move(referenceRules, (section, count) -> reference[section] = count, reference, sections));
            referenceCounts[batch] += Arrays.stream(reference).sum();
        }

        assertSameMean("landings per tick", scheduleMeans, referenceMeans);
        assertSameMean("ticking blocks in the chunk", scheduleCounts, referenceCounts);
    }

    /**
     * The loop the schedule replaces: every pick a 12-bit draw, landing when below the count the section holds as the
     * pick is made, a section without ticking blocks passed over when reached.
     */
    private static int perPickTick(final int[] ticking, final RandomSource random, final int tickSpeed, final Runnable onLanding) {
        for (int i = 0; i < tickSpeed; i++) {
            random.nextInt(48);
        }

        int landed = 0;

        for (int section = 0; section < ticking.length; section++) {
            if (ticking[section] == 0) {
                continue;
            }

            for (int i = 0; i < tickSpeed; i++) {
                final int pick = (int) (random.nextLong() >>> 52);

                if (pick >= ticking[section]) {
                    continue;
                }

                landed++;
                onLanding.run();
            }
        }

        return landed;
    }

    /**
     * Between ticks, now and then a section's count is set anew, as players and neighbours build.
     */
    private static void drift(final RandomSource rules, final CountSetter set, final int sections) {
        if (rules.nextInt(50) == 0) {
            set.set(rules.nextInt(sections), rules.nextInt(PICK_RANGE + 1));
        }
    }

    /**
     * Half the random ticks move some section's count a little, as grass spreads and crops grow.
     */
    private static void move(final RandomSource rules, final CountSetter set, final int[] ticking, final int sections) {
        if (rules.nextBoolean()) {
            final int section = rules.nextInt(sections);
            final int step = rules.nextInt(64) * (rules.nextBoolean() ? 1 : -1);
            set.set(section, Math.clamp(ticking[section] + step, 0, PICK_RANGE));
        }
    }

    private static double[] binomial(final int n, final double p) {
        final double[] probabilities = new double[n + 1];

        for (int k = 0; k <= n; k++) {
            probabilities[k] = choose(n, k) * Math.pow(p, k) * Math.pow(1 - p, n - k);
        }

        return probabilities;
    }

    private static double choose(final int n, final int k) {
        double result = 1;

        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }

        return result;
    }

    private static double[] convolve(final double[] left, final double[] right) {
        final double[] result = new double[left.length + right.length - 1];

        for (int i = 0; i < left.length; i++) {
            for (int j = 0; j < right.length; j++) {
                result[i + j] += left[i] * right[j];
            }
        }

        return result;
    }

    private static double[] uniform(final int bins) {
        final double[] probabilities = new double[bins];
        Arrays.fill(probabilities, 1.0 / bins);

        return probabilities;
    }

    /**
     * Pearson's test of observed counts against probabilities, bins expecting fewer than five merged into the next, at
     * the Wilson-Hilferty approximation of the chi-square bound for one in a million.
     */
    private static void assertChiSquare(final String what, final long[] observed, final double[] probabilities) {
        final long total = Arrays.stream(observed).sum();
        final List<double[]> bins = new ArrayList<>();
        double pendingObserved = 0;
        double pendingExpected = 0;

        for (int i = 0; i < probabilities.length; i++) {
            pendingObserved += i < observed.length ? observed[i] : 0;
            pendingExpected += probabilities[i] * total;

            if (pendingExpected >= 5) {
                bins.add(new double[]{pendingObserved, pendingExpected});
                pendingObserved = 0;
                pendingExpected = 0;
            }
        }

        for (int i = probabilities.length; i < observed.length; i++) {
            pendingObserved += observed[i];
        }

        assertTrue(pendingExpected > 0 || pendingObserved == 0, what + ": landed where it never may, " + Arrays.toString(observed));

        if (pendingExpected > 0) {
            if (bins.isEmpty()) {
                bins.add(new double[]{pendingObserved, pendingExpected});
            } else {
                final double[] last = bins.getLast();
                last[0] += pendingObserved;
                last[1] += pendingExpected;
            }
        }

        if (bins.size() == 1) {
            assertEquals(bins.getFirst()[1], bins.getFirst()[0], 1e-6, what);

            return;
        }

        double statistic = 0;

        for (final double[] bin : bins) {
            statistic += (bin[0] - bin[1]) * (bin[0] - bin[1]) / bin[1];
        }

        final int degrees = bins.size() - 1;
        final double shape = 2.0 / (9 * degrees);
        final double bound = degrees * Math.pow(1 - shape + CRITICAL_Z * Math.sqrt(shape), 3);
        assertTrue(statistic < bound, what + ": chi-square " + statistic + " over " + degrees + " degrees, bound " + bound + ", observed " + Arrays.toString(observed));
    }

    /**
     * Two runs' means within five standard errors of each other, the error taken from batch means since a count moved
     * by one tick carries into the next.
     */
    private static void assertSameMean(final String what, final double[] left, final double[] right) {
        final double leftMean = Arrays.stream(left).average().orElseThrow();
        final double rightMean = Arrays.stream(right).average().orElseThrow();
        final double error = Math.sqrt(variance(left, leftMean) / left.length + variance(right, rightMean) / right.length);
        assertTrue(Math.abs(leftMean - rightMean) < 5 * error, what + ": schedule " + leftMean + " against per-pick " + rightMean + ", standard error " + error);
    }

    private static double variance(final double[] values, final double mean) {
        return Arrays.stream(values).map(value -> (value - mean) * (value - mean)).sum() / (values.length - 1);
    }

    @FunctionalInterface
    private interface CountSetter {
        void set(int section, int count);
    }

    @FunctionalInterface
    private interface Landing {
        void landed(int section, int index);
    }

    /**
     * A chunk whose sections' ticking counts a test moves, the schedule told of every move as a block write tells it.
     */
    private static final class Chunk {
        private final int[] ticking;
        private final RandomTickSchedule schedule;

        private Chunk(final int... ticking) {
            this.ticking = ticking.clone();
            this.schedule = new RandomTickSchedule(ticking.length);

            for (int section = 0; section < ticking.length; section++) {
                this.schedule.count(section, ticking[section]);
            }
        }

        private void set(final int section, final int count) {
            this.ticking[section] = count;
            this.schedule.count(section, count);
        }

        /**
         * Runs one tick as the level does, answering its precipitation landings and filling each section's random
         * tick landings.
         */
        private int tick(final RandomSource random, final int tickSpeed, final int[] landed, final Landing onLanding) {
            Arrays.fill(landed, 0);

            if (this.schedule.idle(tickSpeed)) {
                return 0;
            }

            final int precipitations = this.schedule.begin(tickSpeed, random);

            for (int landing = this.schedule.nextLanding(random); landing >= 0; landing = this.schedule.nextLanding(random)) {
                final int section = RandomTickSchedule.section(landing);
                final int index = RandomTickSchedule.index(landing);
                assertTrue(index < this.ticking[section], "landed past the section's ticking blocks");
                landed[section]++;
                onLanding.landed(section, index);
            }

            return precipitations;
        }
    }
}
