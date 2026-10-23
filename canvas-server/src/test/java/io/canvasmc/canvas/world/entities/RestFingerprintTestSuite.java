package io.canvasmc.canvas.world.entities;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Canvas rest fingerprint tests")
@IncludeTags("VanillaFeature")
@SelectClasses(RestFingerprintTest.class)
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class RestFingerprintTestSuite {
}
