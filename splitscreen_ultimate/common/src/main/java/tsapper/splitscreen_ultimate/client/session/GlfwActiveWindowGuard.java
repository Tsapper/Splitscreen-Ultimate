package tsapper.splitscreen_ultimate.client.session;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.ptr.IntByReference;
import tsapper.splitscreen_ultimate.SplitscreenUltimateMod;

/**
 * GLFW 3.3.x polls GetActiveWindow then dereferences its global "GLFW" Win32 property. SetParent
 * joins input queues across processes, so that HWND can belong to another JVM and its property is
 * a foreign address. Guard only GLFW's GetActiveWindow import in this process; leave Windows and
 * every other module untouched. The callback and original function live for this process's lifetime.
 */
public final class GlfwActiveWindowGuard {
    public interface ActiveWindowCallback extends StdCallLibrary.StdCallCallback { Pointer invoke(); }
    private static ActiveWindowCallback callback;
    private static Function original;
    private static final NativeLibrary KERNEL = NativeLibrary.getInstance("kernel32");
    private static final NativeLibrary USER = NativeLibrary.getInstance("user32");
    private GlfwActiveWindowGuard() { }

    public static void install() {
        if (callback != null) return;
        if (Native.POINTER_SIZE != 8) throw new IllegalStateException("Prototype embedding requires 64-bit Windows");
        Pointer module = KERNEL.getFunction("GetModuleHandleW").invokePointer(new Object[] { new WString("glfw.dll") });
        if (module == null) throw new IllegalStateException("Cannot locate loaded GLFW module");
        long pe = Integer.toUnsignedLong(module.getInt(0x3c));
        if (module.getInt(pe) != 0x4550 || module.getShort(pe + 24) != 0x20b) throw new IllegalStateException("Unsupported GLFW PE format");
        long imports = Integer.toUnsignedLong(module.getInt(pe + 24 + 112 + 8));
        int ownPid = KERNEL.getFunction("GetCurrentProcessId").invokeInt(new Object[0]);
        for (long descriptor = imports; module.getInt(descriptor + 12) != 0; descriptor += 20) {
            String library = module.getString(Integer.toUnsignedLong(module.getInt(descriptor + 12)));
            if (!library.equalsIgnoreCase("user32.dll")) continue;
            long names = Integer.toUnsignedLong(module.getInt(descriptor));
            long table = Integer.toUnsignedLong(module.getInt(descriptor + 16));
            if (names == 0) throw new IllegalStateException("GLFW import names unavailable");
            for (int index = 0; ; index++) {
                long name = module.getLong(names + index * 8L);
                if (name == 0) break;
                if (name < 0 || !module.getString(name + 2).equals("GetActiveWindow")) continue;
                Pointer slot = module.share(table + index * 8L);
                original = Function.getFunction(slot.getPointer(0), Function.ALT_CONVENTION);
                callback = () -> {
                    Pointer active = original.invokePointer(new Object[0]);
                    if (active == null) return null;
                    var pid = new IntByReference();
                    USER.getFunction("GetWindowThreadProcessId").invokeInt(new Object[] { active, pid });
                    return pid.getValue() == ownPid ? active : null;
                };
                IntByReference oldProtection = new IntByReference();
                Function protect = KERNEL.getFunction("VirtualProtect");
                if (protect.invokeInt(new Object[] { slot, new NativeLong(8), 0x04, oldProtection }) == 0) {
                    callback = null; throw new IllegalStateException("Cannot protect GLFW import slot");
                }
                slot.setPointer(0, CallbackReference.getFunctionPointer(callback));
                protect.invokeInt(new Object[] { slot, new NativeLong(8), oldProtection.getValue(), new IntByReference() });
                SplitscreenUltimateMod.LOGGER.info("Installed process-local GLFW active-window guard");
                return;
            }
        }
        throw new IllegalStateException("GLFW GetActiveWindow import was not found");
    }
}
