package net.minecraft.world.level.block.entity;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Canvas hopper fullness tests")
@IncludeTags("VanillaFeature")
@SelectClasses(HopperFullnessTest.class)
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class HopperFullnessTestSuite {
}
