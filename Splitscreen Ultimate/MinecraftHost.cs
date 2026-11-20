using Avalonia.Controls;
using Avalonia.Platform;
using System;
using System.Runtime.InteropServices;

namespace Splitscreen_Ultimate
{
    public class MinecraftHost : NativeControlHost
    {
        public IntPtr ContainerHwnd { get; private set; }

        protected override IPlatformHandle CreateNativeControlCore(IPlatformHandle parent)
        {
            ContainerHwnd = CreateWindowEx(
                0, "STATIC", "", WS_CHILD | WS_VISIBLE,
                0, 0, 100, 100,
                parent.Handle, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);

            return new PlatformHandle(ContainerHwnd, "HWND");
        }

        protected override void DestroyNativeControlCore(IPlatformHandle control)
        {
            DestroyWindow(control.Handle);
        }

        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        static extern IntPtr CreateWindowEx(int dwExStyle, string lpClassName, string lpWindowName,
            int dwStyle, int x, int y, int nWidth, int nHeight,
            IntPtr hWndParent, IntPtr hMenu, IntPtr hInstance, IntPtr lpParam);

        [DllImport("user32.dll")]
        static extern bool DestroyWindow(IntPtr hWnd);

        const int WS_CHILD = 0x40000000;
        const int WS_VISIBLE = 0x10000000;
    }
}