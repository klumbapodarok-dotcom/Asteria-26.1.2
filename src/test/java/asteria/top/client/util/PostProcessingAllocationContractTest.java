package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PostProcessingAllocationContractTest {
    public static void main(String[] args) throws Exception {
        String renderer = Files.readString(Path.of("src/client/kotlin/asteria/top/client/render/DoubleKawaseBlurRenderer.kt"));

        require(renderer, "private val kawaseInfoBuffer = reusableBuffer(KAWASE_UBO_SIZE)", "Kawase UBO data should reuse a direct buffer");
        require(renderer, "private val gaussianInfoBuffer = reusableBuffer(GAUSSIAN_UBO_SIZE)", "Gaussian UBO data should reuse a direct buffer");
        require(renderer, "private val compositeInfoBuffer = reusableBuffer(COMPOSITE_UBO_SIZE)", "Composite UBO data should reuse a direct buffer");
        require(renderer, "private val liquidGlassInfoBuffer = reusableBuffer(LIQUID_GLASS_UBO_SIZE)", "Liquid glass UBO data should reuse a direct buffer");
        require(renderer, "private fun reusableBuffer(size: Int): ByteBuffer", "renderer should centralize reusable direct buffer creation");
        require(renderer, "buffer.clear()", "reused buffers should be cleared before writing");
        reject(renderer, "ByteBuffer.allocateDirect(KAWASE_UBO_SIZE)", "Kawase pass should not allocate direct buffers per frame");
        reject(renderer, "ByteBuffer.allocateDirect(GAUSSIAN_UBO_SIZE)", "Gaussian pass should not allocate direct buffers per frame");
        reject(renderer, "ByteBuffer.allocateDirect(COMPOSITE_UBO_SIZE)", "Composite pass should not allocate direct buffers per frame");
        reject(renderer, "ByteBuffer.allocateDirect(LIQUID_GLASS_UBO_SIZE)", "Liquid glass pass should not allocate direct buffers per frame");
    }

    private static void require(String source, String token, String label) {
        if (!source.contains(token)) {
            throw new AssertionError(label + " is missing: " + token);
        }
    }

    private static void reject(String source, String token, String label) {
        if (source.contains(token)) {
            throw new AssertionError(label + ": " + token);
        }
    }
}
