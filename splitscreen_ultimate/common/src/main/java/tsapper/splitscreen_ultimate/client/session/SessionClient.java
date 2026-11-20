package tsapper.splitscreen_ultimate.client.session;

import com.google.gson.*;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import org.lwjgl.glfw.GLFWNativeWin32;
import tsapper.simple_controller_support.api.*;
import tsapper.simple_controller_support.client.controller.ControllerButton;
import tsapper.splitscreen_ultimate.SplitscreenUltimateMod;
import java.util.*;

public final class SessionClient {
    public static SessionClient INSTANCE;
    private final Minecraft mc = Minecraft.getInstance();
    private final LaunchConfig config;
    private final Players players = new Players();
    private IpcClient ipc;
    private ExternalController external;
    private IntegratedServer world;
    private long generation, sequence, lastSequence = -1, connectingSince;
    private int targetPort;
    private boolean registered, connecting, joined, active, shutdown;
    private boolean leavingWorld;
    private volatile Set<String> guestNames = Set.of();
    private final Map<Integer, ControllerSnapshot> sent = new HashMap<>();
    private final Map<Integer, Long> sentAt = new HashMap<>();
    private final Map<Integer, Boolean> connectedControllers = new HashMap<>();
    private String error = "";
    private List<String> statusLines = List.of();
    private final PrototypeSmoke smoke;
    private final Set<Integer> observedInput = new HashSet<>();
    private long observedGeneration = -1;

