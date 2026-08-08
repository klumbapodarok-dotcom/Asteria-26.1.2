package asteria.top.client.mixin

import asteria.top.client.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Connection::class)
class ConnectionMixin {
    @Inject(method = ["send(Lnet/minecraft/network/protocol/Packet;)V"], at = [At("HEAD")], cancellable = true)
    private fun glacierInterceptOutgoingPacket(packet: Packet<*>, ci: CallbackInfo) {
        if (packet is ServerboundInteractPacket) {
            val targetEntity = Minecraft.getInstance().level?.getEntity(packet.entityId())
            if (targetEntity != null) {
                ModuleManager.backtrack.setTarget(targetEntity)
            }
        }

        ModuleManager.trapEsp.onOutgoingPacket(packet)

        if (ModuleManager.fakeLag.interceptOutgoingPacket(packet)) {
            ci.cancel()
        }
    }
}
