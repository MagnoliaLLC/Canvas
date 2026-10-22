package io.canvasmc.canvas.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The whole-word VarInt and VarLong writers against the vanilla loop writing one byte per seven bits: every edge
 * value and a million seeded random values encode to the same bytes, at the start of a buffer and after a prefix, on
 * heap and direct buffers, and read back to the value written.
 */
@Tag("Network")
class VarIntWriteTest {

    private static final int RANDOM_VALUES = 1_000_000;
    private static final long SEED = 0x5EED_CAFEL;
    private static final int PREFIX = 1;

    @Test
    void varIntEdgesMatchTheVanillaLoop() {
        for (final int value : intEdges()) {
            for (final boolean direct : new boolean[] {false, true}) {
                for (final int prefix : new int[] {0, PREFIX}) {
                    final ByteBuf actual = direct ? Unpooled.directBuffer() : Unpooled.buffer();
                    final ByteBuf expected = direct ? Unpooled.directBuffer() : Unpooled.buffer();
                    try {
                        assertVarInt(value, prefix, actual, expected);
                    } finally {
                        actual.release();
                        expected.release();
                    }
                }
            }
        }
    }

    @Test
    void randomVarIntsMatchTheVanillaLoop() {
        final SplittableRandom random = new SplittableRandom(SEED);
        final ByteBuf actual = Unpooled.buffer();
        final ByteBuf expected = Unpooled.buffer();
        for (int i = 0; i < RANDOM_VALUES; i++) {
            final int raw = random.nextInt();
            final int value = (i & 1) == 0 ? raw : raw >>> random.nextInt(Integer.SIZE);
            assertVarInt(value, 0, actual, expected);
        }
    }

    @Test
    void varLongEdgesMatchTheVanillaLoop() {
        for (final long value : longEdges()) {
            for (final boolean direct : new boolean[] {false, true}) {
                for (final int prefix : new int[] {0, PREFIX}) {
                    final ByteBuf actual = direct ? Unpooled.directBuffer() : Unpooled.buffer();
                    final ByteBuf expected = direct ? Unpooled.directBuffer() : Unpooled.buffer();
                    try {
                        assertVarLong(value, prefix, actual, expected);
                    } finally {
                        actual.release();
                        expected.release();
                    }
                }
            }
        }
    }

    @Test
    void randomVarLongsMatchTheVanillaLoop() {
        final SplittableRandom random = new SplittableRandom(SEED);
        final ByteBuf actual = Unpooled.buffer();
        final ByteBuf expected = Unpooled.buffer();
        for (int i = 0; i < RANDOM_VALUES; i++) {
            final long raw = random.nextLong();
            final long value = (i & 1) == 0 ? raw : raw >>> random.nextInt(Long.SIZE);
            assertVarLong(value, 0, actual, expected);
        }
    }

    private static void assertVarInt(final int value, final int prefix, final ByteBuf actual, final ByteBuf expected) {
        actual.clear();
        expected.clear();
        actual.writeZero(prefix);
        expected.writeZero(prefix);

        VarInt.write(actual, value);
        vanillaVarInt(expected, value);

        assertSameBytes(expected, actual, value);
        assertEquals(VarInt.getByteSize(value), actual.readableBytes() - prefix, () -> "size of " + value);
        actual.skipBytes(prefix);
        assertEquals(value, VarInt.read(actual), () -> "read back " + value);
        assertEquals(0, actual.readableBytes(), () -> "bytes left after reading " + value);
    }

    private static void assertVarLong(final long value, final int prefix, final ByteBuf actual, final ByteBuf expected) {
        actual.clear();
        expected.clear();
        actual.writeZero(prefix);
        expected.writeZero(prefix);

        VarLong.write(actual, value);
        vanillaVarLong(expected, value);

        assertSameBytes(expected, actual, value);
        assertEquals(VarLong.getByteSize(value), actual.readableBytes() - prefix, () -> "size of " + value);
        actual.skipBytes(prefix);
        assertEquals(value, VarLong.read(actual), () -> "read back " + value);
        assertEquals(0, actual.readableBytes(), () -> "bytes left after reading " + value);
    }

    private static void vanillaVarInt(final ByteBuf output, final int value) {
        int remaining = value;
        while ((remaining & -128) != 0) {
            output.writeByte(remaining & 127 | 128);
            remaining >>>= 7;
        }

        output.writeByte(remaining);
    }

    private static void vanillaVarLong(final ByteBuf output, final long value) {
        long remaining = value;
        while ((remaining & -128L) != 0L) {
            output.writeByte((int) (remaining & 127) | 128);
            remaining >>>= 7;
        }

        output.writeByte((int) remaining);
    }

    private static void assertSameBytes(final ByteBuf expected, final ByteBuf actual, final long value) {
        final int length = expected.writerIndex();
        if (actual.writerIndex() != length || !ByteBufUtil.equals(expected, 0, actual, 0, length)) {
            fail(String.format(
                "value %d (0x%x): expected %s, wrote %s",
                value,
                value,
                ByteBufUtil.hexDump(expected, 0, length),
                ByteBufUtil.hexDump(actual, 0, actual.writerIndex())
            ));
        }
    }

    private static int[] intEdges() {
        return new int[] {
            0, 1, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456, Integer.MAX_VALUE, -1, Integer.MIN_VALUE
        };
    }

    private static List<Long> longEdges() {
        final List<Long> edges = new ArrayList<>();
        for (final int value : intEdges()) {
            edges.add((long) value);
            edges.add(value & 0xFFFFFFFFL);
        }
        for (int bits = 7; bits < Long.SIZE; bits += 7) {
            edges.add((1L << bits) - 1L);
            edges.add(1L << bits);
        }
        edges.add(Long.MAX_VALUE);
        edges.add(Long.MIN_VALUE);
        edges.add(-1L);
        return edges;
    }
}
