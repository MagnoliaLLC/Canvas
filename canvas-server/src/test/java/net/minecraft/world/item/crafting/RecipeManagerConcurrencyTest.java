package net.minecraft.world.item.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

/**
 * Region threads read the recipes while a plugin changes them from another thread: a reader never sees a change in progress,
 * and changes made at once all land.
 */
@VanillaFeature
public class RecipeManagerConcurrencyTest {

    private static final CraftingInput STICK = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));

    private static RecipeManager emptyManager() {
        final MappedRegistry<Recipe<?>> recipes = new MappedRegistry<>(Registries.RECIPE, Lifecycle.stable());
        return new RecipeManager(HolderLookup.Provider.create(Stream.of(recipes.freeze())));
    }

    private static RecipeHolder<ShapelessRecipe> stickRecipe(final String name) {
        final ShapelessRecipe recipe = new ShapelessRecipe(
            new Recipe.CommonInfo(false),
            new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
            new ItemStackTemplate(Items.STICK),
            List.of(Ingredient.of(Items.STICK))
        );
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("canvas", name)), recipe);
    }

    private static void runOnTickThreads(final List<Runnable> tasks, final Queue<Throwable> failures) throws InterruptedException {
        final List<Thread> threads = tasks.stream().map(task -> (Thread) new TickThread(() -> {
            try {
                task.run();
            } catch (final Throwable failure) {
                failures.add(failure);
            }
        }, "recipe test")).toList();
        threads.forEach(Thread::start);
        for (final Thread thread : threads) {
            thread.join(60_000L);
            assertFalse(thread.isAlive(), "a recipe thread did not finish");
        }
    }

    @Test
    public void readersNeverSeeAChangeInProgress() throws InterruptedException {
        final RecipeManager manager = emptyManager();
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        final AtomicBoolean writing = new AtomicBoolean(true);

        runOnTickThreads(List.of(() -> IntStream.range(0, 200).forEach(i -> manager.addRecipe(stickRecipe("base_" + i)))), failures);

        final Runnable reader = () -> {
            while (writing.get()) {
                assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, STICK, null).isPresent());
            }
        };
        final Runnable writer = () -> {
            try {
                for (int i = 0; i < 2_000; i++) {
                    final RecipeHolder<ShapelessRecipe> recipe = stickRecipe("changed_" + i);
                    manager.addRecipe(recipe);
                    manager.removeRecipe(recipe.id());
                }
            } finally {
                writing.set(false);
            }
        };

        runOnTickThreads(List.of(reader, reader, reader, reader, writer), failures);

        assertEquals(List.of(), failures.stream().map(Throwable::toString).toList());
        assertEquals(200, manager.getRecipes().size());
    }

    @Test
    public void changesMadeAtOnceAllLand() throws InterruptedException {
        final RecipeManager manager = emptyManager();
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        runOnTickThreads(
            IntStream.range(0, 4).<Runnable>mapToObj(writer -> () -> IntStream.range(0, 250).forEach(i -> manager.addRecipe(stickRecipe("writer_" + writer + "_" + i)))).toList(),
            failures
        );

        assertEquals(List.of(), failures.stream().map(Throwable::toString).toList());
        assertEquals(1_000, manager.getRecipes().size());
        assertEquals(1_000, manager.recipes.byType(RecipeType.CRAFTING).size());
    }
}
