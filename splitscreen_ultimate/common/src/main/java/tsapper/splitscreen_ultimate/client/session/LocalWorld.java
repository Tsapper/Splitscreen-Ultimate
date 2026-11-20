package tsapper.splitscreen_ultimate.client.session;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.client.server.LanServerPinger;
import java.io.IOException;
import java.net.*;

/** Private listener remains open when the player optionally publishes a second listener to LAN. */
public final class LocalWorld {
    public static volatile boolean openingPrivate;
    public static volatile IntegratedServer host;
    public static volatile int privatePort;
    public static volatile boolean publicLan;
    private static LanServerPinger publicPinger;
    public static int open(IntegratedServer server) throws IOException {
        int port;
        try (var probe = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { port = probe.getLocalPort(); }
        openingPrivate = true;
        try {
            if (!server.publishServer(null, false, port)) throw new IOException("Cannot open local world listener");
            host = server; privatePort = port; publicLan = false;
            return port;
        } finally { openingPrivate = false; }
    }
    public static boolean publish(IntegratedServer server, GameType mode, boolean commands, int port) {
        if (server != host) return server.publishServer(mode, commands, port);
        if (publicLan) return false;
        try {
            // A different port permits the private listener and existing guests to stay connected.
            if (port == privatePort) return false;
            server.getConnection().startTcpServerListener(null, port);
            server.getPlayerList().setAllowCommandsForAllPlayers(commands);
            ((tsapper.splitscreen_ultimate.mixin.IntegratedServerAccessor)server).su$gameType(mode);
            if (mode != null) for (ServerPlayer player : server.getPlayerList().getPlayers()) player.setGameMode(mode);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
            publicPinger = new LanServerPinger(server.getMotd(), Integer.toString(port));
            publicPinger.start();
            publicLan = true;
            return true;
        } catch (IOException e) { return false; }
    }
    public static void clear() {
        if (publicPinger != null) { publicPinger.interrupt(); publicPinger = null; }
        host = null; privatePort = 0; publicLan = false;
    }
    public static boolean expectedGuest(String name, SocketAddress remote, SocketAddress local) {
        if (!LaunchConfig.enabled() || host == null || !(remote instanceof InetSocketAddress address)
                || !address.getAddress().isLoopbackAddress() || !(local instanceof InetSocketAddress listener)
                || listener.getPort() != privatePort) return false;
        return SessionClient.INSTANCE != null && SessionClient.INSTANCE.expectedGuest(name);
    }
}
