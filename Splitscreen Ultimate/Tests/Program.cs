using System;
using System.Linq;
using System.Net.Sockets;
using System.IO;
using System.Text.Json.Nodes;
using System.Threading.Tasks;
using Splitscreen_Ultimate;

static void Check(bool value, string description) { if (!value) throw new Exception(description); }
for (int count = 1; count <= 4; count++) {
    var tiles = SessionRules.Layout(count);
    Check(tiles.Length == count, "Tile count");
    Check(tiles.Sum(t => t.Rows * t.Columns) == 4, "Full coverage");
    for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
        Check(tiles.Count(t => row >= t.Row && row < t.Row + t.Rows && col >= t.Column && col < t.Column + t.Columns) == 1, "No gaps or overlap");
}
Check(SessionRules.Layout(3)[0] == new Tile(0, 0, 1, 2), "Three-player host top half");
var instance = new Instance { Slot = 2 };
var hello = new JsonObject { ["type"] = "hello", ["version"] = 1, ["session"] = "session", ["instance"] = instance.Id, ["slot"] = 2, ["token"] = instance.Token };
Check(SessionRules.Authenticates(hello, "session", instance), "Valid registration");
hello["token"] = "wrong"; Check(!SessionRules.Authenticates(hello, "session", instance), "Reject wrong token");
hello["token"] = instance.Token; hello["slot"] = 3;
Check(!SessionRules.Authenticates(hello, "session", instance), "Reject wrong instance slot");
hello["slot"] = 2; hello["version"] = 2;
Check(!SessionRules.Authenticates(hello, "session", instance), "Reject unsupported protocol");
Check(!SessionRules.IsCurrentWorld(1, 2, true), "Reject old-world readiness");
Check(!SessionRules.IsCurrentWorld(2, 2, false), "Reject readiness after world closes");
Check(SessionRules.IsCurrentWorld(2, 2, true), "Accept current readiness");

var received = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
var disconnected = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
using (var server = new IpcServer((peer, message) => {
    received.TrySetResult(message["type"]!.GetValue<string>());
    peer.Send(new JsonObject { ["type"] = "reply" });
    return Task.CompletedTask;
}, _ => disconnected.TrySetResult())) {
    using (var client = new TcpClient()) {
        await client.ConnectAsync("127.0.0.1", server.Port);
        using var writer = new StreamWriter(client.GetStream(), leaveOpen: true) { AutoFlush = true };
        using var reader = new StreamReader(client.GetStream(), leaveOpen: true);
        await writer.WriteLineAsync("{\"type\":\"probe\"}");
        Check(await received.Task.WaitAsync(TimeSpan.FromSeconds(5)) == "probe", "IPC receives JSON");
        Check(JsonNode.Parse((await reader.ReadLineAsync())!)!["type"]!.GetValue<string>() == "reply", "IPC replies");
    }
    await disconnected.Task.WaitAsync(TimeSpan.FromSeconds(5));
}
Console.WriteLine("Passed: four layouts, authentication, world generations, IPC framing/replies/disconnect cleanup.");