    public SessionClient(LaunchConfig config) {
        this.config = config;
        GlfwActiveWindowGuard.install();
        smoke = config.main() && Boolean.getBoolean("splitscreen.smoke") ? new PrototypeSmoke() : null;
        INSTANCE = this;
        ControllerSupport.disable();
        if (!config.main()) {
            ControllerSupport.setPhysicalInputEnabled(false);
            external = ControllerSupport.registerExternalController("Assigned local player " + config.slot());
        }
        ControllerSupport.onFrame(this::input);
        ClientTickEvent.CLIENT_POST.register(client -> tick());
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> drawError(graphics));
        ClientGuiEvent.RENDER_POST.register((screen, graphics, mouseX, mouseY, delta) -> drawError(graphics));
    }

    private void tick() {
        if (shutdown) return;
        if (ipc == null) {
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().getWindow());
            ipc = new IpcClient(config, hwnd,
                    message -> mc.execute(() -> receive(message)),
                    problem -> mc.execute(() -> {
                        SplitscreenUltimateMod.LOGGER.error("Launcher connection failed: {}", problem);
                        error = "Launcher: " + problem; registered = false;
                        ControllerSupport.disable();
                        if (external != null) external.clear();
                        // A lost coordinator must never leave a hidden orphan running.
                        if (!config.main()) mc.stop();
                    }));
        }
        if (!registered) return;
        if (smoke != null) smoke.tick(this, (int)players.all().stream().filter(p -> p.ready).count(),
                (int)players.all().stream().filter(p -> p.world).count(), observedInput.size());
        if (config.main()) {
            if (leavingWorld) {
                if (mc.level != null || mc.getSingleplayerServer() != null) return;
                leavingWorld = false;
            }
            IntegratedServer current = mc.level != null && mc.player != null ? mc.getSingleplayerServer() : null;
            if (current != world) {
                generation++;
                observedInput.clear();
                LocalWorld.clear();
                world = current;
                JsonObject event = IpcClient.message("world");
                event.addProperty("generation", generation);
                event.addProperty("active", false);
                if (world != null) try {
                    int port = LocalWorld.open(world);
                    event.addProperty("active", true); event.addProperty("port", port); error = "";
                } catch (Exception exception) { error = "Local hosting: " + exception.getMessage(); }
                ipc.send(event);
            }
        } else {
            if (connecting && mc.level != null && mc.player != null && mc.getOverlay() == null
                    && !(mc.screen instanceof ReceivingLevelScreen)) {
                connecting = false; joined = true;
                sendGeneration("worldReady");
            } else if (connecting && (mc.screen instanceof DisconnectedScreen || System.nanoTime() - connectingSince > 45_000_000_000L)) {
                connecting = false; joined = false;
                error = Component.translatable("splitscreen_ultimate.join_failed").getString();
                JsonObject failed = IpcClient.message("joinFailed"); failed.addProperty("generation", generation);
                failed.addProperty("error", error); ipc.send(failed);
            }
            if (joined && (mc.level == null || mc.player == null)) {
                joined = active = false; ControllerSupport.disable(); external.clear(); sendGeneration("guestLeft");
            }
        }
    }

    private void input(ControllerFrame frame) {
        if (!registered) return;
        if (!config.main()) {
            if (Boolean.getBoolean("splitscreen.smoke") && active && frame.controllerInputActive() && observedGeneration != generation
                    && frame.controllers().stream().anyMatch(c -> c.info().connectionId().equals(external.connectionId())
                    && c.down().contains(ControllerButton.A))) {
                observedGeneration = generation;
                var observed = IpcClient.message("inputObserved"); observed.addProperty("generation", generation); ipc.send(observed);
            }
            return;
        }
        Set<UUID> ids = new HashSet<>();
        for (var device : frame.controllers()) ids.add(device.info().connectionId());
        players.disconnected(ids);
        for (var device : frame.controllers()) if (device.pressed().contains(ControllerButton.A)) {
            Players.Player player = players.claim(device.info().connectionId());
            if (player == null) continue;
            player.status = "Controller connected";
            if (player.slot == 1) ControllerSupport.assign(player.physical);
            else if (!player.launched && !player.request.isEmpty()) {
                JsonObject request = IpcClient.message("launch");
                request.addProperty("slot", player.slot); request.addProperty("route", player.route);
                request.addProperty("request", player.request); ipc.send(request);
            }
        }
        Set<String> names = new HashSet<>();
        for (Players.Player player : players.all()) {
            if (player.slot > 1) names.add("player" + player.slot);
            boolean connected = player.physical != null;
            if (!Objects.equals(connectedControllers.put(player.slot, connected), connected)) {
                JsonObject event = IpcClient.message("controllerStatus");
                event.addProperty("slot", player.slot); event.addProperty("connected", connected); ipc.send(event);
            }
            // Waiting/initializing guests cannot consume input. Avoid building a stale input backlog
            // while native embedding or resource loading temporarily occupies the launcher's UI thread.
            if (player.slot == 1 || player.physical == null || !player.world) continue;
            var device = frame.controllers().stream().filter(c -> c.info().connectionId().equals(player.physical)).findFirst().orElse(null);
            if (device == null) continue;
            long now = System.nanoTime();
            var previous = sent.get(player.slot);
            if (previous == null || !previous.down().equals(device.down()) || now - sentAt.getOrDefault(player.slot, 0L) >= 16_000_000L) {
                JsonObject message = IpcClient.message("input");
                message.addProperty("slot", player.slot); message.addProperty("route", player.route);
                message.addProperty("sequence", ++sequence);
                message.addProperty("generation", generation);
                message.add("sample", IpcClient.GSON.toJsonTree(new ControllerSample(device.axes(), device.down())));
                ipc.send(message); sent.put(player.slot, device); sentAt.put(player.slot, now);
                if (Boolean.getBoolean("splitscreen.smoke") && device.pressed().contains(ControllerButton.A))
                    SplitscreenUltimateMod.LOGGER.info("SPLITSCREEN_SMOKE forwarding A to player {}", player.slot);
            }
        }
        guestNames = Set.copyOf(names);
    }

    private void receive(JsonObject message) {
        if (shutdown) return;
        try {
            String type = message.get("type").getAsString();
            switch (type) {
                case "registered" -> registered = true;
                case "inputObserved" -> {
                    if (smoke != null && message.get("generation").getAsLong() == generation)
                        observedInput.add(message.get("slot").getAsInt());
                }
                case "shutdown" -> { shutdown = true; ControllerSupport.disable(); if (external != null) external.close(); ipc.close(); mc.stop(); }
                case "launchResult" -> {
                    if (config.main()) {
                        int slot = message.get("slot").getAsInt();
                        boolean ok = message.get("success").getAsBoolean();
                        if (players.launchResult(slot, message.get("request").getAsString(), ok) && !ok) {
                            error = message.get("error").getAsString();
                            connectedControllers.remove(slot);
                        }
                    }
                }
                case "guestExited" -> {
                    int slot = message.get("slot").getAsInt(); players.remove(slot);
                    connectedControllers.remove(slot); sent.remove(slot); sentAt.remove(slot);
                }
                case "status" -> {
                    List<String> lines = new ArrayList<>(); int launched = 0, ready = 0, inWorld = 0;
                    for (var item : message.getAsJsonArray("players")) {
                        var p = item.getAsJsonObject(); int slot = p.get("slot").getAsInt();
                        if (p.get("launched").getAsBoolean()) launched++;
                        if (p.get("ready").getAsBoolean()) ready++;
                        if (p.get("world").getAsBoolean()) inWorld++;
                        String status = p.get("status").getAsString();
                        lines.add(Component.translatable("splitscreen_ultimate.player_status", slot, status).getString());
                        var local = players.get(slot); if (local != null) {
                            local.launched = p.get("launched").getAsBoolean(); local.ready = p.get("ready").getAsBoolean(); local.world = p.get("world").getAsBoolean();
                        }
                    }
                    lines.addFirst(Component.translatable("splitscreen_ultimate.counts", launched, ready, inWorld).getString());
                    statusLines = List.copyOf(lines);
                }
                case "connect" -> {
                    if (config.main()) break;
                    long next = message.get("generation").getAsLong();
                    if (next < generation || (next == generation && (connecting || joined))) break;
                    generation = next; targetPort = message.get("port").getAsInt();
                    ControllerSupport.disable(); external.clear(); active = joined = false;
                    cancelConnecting();
                    if (mc.level != null) mc.level.disconnect();
                    mc.disconnect();
                    ConnectScreen.startConnecting(new TitleScreen(), mc,
                            ServerAddress.parseString("127.0.0.1:" + targetPort),
                            new ServerData("Local splitscreen", "127.0.0.1:" + targetPort, ServerData.Type.LAN), false, null);
                    connecting = true; connectingSince = System.nanoTime();
                }
                case "disconnect" -> {
                    if (config.main() || message.get("generation").getAsLong() < generation) break;
                    generation = message.get("generation").getAsLong();
                    connecting = joined = active = false; ControllerSupport.disable(); external.clear();
                    cancelConnecting();
                    if (mc.level != null) mc.level.disconnect();
                    mc.disconnect(new TitleScreen());
                }
                case "activate" -> {
                    if (!config.main() && message.get("generation").getAsLong() == generation) {
                        active = message.get("enabled").getAsBoolean() && joined;
                        external.clear();
                        if (active) ControllerSupport.assign(external.connectionId()); else ControllerSupport.disable();
                    }
                }
                case "controllerStatus" -> {
                    if (!config.main() && !message.get("connected").getAsBoolean()) {
                        external.clear(); error = Component.translatable("splitscreen_ultimate.controller_disconnected").getString();
                    } else error = "";
                }
                case "input" -> {
                    if (config.main() || !active || message.get("generation").getAsLong() != generation
                            || !config.route().equals(message.get("route").getAsString())) break;
                    long seq = message.get("sequence").getAsLong(); if (seq <= lastSequence) break;
                    lastSequence = seq;
                    external.submit(IpcClient.GSON.fromJson(message.get("sample"), ControllerSample.class));
                    if (Boolean.getBoolean("splitscreen.smoke") && message.getAsJsonObject("sample").getAsJsonArray("down").toString().contains("\"A\""))
                        SplitscreenUltimateMod.LOGGER.info("SPLITSCREEN_SMOKE guest received A (active {})", active);
                }
                default -> { }
            }
        } catch (Exception exception) {
            error = "Session message: " + exception.getMessage();
            SplitscreenUltimateMod.LOGGER.error("Session message failed", exception);
            if (!config.main()) { ControllerSupport.disable(); external.clear(); }
        }
    }
    private void sendGeneration(String type) { var event = IpcClient.message(type); event.addProperty("generation", generation); ipc.send(event); }
    private void drawError(GuiGraphics graphics) {
        if (config.main() || error.isEmpty() || mc.font == null) return;
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        var lines = mc.font.split(Component.literal(error), Math.max(40, width - 32));
        int top = height / 2 - lines.size() * 5;
        graphics.fill(8, top - 8, width - 8, top + lines.size() * 10 + 6, 0xCC000000);
        for (var line : lines) { graphics.drawString(mc.font, line, (width - mc.font.width(line)) / 2, top, 0xFFFFFF); top += 10; }
    }
    private void cancelConnecting() { if (mc.screen instanceof ConnectScreen screen) screen.onClose(); }
    public boolean expectedGuest(String name) { return guestNames.contains(name); }
    public void beforeDisconnect() {
        if (!config.main() || world == null || ipc == null) return;
        generation++; world = null; leavingWorld = true; observedInput.clear(); LocalWorld.clear();
        var event = IpcClient.message("world"); event.addProperty("generation", generation);
        event.addProperty("active", false); ipc.send(event);
    }
    void smokePublishLan() {
        try (var probe = new java.net.ServerSocket(0)) {
            int port = probe.getLocalPort(); probe.close();
            if (!LocalWorld.publish(world, net.minecraft.world.level.GameType.CREATIVE, false, port))
                throw new IllegalStateException("Smoke test could not open optional LAN listener");
            var published = IpcClient.message("lanPublished"); published.addProperty("port", port); ipc.send(published);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public List<String> statusLines() { return statusLines; }
    public String error() { return error; }
    public boolean main() { return config.main(); }
    public boolean mainHasController() { return players.get(1).physical != null; }
    public void retry() {
        error = "";
        if (registered) ipc.send(IpcClient.message("retry"));
        if (config.main() && world != null && LocalWorld.host == null) world = null;
    }
}
