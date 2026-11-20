using Avalonia.Controls;
using Avalonia.Threading;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Linq;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace Splitscreen_Ultimate;

public partial class MainWindow : Window
{
    private readonly SortedDictionary<int, Instance> instances = new();
    private readonly CancellationTokenSource lifetime = new();
    private readonly string session = Guid.NewGuid().ToString();
    private Launcher? launcher;
    private IpcServer? server;
    private readonly DispatcherTimer watchdog = new() { Interval = TimeSpan.FromSeconds(1) };
    private bool stopping, closed;
    private Task? stopTask;
    private long generation;
    private int worldPort;
    private bool Smoke => Environment.GetCommandLineArgs().Contains("--prototype-smoke-test");
    private void Record(string text) {
        if (launcher != null) File.AppendAllText(Path.Combine(launcher.DataDirectory, "prototype-session.log"), DateTimeOffset.Now + " " + text + Environment.NewLine);
    }
    private MinecraftHost[] Hosts => new[] { Host1, Host2, Host3, Host4 };

    public MainWindow()
    {
        InitializeComponent();
        Surface.SizeChanged += (_, _) => LayoutWindows();
        foreach (var host in Hosts) host.SizeChanged += (_, _) => LayoutWindows();
        Loaded += async (_, _) => {
            try {
                launcher = new Launcher();
                Record("Session started");
                server = new IpcServer((peer, message) => Dispatcher.UIThread.InvokeAsync(() => Receive(peer, message)).GetTask(),
                    peer => Dispatcher.UIThread.Post(() => PeerClosed(peer)));
                watchdog.Tick += (_, _) => CheckInstances(); watchdog.Start();
                var main = new Instance { Slot = 1 }; instances.Add(1, main);
                await Start(main);
            } catch (Exception e) { SessionStatus.Text = e.Message; Record("Startup failed: " + e.Message); }
        };
        Closing += async (_, e) => {
            if (closed) return;
            e.Cancel = true;
            await Stop(); closed = true; Close();
        };
    }

    private async Task Start(Instance instance)
    {
        try {
            var process = await launcher!.Launch(instance, session, server!.Port, lifetime.Token);
            instance.Process = process;
            instance.StartedAt = Environment.TickCount64;
            Record($"Player {instance.Slot} process {process.Id} started");
            if (stopping || !instances.TryGetValue(instance.Slot, out var current) || current != instance) {
                if (!process.HasExited) process.Kill(true); return;
            }
            process.Exited += (_, _) => Dispatcher.UIThread.Post(() => Exited(instance));
            if (process.HasExited) Exited(instance);
            else { instance.Status = "Starting Minecraft"; SendLaunchResult(instance, true); BroadcastStatus(); }
        } catch (Exception e) {
            if (stopping) return;
            SendLaunchResult(instance, false, e.Message);
            instances.Remove(instance.Slot); SessionStatus.Text = "Launch failed: " + e.Message;
            Record($"Player {instance.Slot} launch failed: {e.Message}");
            BroadcastStatus();
            if (instance.Slot == 1 && Smoke) _ = FinishSmoke();
        }
    }

