package net.minecraft.world.item.crafting;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Canvas recipe manager concurrency tests")
@IncludeTags("VanillaFeature")
@SelectClasses(RecipeManagerConcurrencyTest.class)
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class RecipeManagerConcurrencyTestSuite {
}
