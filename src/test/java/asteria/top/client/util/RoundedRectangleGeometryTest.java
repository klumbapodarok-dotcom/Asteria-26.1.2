package asteria.top.client.util;

public final class RoundedRectangleGeometryTest {
    public static void main(String[] args) {
        clampsHugeRadiusToHalfSmallestAxis();
        preservesFractionalRadiusDuringAnimation();
        boundsTessellationDensity();
        scalesAntiAliasWidthForTinyRects();
        try {
            LiquidGlassGeometryTest.main(args);
            LiquidGlassShaderContractTest.main(args);
            PostProcessingAllocationContractTest.main(args);
            HudWidgetAnimationContractTest.main(args);
            NormalRotationMoveFixContractTest.main(args);
            AntiWebContractTest.main(args);
            WindHopContractTest.main(args);
            SpearLungeContractTest.main(args);
            TargetEspContractTest.main(args);
        } catch (Exception error) {
            throw new AssertionError("liquid glass geometry regression failed", error);
        }
    }

    private static void clampsHugeRadiusToHalfSmallestAxis() {
        assertClose(20.0f, RoundedRectangleGeometry.stableRadius(130.0f, 40.0f, 990.0f), "huge radius clamps to pill");
        assertClose(20.0f, RoundedRectangleGeometry.stableRadius(130.0f, 40.0f, 30.0f), "normal radius clamps to same pill");
    }

    private static void preservesFractionalRadiusDuringAnimation() {
        float radius = RoundedRectangleGeometry.stableRadius(83.25f, 27.5f, 11.375f);
        assertClose(11.375f, radius, "fractional radius survives");
        if (radius == (float) Math.floor(radius)) {
            throw new AssertionError("radius was integer-snapped");
        }
    }

    private static void boundsTessellationDensity() {
        int tiny = RoundedRectangleGeometry.segmentCount(1.2f);
        int category = RoundedRectangleGeometry.segmentCount(20.0f);
        int huge = RoundedRectangleGeometry.segmentCount(990.0f);

        if (tiny < 4) {
            throw new AssertionError("tiny radius should still have a smooth minimum segment count");
        }
        if (category <= tiny) {
            throw new AssertionError("larger radius should receive denser tessellation");
        }
        if (huge > 18) {
            throw new AssertionError("segment count should be capped for GUI performance");
        }
    }

    private static void scalesAntiAliasWidthForTinyRects() {
        float aa = RoundedRectangleGeometry.antiAliasWidth(1.0f, 1.0f, 0.75f);
        if (aa <= 0.0f || aa > 0.25f) {
            throw new AssertionError("anti-alias width should stay visible but bounded for tiny rectangles: " + aa);
        }
    }

    private static void assertClose(float expected, float actual, String label) {
        if (Math.abs(expected - actual) > 0.0001f) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