    private void Receive(IpcPeer peer, JsonObject message)
    {
        if (stopping) { peer.Dispose(); return; }
        try {
            if (peer.Instance == null) {
                int slot = message["slot"]!.GetValue<int>();
                if (!instances.TryGetValue(slot, out var instance) || instance.Peer != null || !SessionRules.Authenticates(message, session, instance)) {
                    peer.Dispose(); return;
                }
                if (!ulong.TryParse(message["hwnd"]!.GetValue<string>(), out ulong raw) || raw == 0) { peer.Dispose(); return; }
                var hwnd = new IntPtr(unchecked((long)raw));
                if (!WindowEmbedder.BelongsToProcess(hwnd, instance.Process?.Id)) { peer.Dispose(); return; }
                instance.Peer = peer; peer.Instance = instance; instance.Hwnd = hwnd;
                Record($"Player {slot} registered");
                instance.Status = "Ready — waiting for world";
                WindowEmbedder.SetVisible(hwnd, false);
                LayoutWindows();
                peer.Send(SessionRules.Message("registered"));
                if (slot > 1 && worldPort > 0) Connect(instance);
                BroadcastStatus(); return;
            }
            var sender = peer.Instance;
            if (!instances.TryGetValue(sender.Slot, out var live) || live != sender || live.Peer != peer) return;
            string type = message["type"]!.GetValue<string>();
            switch (type) {
                case "launch" when sender.Slot == 1:
                    int slot = message["slot"]!.GetValue<int>();
                    string request = message["request"]!.GetValue<string>();
                    if (slot < 2 || slot > 4 || instances.Count >= 4 || instances.ContainsKey(slot)) break;
                    var guest = new Instance { Slot = slot, Request = request, Route = message["route"]!.GetValue<string>() };
                    instances.Add(slot, guest); BroadcastStatus(); _ = Start(guest); break;
                case "world" when sender.Slot == 1:
                    long next = message["generation"]!.GetValue<long>(); if (next <= generation) break;
                    generation = next; worldPort = message["active"]!.GetValue<bool>() ? message["port"]!.GetValue<int>() : 0;
                    Record($"World generation {generation}, private port {worldPort}");
                    if (Smoke && worldPort > 0) {
                        var listeners = IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners().Where(p => p.Port == worldPort).ToArray();
                        if (listeners.Length == 0 || listeners.Any(p => !IPAddress.IsLoopback(p.Address))) throw new InvalidOperationException("Smoke test: private listener is not loopback-only");
                        Record("Verified private listener is loopback-only");
                    }
                    sender.WorldReady = worldPort > 0;
                    foreach (var other in instances.Values.Where(i => i.Slot > 1)) {
                        other.WorldReady = false; Hide(other);
                        if (worldPort > 0) Connect(other); else {
                            var disconnect = GenerationMessage("disconnect"); other.Peer?.Send(disconnect);
                            other.Status = "Ready — waiting for world";
                        }
                    }
                    LayoutWindows(); BroadcastStatus(); break;
                case "worldReady" when sender.Slot > 1:
                    if (!CurrentWorld(message)) break;
                    sender.WorldReady = true; sender.Status = "In world";
                    Record($"Player {sender.Slot} joined generation {generation}");
                    LayoutWindows(); BroadcastStatus(); break;
                case "joinFailed" when sender.Slot > 1:
                    if (!CurrentWorld(message)) break;
                    sender.WorldReady = false; sender.Status = message["error"]?.GetValue<string>() ?? "Join failed — retry guests";
                    Hide(sender); LayoutWindows(); BroadcastStatus(); break;
                case "guestLeft" when sender.Slot > 1:
                    if (!CurrentWorld(message)) break;
                    sender.WorldReady = false; sender.Status = "Disconnected — retry guests";
                    Hide(sender); LayoutWindows(); BroadcastStatus(); break;
                case "controllerStatus" when sender.Slot == 1:
                    if (instances.TryGetValue(message["slot"]!.GetValue<int>(), out var owner)) {
                        owner.ControllerConnected = message["connected"]!.GetValue<bool>();
                        if (!owner.ControllerConnected) owner.Status = "Controller disconnected — press A to reconnect";
                        else owner.Status = owner.WorldReady ? "In world" : "Ready — waiting for world";
                        owner.Peer?.Send(message); BroadcastStatus();
                    }
                    break;
                case "input" when sender.Slot == 1:
                    if (CurrentWorld(message) && instances.TryGetValue(message["slot"]!.GetValue<int>(), out var target) && target.Slot > 1 &&
                        target.WorldReady && message["route"]!.GetValue<string>() == target.Route) target.Peer?.Send(message);
                    break;
                case "retry" when sender.Slot == 1:
                    foreach (var other in instances.Values.Where(i => i.Slot > 1 && !i.WorldReady)) Connect(other);
                    break;
                case "inputObserved" when Smoke && sender.Slot > 1:
                    if (CurrentWorld(message) && instances.TryGetValue(1, out var observer)) {
                        message["slot"] = sender.Slot; observer.Peer?.Send(message);
                        Record($"Player {sender.Slot} applied controller input in generation {generation}");
                    }
                    break;
                case "lanPublished" when Smoke && sender.Slot == 1:
                    int lanPort = message["port"]!.GetValue<int>();
                    bool wildcard = IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners()
                        .Any(p => p.Port == lanPort && (p.Address.Equals(IPAddress.Any) || p.Address.Equals(IPAddress.IPv6Any)));
                    if (!wildcard) throw new InvalidOperationException("Smoke test: LAN listener is not network-facing");
                    Record($"Verified optional LAN listener on port {lanPort}");
                    break;
            }
        } catch (Exception e) {
            SessionStatus.Text = "Session protocol error: " + e.Message; peer.Dispose();
            Record("Protocol error: " + e.Message);
        }
    }

