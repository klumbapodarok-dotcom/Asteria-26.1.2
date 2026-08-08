package asteria.top.client.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import asteria.top.client.render.HandShaderRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderType.class)
public abstract class RenderTypeMixin {
    @Shadow
    @Final
    protected String name;

    @Redirect(
        method = "draw",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"
        )
    )
    private void asteria$bindHandShaderUniform(
        RenderPass pass,
        int start,
        int baseVertex,
        int indexCount,
        int instanceCount
    ) {
        if (this.name.equals("hand_shader") || this.name.startsWith("item_hand_shader") ||
            this.name.equals("plasma_hand_shader") || this.name.startsWith("plasma_item_hand_shader")) {
            HandShaderRenderer.bindUniform(pass);
        } else if (this.name.equals("chams_shader") || this.name.equals("chams_through_walls_shader") ||
                   this.name.equals("plasma_shader") || this.name.equals("plasma_through_walls_shader")) {
            asteria.top.client.render.ChamsRenderer.bindUniform(pass);
        }
        pass.drawIndexed(start, baseVertex, indexCount, instanceCount);
    }
}
