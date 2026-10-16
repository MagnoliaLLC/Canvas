package net.minecraft.world.entity.ai;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Canvas memory gate tests")
@IncludeTags("VanillaFeature")
@SelectClasses(MemoryGateTest.class)
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class MemoryGateTestSuite {
}
