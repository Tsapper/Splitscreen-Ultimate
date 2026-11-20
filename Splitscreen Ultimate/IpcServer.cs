using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Channels;
using System.Threading.Tasks;

namespace Splitscreen_Ultimate;

public sealed class IpcPeer : IDisposable
{
    private readonly TcpClient client;
    private readonly Channel<string> outgoing = Channel.CreateBounded<string>(512);
    public Instance? Instance { get; set; }
    public string CloseReason { get; private set; } = "Peer closed connection";
    public IpcPeer(TcpClient client) { this.client = client; }
    public bool Send(JsonObject message) {
        if (outgoing.Writer.TryWrite(message.ToJsonString())) return true;
        Dispose(); return false;
    }
    public async Task Run(Func<IpcPeer, JsonObject, Task> received, Action<IpcPeer> closed, CancellationToken cancellation) {
        try {
            client.NoDelay = true;
            var stream = client.GetStream();
            using var reader = new StreamReader(stream, new UTF8Encoding(false), false, 4096, true);
            using var writer = new StreamWriter(stream, new UTF8Encoding(false), 4096, true) { AutoFlush = true };
            _ = Task.Run(async () => {
                try { await foreach (var line in outgoing.Reader.ReadAllAsync(cancellation)) await writer.WriteLineAsync(line); }
                catch { Dispose(); }
            }, cancellation);
            while (!cancellation.IsCancellationRequested) {
                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellation);
                if (Instance == null) timeout.CancelAfter(TimeSpan.FromSeconds(10));
                string? line = await ReadBoundedLine(reader, timeout.Token); if (line == null) break;
                await received(this, JsonNode.Parse(line)?.AsObject() ?? throw new IOException("Invalid IPC JSON"));
            }
        }
        catch (Exception e) when (e is IOException or SocketException or OperationCanceledException or System.Text.Json.JsonException or InvalidOperationException or ObjectDisposedException) { CloseReason = e.Message; }
        finally { Dispose(); closed(this); }
    }
    private static async Task<string?> ReadBoundedLine(StreamReader reader, CancellationToken token) {
        var text = new StringBuilder(); var buffer = new char[1];
        while (await reader.ReadAsync(buffer.AsMemory(), token) != 0) {
            if (buffer[0] == '\n') return text.ToString();
            if (buffer[0] != '\r') text.Append(buffer[0]);
            if (text.Length > 65536) throw new IOException("IPC frame too large");
        }
        return text.Length == 0 ? null : text.ToString();
    }
    public void Dispose() { outgoing.Writer.TryComplete(); client.Dispose(); }
}
public sealed class IpcServer : IDisposable
{
    private readonly TcpListener listener = new(IPAddress.Loopback, 0);
    private readonly CancellationTokenSource cancellation = new();
    public int Port => ((IPEndPoint)listener.LocalEndpoint).Port;
    public IpcServer(Func<IpcPeer, JsonObject, Task> received, Action<IpcPeer> closed) {
        listener.Start();
        _ = Task.Run(async () => {
            try { while (!cancellation.IsCancellationRequested) {
                var client = await listener.AcceptTcpClientAsync(cancellation.Token);
                _ = new IpcPeer(client).Run(received, closed, cancellation.Token);
            } } catch (OperationCanceledException) { }
        });
    }
    public void Dispose() { cancellation.Cancel(); listener.Stop(); }
}
