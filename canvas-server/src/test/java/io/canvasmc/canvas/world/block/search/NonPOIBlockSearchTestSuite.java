package io.canvasmc.canvas.world.block.search;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Canvas non-POI block search tests")
@IncludeTags("VanillaFeature")
@SelectClasses(NonPOIBlockSearchTest.class)
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class NonPOIBlockSearchTestSuite {
}
