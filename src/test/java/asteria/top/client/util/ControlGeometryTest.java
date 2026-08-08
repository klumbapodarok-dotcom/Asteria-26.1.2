package asteria.top.client.util;

public final class ControlGeometryTest {
    public static void main(String[] args) {
        settingSwitchIsSmallerThanModuleToggle();
        pillUsesHalfCircleCapsAndCenterRect();
    }

    private static void settingSwitchIsSmallerThanModuleToggle() {
        assertClose(30.0f, ControlGeometry.MODULE_SWITCH_WIDTH, "module switch width");
        assertClose(16.0f, ControlGeometry.MODULE_SWITCH_HEIGHT, "module switch height");
        assertClose(28.0f, ControlGeometry.SETTING_SWITCH_WIDTH, "setting switch width");
        assertClose(15.0f, ControlGeometry.SETTING_SWITCH_HEIGHT, "setting switch height");
    }

    private static void pillUsesHalfCircleCapsAndCenterRect() {
        ControlGeometry.PillSegments segments = ControlGeometry.pillSegments(36.0f, 19.0f);
        assertClose(9.5f, segments.getRadius(), "pill cap radius");
        assertClose(17.0f, segments.getCenterWidth(), "pill center width");
    }

    private static void assertClose(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 0.001f) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }
}
