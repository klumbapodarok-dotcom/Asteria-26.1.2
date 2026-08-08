package asteria.top.client.mixin;

import asteria.top.client.module.ModuleManager;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionIncomingMixin {
    @Inject(
        method = "genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static <T extends PacketListener> void glacierInterceptIncomingPacket(
            Packet<T> packet,
            PacketListener listener,
            CallbackInfo ci
    ) {
        if (ModuleManager.INSTANCE.getBacktrack().interceptIncomingPacket(packet)) {
            ci.cancel();
        }
    }
}
