using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.ProcessBuilder;
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace Splitscreen_Ultimate;

public sealed class Launcher
{
    private readonly SemaphoreSlim installLock = new(1);
    private readonly HttpClient http = new();
    private string? version;
    public string Workspace { get; } = FindWorkspace();
    public string DataDirectory => Path.Combine(Workspace, "Splitscreen Ultimate");
    private static string FindWorkspace()
    {
        foreach (var start in new[] { Environment.CurrentDirectory, AppContext.BaseDirectory })
            for (var dir = new DirectoryInfo(start); dir != null; dir = dir.Parent)
                if (Directory.Exists(Path.Combine(dir.FullName, "simple_controller_support")) &&
                    Directory.Exists(Path.Combine(dir.FullName, "splitscreen_ultimate"))) return dir.FullName;
        throw new DirectoryNotFoundException("Run from the Splitscreen Minecraft workspace.");
    }
    public async Task<Process> Launch(Instance instance, string session, int port, CancellationToken cancellation)
    {
        string gameDir = Path.Combine(DataDirectory, "player" + instance.Slot);
        Directory.CreateDirectory(gameDir);
        string shared = Path.Combine(DataDirectory, "minecraft");
        var path = new MinecraftPath(gameDir) {
            Library = Path.Combine(shared, "libraries"), Versions = Path.Combine(shared, "versions"),
            Assets = Path.Combine(shared, "assets"), Runtime = Path.Combine(shared, "runtime")
        };
        var launcher = new MinecraftLauncher(path);
        await installLock.WaitAsync(cancellation);
        try
        {
            StageMods(gameDir);
            if (version == null)
            {
                string profile = await http.GetStringAsync("https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.19.5/profile/json", cancellation);
                string id = JsonNode.Parse(profile)!["id"]!.GetValue<string>();
                string directory = Path.Combine(path.Versions, id); Directory.CreateDirectory(directory);
                await File.WriteAllTextAsync(Path.Combine(directory, id + ".json"), profile, cancellation);
                await launcher.InstallAsync(id, cancellation);
                version = id;
            }
        }
        finally { installLock.Release(); }
        cancellation.ThrowIfCancellationRequested();
        var properties = new[] { $"-Dsplitscreen.session={session}", $"-Dsplitscreen.instance={instance.Id}",
            $"-Dsplitscreen.slot={instance.Slot}", $"-Dsplitscreen.role={(instance.Slot == 1 ? "main" : "guest")}",
            $"-Dsplitscreen.port={port}", $"-Dsplitscreen.token={instance.Token}", $"-Dsplitscreen.controller={instance.Route}" };
        if (Environment.GetCommandLineArgs().Contains("--prototype-smoke-test"))
            properties = properties.Append("-Dsplitscreen.smoke=true").ToArray();
        var process = await launcher.CreateProcessAsync(version, new MLaunchOption {
            Session = MSession.CreateOfflineSession("player" + instance.Slot), MaximumRamMb = 2048,
            ScreenWidth = 960, ScreenHeight = 540, FullScreen = false,
            ExtraJvmArguments = properties.Select(p => new MArgument(p))
        });
        process.StartInfo.WorkingDirectory = gameDir;
        process.StartInfo.RedirectStandardOutput = process.StartInfo.RedirectStandardError = true;
        process.StartInfo.UseShellExecute = false;
        var log = new StreamWriter(Path.Combine(gameDir, "launcher-console.log"), false) { AutoFlush = true };
        object logLock = new();
        void Log(string? value) { if (value != null) lock (logLock) { try { log.WriteLine(value); } catch (ObjectDisposedException) { } } }
        process.OutputDataReceived += (_, e) => Log(e.Data); process.ErrorDataReceived += (_, e) => Log(e.Data);
        process.EnableRaisingEvents = true;
        process.Exited += (_, _) => { lock (logLock) log.Dispose(); };
        cancellation.ThrowIfCancellationRequested();
        try { process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine(); return process; }
        catch { log.Dispose(); throw; }
    }
    private void StageMods(string gameDir)
    {
        string cache = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".gradle", "caches", "modules-2", "files-2.1");
        string Cached(string group, string artifact, string version) {
            string root = Path.Combine(cache, group, artifact, version);
            return (Directory.Exists(root) ? Directory.EnumerateFiles(root, artifact + "-" + version + ".jar", SearchOption.AllDirectories).FirstOrDefault() : null)
                ?? throw new FileNotFoundException($"Missing cached {artifact} {version}; build the Fabric mods first.");
        }
        string[] jars = {
            Path.Combine(Workspace, "simple_controller_support", "fabric", "build", "libs", "simple_controller_support-fabric-1.1.0.jar"),
            Path.Combine(Workspace, "splitscreen_ultimate", "fabric", "build", "libs", "splitscreen_ultimate-fabric-1.0.0.jar"),
            Cached("dev.architectury", "architectury-fabric", "13.0.11"),
            Cached("net.fabricmc.fabric-api", "fabric-api", "0.116.17+1.21.1") };
        foreach (string jar in jars) if (!File.Exists(jar)) throw new FileNotFoundException("Build the prototype first: " + jar);
        string mods = Path.Combine(gameDir, "mods"); Directory.CreateDirectory(mods);
        string[] managed = { "simple_controller_support", "splitscreen_ultimate", "architectury", "fabric-api" };
        foreach (string old in Directory.EnumerateFiles(mods, "*.jar")) {
            using var zip = ZipFile.OpenRead(old); var metadata = zip.GetEntry("fabric.mod.json"); if (metadata == null) continue;
            using var reader = new StreamReader(metadata.Open());
            string? id = JsonNode.Parse(reader.ReadToEnd())?["id"]?.GetValue<string>();
            if (id != null && managed.Contains(id)) { reader.Dispose(); zip.Dispose(); File.Delete(old); }
        }
        foreach (string jar in jars) File.Copy(jar, Path.Combine(mods, Path.GetFileName(jar)), true);
        string options = Path.Combine(gameDir, "options.txt");
        var settings = File.Exists(options) ? File.ReadAllLines(options) : Array.Empty<string>();
        if (Environment.GetCommandLineArgs().Contains("--prototype-smoke-test")) {
            settings = settings.Where(l => !l.StartsWith("renderDistance:") && !l.StartsWith("simulationDistance:") && !l.StartsWith("maxFps:"))
                .Concat(new[] { "renderDistance:4", "simulationDistance:4", "maxFps:30" }).ToArray();
        }
        File.WriteAllLines(options, settings.Where(l => !l.StartsWith("fullscreen:") && !l.StartsWith("onboardAccessibility:"))
            .Append("fullscreen:false").Append("onboardAccessibility:false"));
    }
}
