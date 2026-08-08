package asteria.top.client.mixin

import asteria.top.client.util.ChatRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ChatComponent
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ChatComponent::class)
class ChatComponentMixin {
    @Inject(
        method = [
            "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent\$DisplayMode;Z)V"
        ],
        at = [At("HEAD")]
    )
    private fun onExtractRenderStateHead(
        graphics: GuiGraphicsExtractor,
        font: Font,
        p2: Int,
        p3: Int,
        p4: Int,
        p5: ChatComponent.DisplayMode,
        p6: Boolean,
        ci: CallbackInfo
    ) {
        ChatRenderContext.currentGraphics = graphics
    }

    @Inject(
        method = [
            "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent\$DisplayMode;Z)V"
        ],
        at = [At("RETURN")]
    )
    private fun onExtractRenderStateReturn(
        graphics: GuiGraphicsExtractor,
        font: Font,
        p2: Int,
        p3: Int,
        p4: Int,
        p5: ChatComponent.DisplayMode,
        p6: Boolean,
        ci: CallbackInfo
    ) {
        ChatRenderContext.currentGraphics = null
    }
}
