param([int]$Seconds = 90)
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class PrototypeWindowInspection {
    public delegate bool EnumCallback(IntPtr hwnd, IntPtr parameter);
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
    public record Info(int Pid, bool Visible, bool Enabled, int Left, int Top, int Width, int Height);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumCallback callback, IntPtr parameter);
    [DllImport("user32.dll")] static extern bool EnumChildWindows(IntPtr parent, EnumCallback callback, IntPtr parameter);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hwnd, out int pid);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr hwnd);
    [DllImport("user32.dll")] static extern bool IsWindowEnabled(IntPtr hwnd);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr hwnd, out Rect rect);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr hwnd, StringBuilder text, int count);
    public static List<Info> Inspect(int[] pids) {
        var result = new List<Info>();
        EnumCallback inspect = (hwnd, parameter) => {
            GetWindowThreadProcessId(hwnd, out int pid);
            if (Array.IndexOf(pids, pid) < 0) return true;
            var text = new StringBuilder(128); GetClassName(hwnd, text, 128);
            GetWindowRect(hwnd, out Rect r);
            if (text.ToString().StartsWith("GLFW") && r.Right-r.Left > 100 && r.Bottom-r.Top > 100)
                result.Add(new Info(pid, IsWindowVisible(hwnd), IsWindowEnabled(hwnd), r.Left, r.Top, r.Right-r.Left, r.Bottom-r.Top));
            return true;
        };
        EnumWindows((hwnd, parameter) => { inspect(hwnd, parameter); EnumChildWindows(hwnd, inspect, parameter); return true; }, IntPtr.Zero);
        return result;
    }
}
'@
$logPath = Join-Path $PSScriptRoot '../Splitscreen Ultimate/prototype-session.log'
$deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
$sawFour = $false
$sawHidden = $false
$previous = ''
while ([DateTime]::UtcNow -lt $deadline) {
    $lines = Get-Content -LiteralPath $logPath
    $lastSession = ($lines | Select-String 'Session started' | Select-Object -Last 1).LineNumber
    $owners = @{}
    foreach ($line in ($lines | Select-Object -Skip $lastSession)) {
        if ($line -match 'Player ([1-4]) process (\d+) started') { $owners[[int]$Matches[2]] = [int]$Matches[1] }
    }
    if ($owners.Count -gt 0) {
        $windows = @([PrototypeWindowInspection]::Inspect([int[]]@($owners.Keys)))
        $visible = @($windows | Where-Object Visible)
        $signature = ($windows | Sort-Object Pid | ForEach-Object { "$($owners[$_.Pid]):$($_.Visible):$($_.Enabled):$($_.Width)x$($_.Height)" }) -join ' '
        if ($signature -ne $previous) { Write-Output $signature }
        if ($signature -eq $previous -and $visible.Count -eq 4) {
            $widths = $visible | Measure-Object Width -Minimum -Maximum
            $heights = $visible | Measure-Object Height -Minimum -Maximum
            if ($widths.Maximum - $widths.Minimum -gt 1 -or $heights.Maximum - $heights.Minimum -gt 1) { throw 'Four-player native tiles are unequal.' }
            if (@($visible | Where-Object { $owners[$_.Pid] -gt 1 -and $_.Enabled }).Count) { throw 'Guest native window accepts physical input.' }
            $sawFour = $true
        }
        if ($signature -eq $previous -and $sawFour -and $visible.Count -eq 1 -and $owners[$visible[0].Pid] -eq 1) { $sawHidden = $true }
        $previous = $signature
        if ($sawFour -and $sawHidden) { Write-Output 'PASS: native four-player equal tiles, disabled guest windows, and main-only visibility after world exit.'; exit 0 }
    }
    Start-Sleep -Milliseconds 200
}
throw 'Did not observe both settled four-player and hidden-guest phases within the timeout.'
