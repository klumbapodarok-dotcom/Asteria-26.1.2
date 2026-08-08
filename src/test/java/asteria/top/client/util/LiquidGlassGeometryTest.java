package asteria.top.client.util;

import java.lang.reflect.Method;

public final class LiquidGlassGeometryTest {
    public static void main(String[] args) throws Exception {
        Class<?> geometry;
        try {
            geometry = Class.forName("asteria.top.client.util.LiquidGlassGeometry");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("LiquidGlassGeometry must define box-local glass optics", missing);
        }

        clampsRadiusToBoxBounds(geometry);
        keepsBoxCenterDisplacementFinite(geometry);
        scalesDisplacementFromOwningBox(geometry);
        selectsTopmostOverlappingBox(geometry);
    }

    private static void clampsRadiusToBoxBounds(Class<?> geometry) throws Exception {
        Method stableRadius = geometry.getMethod("stableRadius", float.class, float.class, float.class);
        float actual = (float) stableRadius.invoke(null, 100.0f, 30.0f, 99.0f);
        assertClose(15.0f, actual, "radius clamp");
    }

    private static void keepsBoxCenterDisplacementFinite(Class<?> geometry) throws Exception {
        Method displacement = geometry.getMethod(
            "radialDisplacement",
            float.class, float.class, float.class, float.class,
            float.class, float.class, float.class, float.class
        );
        Object offset = displacement.invoke(null, 50.0f, 50.0f, 0.0f, 0.0f, 100.0f, 100.0f, 1.0f, 0.04f);
        assertClose(0.0f, component(offset, "x"), "center displacement x");
        assertClose(0.0f, component(offset, "y"), "center displacement y");
    }

    private static void scalesDisplacementFromOwningBox(Class<?> geometry) throws Exception {
        Method displacement = geometry.getMethod(
            "radialDisplacement",
            float.class, float.class, float.class, float.class,
            float.class, float.class, float.class, float.class
        );
        Object offset = displacement.invoke(null, 90.0f, 50.0f, 0.0f, 0.0f, 100.0f, 100.0f, 1.0f, 0.04f);
        assertClose(4.0f, component(offset, "x"), "right-edge displacement x");
        assertClose(0.0f, component(offset, "y"), "right-edge displacement y");
    }

    private static void selectsTopmostOverlappingBox(Class<?> geometry) throws Exception {
        Method select = geometry.getMethod("selectTopmostBox", float.class, float.class, float[].class, int.class);
        float[] boxes = {
            0.0f, 0.0f, 100.0f, 100.0f,
            25.0f, 25.0f, 50.0f, 50.0f,
        };
        int selected = (int) select.invoke(null, 50.0f, 50.0f, boxes, 2);
        if (selected != 1) {
            throw new AssertionError("last overlapping box must own the reflection: " + selected);
        }
    }

    private static float component(Object offset, String name) throws Exception {
        String accessor = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return (float) offset.getClass().getMethod(accessor).invoke(offset);
    }

    private static void assertClose(float expected, float actual, String label) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.0001f) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
