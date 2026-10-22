package io.canvasmc.canvas.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The bulk long array writer against writing each long through {@code writeLong}: on every kind of buffer, starting
 * at an unaligned nonzero writer index, the bytes written and the writer index left behind are the loop's.
 */
@Tag("Network")
class LongArrayWriteTest {

    private static final int PREFIX = 3;
    private static final long[] LONGS = longs(37);

    @Test
    void heapBufferMatchesTheLoop() {
        assertMatchesLoop(Unpooled::buffer);
    }

    @Test
    void directBufferMatchesTheLoop() {
        assertMatchesLoop(Unpooled::directBuffer);
    }

    @Test
    void offsetHeapSliceMatchesTheLoop() {
        assertMatchesLoop(() -> Unpooled.wrappedBuffer(new byte[512], 7, PREFIX + LONGS.length * Long.BYTES + 9).clear());
    }

    @Test
    void multiComponentCompositeMatchesTheLoop() {
        assertMatchesLoop(() -> {
            final CompositeByteBuf composite = Unpooled.compositeBuffer();
            composite.addComponent(false, Unpooled.wrappedBuffer(new byte[2]));
            composite.addComponent(false, Unpooled.wrappedBuffer(new byte[2]));
            assertFalse(composite.hasArray());
            return composite;
        });
    }

    @Test
    void singleComponentCompositeMatchesTheLoop() {
        assertMatchesLoop(() -> {
            final CompositeByteBuf composite = Unpooled.compositeBuffer();
            composite.addComponent(false, Unpooled.wrappedBuffer(new byte[PREFIX + LONGS.length * Long.BYTES + 5]));
            assertTrue(composite.hasArray());
            return composite;
        });
    }

    @Test
    void growingCompositeMatchesTheLoop() {
        assertMatchesLoop(() -> {
            final CompositeByteBuf composite = Unpooled.compositeBuffer();
            composite.addComponent(false, Unpooled.wrappedBuffer(new byte[PREFIX + 4]));
            assertTrue(composite.hasArray());
            return composite;
        });
    }

    @Test
    void consolidatingCompositeMatchesTheLoop() {
        assertMatchesLoop(() -> {
            final CompositeByteBuf composite = Unpooled.compositeBuffer(1);
            composite.addComponent(false, Unpooled.wrappedBuffer(new byte[PREFIX + 4]));
            assertTrue(composite.hasArray());
            return composite;
        });
    }

    @Test
    @SuppressWarnings("deprecation")
    void littleEndianBufferMatchesTheLoop() {
        assertMatchesLoop(() -> Unpooled.buffer().order(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void emptyArrayWritesNothing() {
        assertMatchesLoop(Unpooled::buffer, new long[0]);
    }

    @Test
    void chunkBufferKeepsTheIndexAntiXrayRecords() {
        final byte[] array = new byte[PREFIX + LONGS.length * Long.BYTES];
        final ByteBuf wrapped = Unpooled.wrappedBuffer(array);
        wrapped.writerIndex(0);
        final FriendlyByteBuf buffer = new FriendlyByteBuf(wrapped);
        writePrefix(buffer);
        final int index = buffer.writerIndex();

        buffer.writeFixedSizeLongArray(LONGS);

        assertEquals(buffer.capacity(), buffer.writerIndex());
        final ByteBuffer view = ByteBuffer.wrap(array);
        for (int i = 0; i < LONGS.length; i++) {
            assertEquals(LONGS[i], view.getLong(index + i * Long.BYTES), "long " + i);
        }
    }

    @Test
    void bufferTooSmallFailsLikeTheLoop() {
        final long[] three = {1L, 2L, 3L};
        final ByteBuf expected = Unpooled.buffer(8, PREFIX + 2 * Long.BYTES + 4);
        final ByteBuf actual = Unpooled.buffer(8, PREFIX + 2 * Long.BYTES + 4);
        writePrefix(expected);
        writePrefix(actual);

        assertThrows(IndexOutOfBoundsException.class, () -> loop(expected, three));
        assertThrows(IndexOutOfBoundsException.class, () -> FriendlyByteBuf.writeFixedSizeLongArray(actual, three));

        assertSameBytes(expected, actual, "too small");
    }

    @Test
    void writeLongArrayMatchesTheLoop() {
        final ByteBuf expected = Unpooled.buffer();
        final ByteBuf viaStatic = Unpooled.buffer();
        final ByteBuf viaInstance = Unpooled.buffer();
        writePrefix(expected);
        writePrefix(viaStatic);
        writePrefix(viaInstance);

        expected.writeByte(LONGS.length);
        loop(expected, LONGS);
        FriendlyByteBuf.writeLongArray(viaStatic, LONGS);
        new FriendlyByteBuf(viaInstance).writeLongArray(LONGS);

        assertSameBytes(expected, viaStatic, "static writeLongArray");
        assertSameBytes(expected, viaInstance, "instance writeLongArray");
    }

    private static void assertMatchesLoop(final Supplier<ByteBuf> buffers) {
        assertMatchesLoop(buffers, LONGS);
    }

    private static void assertMatchesLoop(final Supplier<ByteBuf> buffers, final long[] longs) {
        final ByteBuf expected = buffers.get();
        final ByteBuf viaStatic = buffers.get();
        final ByteBuf viaInstance = buffers.get();
        try {
            writePrefix(expected);
            writePrefix(viaStatic);
            writePrefix(viaInstance);

            loop(expected, longs);
            FriendlyByteBuf.writeFixedSizeLongArray(viaStatic, longs);
            new FriendlyByteBuf(viaInstance).writeFixedSizeLongArray(longs);

            assertSameBytes(expected, viaStatic, "static writeFixedSizeLongArray");
            assertSameBytes(expected, viaInstance, "instance writeFixedSizeLongArray");
        } finally {
            expected.release();
            viaStatic.release();
            viaInstance.release();
        }
    }

    private static void loop(final ByteBuf output, final long[] longs) {
        for (final long l : longs) {
            output.writeLong(l);
        }
    }

    private static void writePrefix(final ByteBuf output) {
        for (int i = 1; i <= PREFIX; i++) {
            output.writeByte(i);
        }
    }

    private static void assertSameBytes(final ByteBuf expected, final ByteBuf actual, final String what) {
        final int length = expected.writerIndex();
        assertEquals(length, actual.writerIndex(), what + ": writer index");
        if (!ByteBufUtil.equals(expected, 0, actual, 0, length)) {
            fail(String.format(
                "%s: expected %s, wrote %s",
                what,
                ByteBufUtil.hexDump(expected, 0, length),
                ByteBufUtil.hexDump(actual, 0, length)
            ));
        }
    }

    private static long[] longs(final int count) {
        final long[] longs = new long[count];
        longs[0] = 0L;
        longs[1] = -1L;
        longs[2] = Long.MIN_VALUE;
        longs[3] = Long.MAX_VALUE;
        longs[4] = 0x0102030405060708L;
        final SplittableRandom random = new SplittableRandom(0x10A6_A88AL);
        for (int i = 5; i < count; i++) {
            longs[i] = random.nextLong();
        }
        return longs;
    }
}
