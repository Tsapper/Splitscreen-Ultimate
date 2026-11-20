package tsapper.splitscreen_ultimate.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tsapper.splitscreen_ultimate.client.session.LocalWorld;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginMixin {
    @Shadow @Final private Connection connection;
    @Shadow private String requestedUsername;
    @Shadow abstract void startClientVerification(GameProfile profile);

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void su$localOfflineGuest(ServerboundHelloPacket packet, CallbackInfo ci) {
        var channel = ((ConnectionAccessor)connection).su$channel();
        if (channel != null && LocalWorld.expectedGuest(packet.name(), connection.getRemoteAddress(), channel.localAddress())) {
            requestedUsername = packet.name();
            UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + packet.name()).getBytes(StandardCharsets.UTF_8));
            startClientVerification(new GameProfile(id, packet.name()));
            ci.cancel();
        }
    }
}
