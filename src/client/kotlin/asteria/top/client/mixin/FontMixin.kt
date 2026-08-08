package asteria.top.client.mixin

import asteria.top.client.render.FontRenderer
import asteria.top.client.util.ChatRenderContext
import net.minecraft.client.gui.Font
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Font::class)
class FontMixin {
    @Inject(
        method = [
            "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font\$DisplayMode;II)V"
        ],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun onDrawInBatch(
        text: net.minecraft.util.FormattedCharSequence,
        x: Float,
        y: Float,
        color: Int,
        dropShadow: Boolean,
        matrix: org.joml.Matrix4fc,
        bufferSource: net.minecraft.client.renderer.MultiBufferSource,
        displayMode: net.minecraft.client.gui.Font.DisplayMode,
        backgroundColor: Int,
        lightmap: Int,
        ci: CallbackInfo
    ) {
        val graphics = ChatRenderContext.currentGraphics ?: return

        val sb = StringBuilder()
        text.accept { _, _, codePoint ->
            sb.appendCodePoint(codePoint)
            true
        }
        val plainText = sb.toString()

        if (plainText.startsWith("[Asteria]")) {
            ci.cancel()

            FontRenderer.drawFormatted(
                graphics = graphics,
                face = FontRenderer.Face.SfRegular,
                formattedText = text,
                x = x,
                y = y,
                size = 9.0f,
                defaultColor = color,
                bloom = true
            )
        }
    }
}
