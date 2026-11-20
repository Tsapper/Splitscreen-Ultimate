using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading.Tasks;

namespace Splitscreen_Ultimate
{
    public static class WindowEmbedder
    {
        [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr hwnd, int command);
        [DllImport("user32.dll")] static extern bool EnableWindow(IntPtr hwnd, bool enabled);
        [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint process);
        public static bool BelongsToProcess(IntPtr hwnd, int? process) {
            GetWindowThreadProcessId(hwnd, out uint actual); return process != null && actual == process;
        }
        public static void SetVisible(IntPtr hwnd, bool visible) { if (hwnd != IntPtr.Zero) ShowWindow(hwnd, visible ? 8 : 0); }
        public static void SetEnabled(IntPtr hwnd, bool enabled) { if (hwnd != IntPtr.Zero) EnableWindow(hwnd, enabled); }
        [DllImport("user32.dll")]
        static extern IntPtr SetParent(IntPtr hWndChild, IntPtr hWndNewParent);

        [DllImport("user32.dll")]
        static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);

        [DllImport("user32.dll")]
        static extern bool MoveWindow(IntPtr hWnd, int X, int Y, int nWidth, int nHeight, bool bRepaint);

        [DllImport("user32.dll")]
        static extern int GetWindowLong(IntPtr hWnd, int nIndex);

        [DllImport("user32.dll")]
        static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

        public static void Resize(IntPtr hwnd, int width, int height)
            => SetWindowPos(hwnd, IntPtr.Zero, 0, 0, width, height, SWP_NOZORDER | SWP_NOACTIVATE | 0x4000);

        const int GWL_STYLE = -16;
        const int WS_CHILD = 0x40000000;
        const int WS_VISIBLE = 0x10000000;
        const uint WS_CAPTION = 0x00C00000;
        const uint WS_THICKFRAME = 0x00040000;
        const uint WS_CLIPSIBLINGS = 0x04000000;
        const uint WS_CLIPCHILDREN = 0x02000000;
        const uint WS_SYSMENU = 0x00080000;    // system menu (also removes min/max/close buttons)
        const uint SWP_NOZORDER = 0x0004;
        const uint SWP_NOACTIVATE = 0x0010;
        const uint SWP_FRAMECHANGED = 0x0020;

        // Poll until the JVM's window actually exists
        public static async Task<IntPtr> WaitForWindowHandle(Process process, int timeoutMs = 15000)
        {
            var start = DateTime.UtcNow;
            while ((DateTime.UtcNow - start).TotalMilliseconds < timeoutMs)
            {
                process.Refresh();
                if (process.MainWindowHandle != IntPtr.Zero)
                    return process.MainWindowHandle;

                await Task.Delay(100);
            }
            throw new TimeoutException("Minecraft window did not appear in time.");
        }

        public static void Embed(IntPtr childHwnd, IntPtr parentHwnd, int x, int y, int width, int height)
        {
            int style = GetWindowLong(childHwnd, GWL_STYLE);
            style &= ~(int)(WS_CAPTION | WS_THICKFRAME | WS_SYSMENU);
            style |= WS_CHILD | (int)WS_CLIPSIBLINGS;
            SetWindowLong(childHwnd, GWL_STYLE, style);

            SetParent(childHwnd, parentHwnd);

            SetWindowPos(childHwnd, IntPtr.Zero, x, y, width, height,
                SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED | 0x4000);
        }
    }
}
