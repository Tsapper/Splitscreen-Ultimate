using System;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.Json.Nodes;

namespace Splitscreen_Ultimate;

public sealed class Instance
{
    public int Slot { get; init; }
    public string Id { get; } = Guid.NewGuid().ToString();
    public string Token { get; } = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
    public string Route { get; init; } = "";
    public string Request { get; init; } = "";
    public Process? Process { get; set; }
    public IpcPeer? Peer { get; set; }
    public IntPtr Hwnd { get; set; }
    public bool Embedded { get; set; }
    public bool InputActive { get; set; }
    public bool WorldReady { get; set; }
    public bool ControllerConnected { get; set; }
    public string Status { get; set; } = "Launching";
    public long CreatedAt { get; } = Environment.TickCount64;
    public long StartedAt { get; set; }
}
public readonly record struct Tile(int Row, int Column, int Rows, int Columns);
public static class SessionRules
{
    public static bool IsCurrentWorld(long incoming, long current, bool active) => active && incoming == current;
    public static Tile[] Layout(int count) => count switch {
        1 => new[] { new Tile(0, 0, 2, 2) },
        2 => new[] { new Tile(0, 0, 1, 2), new Tile(1, 0, 1, 2) },
        3 => new[] { new Tile(0, 0, 1, 2), new Tile(1, 0, 1, 1), new Tile(1, 1, 1, 1) },
        4 => new[] { new Tile(0, 0, 1, 1), new Tile(0, 1, 1, 1), new Tile(1, 0, 1, 1), new Tile(1, 1, 1, 1) },
        _ => throw new ArgumentOutOfRangeException(nameof(count)) };
    public static bool Authenticates(JsonObject hello, string session, Instance instance) {
        try { return hello["type"]?.GetValue<string>() == "hello" && hello["version"]?.GetValue<int>() == 1 &&
            hello["session"]?.GetValue<string>() == session && hello["instance"]?.GetValue<string>() == instance.Id &&
            hello["slot"]?.GetValue<int>() == instance.Slot &&
            CryptographicOperations.FixedTimeEquals(System.Text.Encoding.UTF8.GetBytes(hello["token"]!.GetValue<string>()),
                System.Text.Encoding.UTF8.GetBytes(instance.Token)); }
        catch { return false; }
    }
    public static JsonObject Message(string type) => new() { ["type"] = type };
}
