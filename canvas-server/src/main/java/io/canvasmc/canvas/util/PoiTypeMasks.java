package io.canvasmc.canvas.util;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/**
 * One bit per point of interest type; types past the 63rd, and unregistered ones, share the last bit, which every query includes.
 */
public final class PoiTypeMasks {

    private static final int SHARED_BIT = 63;

    private PoiTypeMasks() {
    }

    public static long bit(final Holder<PoiType> type) {
        return bitOf(BuiltInRegistries.POINT_OF_INTEREST_TYPE.getId(type.value()));
    }

    public static long matching(final Predicate<Holder<PoiType>> predicate) {
        final IdMap<Holder<PoiType>> types = BuiltInRegistries.POINT_OF_INTEREST_TYPE.asHolderIdMap();
        long mask = 1L << SHARED_BIT;

        for (int id = 0, size = types.size(); id < size; ++id) {
            final Holder<PoiType> type = types.byId(id);

            if (type != null && predicate.test(type)) {
                mask |= bitOf(id);
            }
        }

        return mask;
    }

    private static long bitOf(final int id) {
        return 1L << (id < 0 || id > SHARED_BIT ? SHARED_BIT : id);
    }
}
