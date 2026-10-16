package net.minecraft.world.entity.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.datafixers.util.Pair;
import io.canvasmc.canvas.util.MemoryGate;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import net.minecraft.world.entity.ai.behavior.ShufflingList;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryMap;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A behavior's memory gate must answer exactly as its own memory checks would: for every memory the behavior reads, in every
 * combination of unregistered, registered and empty, and holding a value, the gate passes only when the builder's evaluation
 * would have produced a trigger.
 */
@VanillaFeature
public class MemoryGateTest {

    private static final int RANDOM_TRIALS = 2000;

    @Test
    public void memoryTypesHaveDistinctIndexes() {
        final Set<Integer> seen = new HashSet<>();
        for (final MemoryModuleType<?> type : BuiltInRegistries.MEMORY_MODULE_TYPE) {
            assertTrue(type.canvas$index >= 0, "negative index for " + type);
            assertTrue(seen.add(type.canvas$index), "shared index " + type.canvas$index + " for " + type);
        }
    }

    @Test
    public void gateMatchesTheBuilderEvaluation() throws ReflectiveOperationException {
        final Map<BehaviorControl<?>, MemoryGate> gated = new IdentityHashMap<>();
        for (final Holder<VillagerProfession> profession : BuiltInRegistries.VILLAGER_PROFESSION.listElements().toList()) {
            for (final List<? extends Pair<Integer, ? extends BehaviorControl<?>>> pack : packages(profession)) {
                for (final Pair<Integer, ? extends BehaviorControl<?>> pair : pack) {
                    collect(pair.getSecond(), gated);
                }
            }
        }
        assertTrue(gated.size() >= 10, "only " + gated.size() + " gated behaviors found");

        final Random random = new Random(42);
        int checked = 0;
        for (final Map.Entry<BehaviorControl<?>, MemoryGate> entry : gated.entrySet()) {
            checked += check(entry.getKey(), entry.getValue(), random);
        }
        assertTrue(checked > 0);
    }

    private static List<List<? extends Pair<Integer, ? extends BehaviorControl<?>>>> packages(final Holder<VillagerProfession> profession) {
        final float speed = 0.5F;
        return List.of(
            VillagerGoalPackages.getCorePackage(profession, speed),
            VillagerGoalPackages.getWorkPackage(profession, speed),
            VillagerGoalPackages.getPlayPackage(speed),
            VillagerGoalPackages.getRestPackage(speed),
            VillagerGoalPackages.getMeetPackage(speed),
            VillagerGoalPackages.getIdlePackage(speed),
            VillagerGoalPackages.getPanicPackage(speed),
            VillagerGoalPackages.getPreRaidPackage(speed),
            VillagerGoalPackages.getRaidPackage(speed),
            VillagerGoalPackages.getHidePackage(speed)
        );
    }

    @SuppressWarnings("unchecked")
    private static void collect(final BehaviorControl<?> behavior, final Map<BehaviorControl<?>, MemoryGate> gated) throws ReflectiveOperationException {
        if (behavior instanceof GateBehavior<?> gate) {
            final Field field = GateBehavior.class.getDeclaredField("behaviors");
            field.setAccessible(true);
            for (final BehaviorControl<?> child : ((ShufflingList<BehaviorControl<?>>) field.get(gate)).stream().toList()) {
                collect(child, gated);
            }
            return;
        }
        final MemoryGate memoryGate = behavior.canvas$memoryGate();
        if (memoryGate != null) {
            gated.put(behavior, memoryGate);
        }
    }

    private static int check(final BehaviorControl<?> behavior, final MemoryGate gate, final Random random) throws ReflectiveOperationException {
        final Object builder = resolvedBuilder(behavior);
        final Method tryTrigger = builder.getClass().getDeclaredMethod("tryTrigger", ServerLevel.class, LivingEntity.class, long.class);
        tryTrigger.setAccessible(true);
        final List<MemoryModuleType<?>> memories = new ArrayList<>(behavior.getRequiredMemories());
        final int states = (int) Math.min(Math.pow(3, memories.size()), Integer.MAX_VALUE);
        final boolean exhaustive = states <= RANDOM_TRIALS;
        final int trials = exhaustive ? states : RANDOM_TRIALS;
        int passed = 0;
        for (int trial = 0; trial < trials; ++trial) {
            final int[] choice = new int[memories.size()];
            int code = trial;
            for (int i = 0; i < choice.length; ++i) {
                choice[i] = exhaustive ? code % 3 : random.nextInt(3);
                code /= 3;
            }
            final Brain<?> brain = brain(memories, choice);
            final LivingEntity body = Mockito.mock(LivingEntity.class);
            Mockito.doReturn(brain).when(body).getBrain();
            final boolean expected = tryTrigger.invoke(builder, null, body, 0L) != null;
            assertEquals(expected, gate.test(brain), behavior.debugString() + " with " + describe(memories, choice));
            passed += expected ? 1 : 0;
        }
        if (passed == 0 || passed == trials) {
            throw new AssertionError(behavior.debugString() + " never covered both answers (" + passed + " of " + trials + ")");
        }
        return trials;
    }

    private static Object resolvedBuilder(final BehaviorControl<?> behavior) throws ReflectiveOperationException {
        for (final Field field : behavior.getClass().getDeclaredFields()) {
            if (field.getType().getSimpleName().equals("TriggerWithResult")) {
                field.setAccessible(true);
                return field.get(behavior);
            }
        }
        throw new AssertionError("no builder captured by " + behavior.debugString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Brain<?> brain(final List<MemoryModuleType<?>> memories, final int[] choice) {
        final List<MemoryModuleType<?>> registered = new ArrayList<>();
        for (int i = 0; i < memories.size(); ++i) {
            if (choice[i] != 0) {
                registered.add(memories.get(i));
            }
        }
        final Brain<LivingEntity> brain = new Brain<>(registered, List.of(), List.of(), MemoryMap.EMPTY, RandomSource.create(0L));
        for (int i = 0; i < memories.size(); ++i) {
            if (choice[i] == 2) {
                brain.setMemory((MemoryModuleType) memories.get(i), (Object) Unit.INSTANCE);
            }
        }
        return brain;
    }

    private static String describe(final List<MemoryModuleType<?>> memories, final int[] choice) {
        final Function<Integer, String> state = value -> switch (value) {
            case 0 -> "unregistered";
            case 1 -> "empty";
            default -> "present";
        };
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < memories.size(); ++i) {
            builder.append(memories.get(i)).append('=').append(state.apply(choice[i])).append(' ');
        }
        return builder.toString().trim();
    }
}