    private bool CurrentWorld(JsonObject message) => SessionRules.IsCurrentWorld(message["generation"]!.GetValue<long>(), generation, worldPort > 0);
    private JsonObject GenerationMessage(string type) { var m = SessionRules.Message(type); m["generation"] = generation; return m; }
    private void Connect(Instance guest)
    {
        if (worldPort <= 0 || guest.Peer == null) return;
        guest.WorldReady = false; guest.Status = "Connecting to host world"; Hide(guest);
        var connect = GenerationMessage("connect"); connect["port"] = worldPort; guest.Peer.Send(connect);
    }
    private void Hide(Instance instance)
    {
        instance.InputActive = false;
        var inactive = GenerationMessage("activate"); inactive["enabled"] = false; instance.Peer?.Send(inactive);
        WindowEmbedder.SetVisible(instance.Hwnd, false);
        WindowEmbedder.SetEnabled(instance.Hwnd, false);
    }
    private void LayoutWindows()
    {
        if (stopping || Surface.Bounds.Width <= 0 || Surface.Bounds.Height <= 0) return;
        var visible = instances.Values.Where(i => i.Slot == 1 || worldPort > 0 && i.WorldReady).OrderBy(i => i.Slot).ToArray();
        if (visible.Length == 0) return;
        var tiles = SessionRules.Layout(visible.Length);
        for (int slot = 1; slot <= 4; slot++) {
            var host = Hosts[slot - 1];
            int index = Array.FindIndex(visible, i => i.Slot == slot);
            if (index < 0) { Canvas.SetLeft(host, -10000); continue; }
            Tile tile = tiles[index];
            double width = Surface.Bounds.Width * tile.Columns / 2;
            double height = Surface.Bounds.Height * tile.Rows / 2;
            if (host.Width != width) host.Width = width;
            if (host.Height != height) host.Height = height;
            Canvas.SetLeft(host, Surface.Bounds.Width * tile.Column / 2); Canvas.SetTop(host, Surface.Bounds.Height * tile.Row / 2);
        }
        foreach (var instance in instances.Values) {
            var host = Hosts[instance.Slot - 1];
            if (instance.Hwnd == IntPtr.Zero || host.ContainerHwnd == IntPtr.Zero) continue;
            int width = Math.Max(1, (int)(host.Width * RenderScaling)); int height = Math.Max(1, (int)(host.Height * RenderScaling));
            if (!instance.Embedded) {
                WindowEmbedder.Embed(instance.Hwnd, host.ContainerHwnd, 0, 0, width, height); instance.Embedded = true;
            } else WindowEmbedder.Resize(instance.Hwnd, width, height);
            bool show = instance.Slot == 1 || worldPort > 0 && instance.WorldReady;
            WindowEmbedder.SetVisible(instance.Hwnd, show);
            WindowEmbedder.SetEnabled(instance.Hwnd, instance.Slot == 1);
            if (show && instance.Slot > 1 && !instance.InputActive) {
                instance.InputActive = true;
                var enable = GenerationMessage("activate"); enable["enabled"] = true; instance.Peer?.Send(enable);
            }
        }
    }
    private void BroadcastStatus()
    {
        var status = SessionRules.Message("status"); var players = new JsonArray();
        foreach (var instance in instances.Values) players.Add(new JsonObject {
            ["slot"] = instance.Slot, ["launched"] = instance.Process is { HasExited: false },
            ["ready"] = instance.Peer != null, ["world"] = instance.WorldReady, ["status"] = instance.Status });
        status["players"] = players;
        foreach (var instance in instances.Values) instance.Peer?.Send(status);
        SessionStatus.Text = $"{instances.Values.Count(i => i.Process is { HasExited: false })}/4 players launched · {instances.Values.Count(i => i.WorldReady)} in world · Press A on another controller to join";
    }
    private void SendLaunchResult(Instance instance, bool success, string error = "")
    {
        if (instance.Slot == 1) return;
        var result = SessionRules.Message("launchResult"); result["slot"] = instance.Slot; result["request"] = instance.Request;
        result["success"] = success; result["error"] = error;
        if (instances.TryGetValue(1, out var main)) main.Peer?.Send(result);
    }
    private void PeerClosed(IpcPeer peer)
    {
        var instance = peer.Instance;
        if (stopping || instance == null || instance.Peer != peer) return;
        Record($"Player {instance.Slot} IPC closed: {peer.CloseReason}");
        instance.Peer = null; instance.WorldReady = false; Hide(instance);
        if (instance.Slot == 1) { _ = Stop(); if (Smoke) _ = FinishSmoke(); SessionStatus.Text = "Main instance disconnected. Restart the launcher to begin a new session."; }
        else { Kill(instance); Exited(instance); }
    }
    private void Exited(Instance instance)
    {
        if (stopping || !instances.TryGetValue(instance.Slot, out var live) || live != instance) return;
        instance.Peer?.Dispose(); Hide(instance); instances.Remove(instance.Slot);
        Record($"Player {instance.Slot} exited");
        if (instance.Slot == 1) { _ = Stop(); if (Smoke) _ = FinishSmoke(); SessionStatus.Text = "Player one exited. Restart the launcher to begin a new session."; }
        else {
            var exited = SessionRules.Message("guestExited"); exited["slot"] = instance.Slot;
            if (instances.TryGetValue(1, out var main)) main.Peer?.Send(exited);
            LayoutWindows(); BroadcastStatus();
        }
    }
    private void CheckInstances()
    {
        foreach (var instance in instances.Values.ToArray()) {
            if (instance.Process?.HasExited == true) Exited(instance);
            else if (instance.Process != null && instance.Peer == null && Environment.TickCount64 - instance.StartedAt > 180000) {
                SessionStatus.Text = $"Player {instance.Slot} did not register; check player{instance.Slot}/launcher-console.log";
                Kill(instance); Exited(instance);
            }
        }
        LayoutWindows();
    }
    private static void Kill(Instance instance) { try { if (instance.Process is { HasExited: false } process) process.Kill(true); } catch (InvalidOperationException) { } }
    private Task Stop() => stopTask ??= StopCore();
    private async Task FinishSmoke() { await Stop(); closed = true; Close(); }
    private async Task StopCore()
    {
        if (stopping) return;
        stopping = true; watchdog.Stop(); lifetime.Cancel();
        var owned = instances.Values.ToArray();
        foreach (var instance in owned) { Hide(instance); instance.Peer?.Send(SessionRules.Message("shutdown")); }
        await Task.WhenAll(owned.Select(async instance => {
            if (instance.Process is not { } process) return;
            try { using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(8)); await process.WaitForExitAsync(timeout.Token); }
            catch (OperationCanceledException) { Kill(instance); }
            catch (InvalidOperationException) { }
        }));
        foreach (var instance in owned) instance.Peer?.Dispose(); server?.Dispose();
    }
}
