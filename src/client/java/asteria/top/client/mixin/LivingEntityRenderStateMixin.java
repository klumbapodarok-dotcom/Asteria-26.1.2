package asteria.top.client.mixin;

import asteria.top.client.render.ChamsRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements ChamsRenderState {
    private boolean asteria$isSelf;
    private boolean asteria$isPlayer;
    private boolean asteria$isMob;
    private boolean asteria$isAnimal;

    @Override
    public boolean isSelf() {
        return asteria$isSelf;
    }

    @Override
    public void setSelf(boolean val) {
        this.asteria$isSelf = val;
    }

    @Override
    public boolean isPlayer() {
        return asteria$isPlayer;
    }

    @Override
    public void setPlayer(boolean val) {
        this.asteria$isPlayer = val;
    }

    @Override
    public boolean isMob() {
        return asteria$isMob;
    }

    @Override
    public void setMob(boolean val) {
        this.asteria$isMob = val;
    }

    @Override
    public boolean isAnimal() {
        return asteria$isAnimal;
    }

    @Override
    public void setAnimal(boolean val) {
        this.asteria$isAnimal = val;
    }
}
