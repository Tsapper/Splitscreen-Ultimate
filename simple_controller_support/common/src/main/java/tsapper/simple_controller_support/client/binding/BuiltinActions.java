package tsapper.simple_controller_support.client.binding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import tsapper.simple_controller_support.SimpleControllerSupportMod;
import tsapper.simple_controller_support.client.binding.BindingAction.ActionContext;
import tsapper.simple_controller_support.client.gui.TextInputDetector;

/** Controller-only actions provided by this mod. Their default buttons are in {@link DefaultBindings}. */
public final class BuiltinActions {
    public static final String CATEGORY_GAMEPLAY = "simple_controller_support.category.gameplay";
    public static final String CATEGORY_MENUS = "simple_controller_support.category.menus";

    public static final String PAUSE = id("pause");
    public static final String HOTBAR_PREVIOUS = id("hotbar_previous");
    public static final String HOTBAR_NEXT = id("hotbar_next");

    public static final String MENU_CLICK = id("menu_click");
    public static final String MENU_SECONDARY_CLICK = id("menu_secondary_click");
    public static final String MENU_QUICK_MOVE = id("menu_quick_move");
    public static final String MENU_BACK = id("menu_back");
    public static final String OPEN_KEYBOARD = id("open_keyboard");
    public static final String SNAP_UP = id("snap_up");
    public static final String SNAP_DOWN = id("snap_down");
    public static final String SNAP_LEFT = id("snap_left");
    public static final String SNAP_RIGHT = id("snap_right");

    private BuiltinActions() {
    }

    private static String id(String path) {
        return SimpleControllerSupportMod.MOD_ID + ":" + path;
    }

    public static void register() {
        // Gameplay
        ControllerActions.register(PAUSE, CATEGORY_GAMEPLAY, ActionContext.GAMEPLAY,
                ctx -> Minecraft.getInstance().pauseGame(false));
        ControllerActions.register(HOTBAR_PREVIOUS, CATEGORY_GAMEPLAY, ActionContext.GAMEPLAY,
                ctx -> scrollHotbar(1));
        ControllerActions.register(HOTBAR_NEXT, CATEGORY_GAMEPLAY, ActionContext.GAMEPLAY,
                ctx -> scrollHotbar(-1));

        // Menus (these act on the virtual cursor)
        ControllerActions.register(MENU_CLICK, CATEGORY_MENUS, ActionContext.SCREEN,
                ctx -> ctx.screenInput().mouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT),
                ctx -> ctx.screenInput().mouseUp(GLFW.GLFW_MOUSE_BUTTON_LEFT));
        ControllerActions.register(MENU_SECONDARY_CLICK, CATEGORY_MENUS, ActionContext.SCREEN,
                ctx -> ctx.screenInput().mouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT),
                ctx -> ctx.screenInput().mouseUp(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        ControllerActions.register(MENU_QUICK_MOVE, CATEGORY_MENUS, ActionContext.SCREEN,
                ctx -> {
                    ctx.screenInput().setShiftHeld(true);
                    ctx.screenInput().mouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                },
                ctx -> {
                    ctx.screenInput().mouseUp(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                    ctx.screenInput().setShiftHeld(false);
                });
        ControllerActions.register(MENU_BACK, CATEGORY_MENUS, ActionContext.SCREEN,
                ctx -> ctx.screenInput().back());
        // Targets the focused text field if one can be found; otherwise types into whatever has focus (e.g. a mod's search box)
        ControllerActions.register(OPEN_KEYBOARD, CATEGORY_MENUS, ActionContext.SCREEN,
                ctx -> {
                    Screen screen = Minecraft.getInstance().screen;
                    ctx.openKeyboard(screen != null ? TextInputDetector.focusedTextField(screen) : null);
                });
        registerSnap(SNAP_UP, ScreenDirection.UP);
        registerSnap(SNAP_DOWN, ScreenDirection.DOWN);
        registerSnap(SNAP_LEFT, ScreenDirection.LEFT);
        registerSnap(SNAP_RIGHT, ScreenDirection.RIGHT);
    }

    /** Jumps the virtual cursor to the nearest clickable thing in a direction, repeating while held. */
    private static void registerSnap(String id, ScreenDirection direction) {
        ControllerActions.register(id, CATEGORY_MENUS, ActionContext.SCREEN,
                ctx -> ctx.screenInput().snapPressed(direction),
                ctx -> ctx.screenInput().snapReleased(direction));
    }

    /** Same behaviour as one notch of the mouse wheel: positive = previous slot. */
    private static void scrollHotbar(int direction) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (player.isSpectator()) {
            if (minecraft.gui.getSpectatorGui().isMenuActive()) {
                minecraft.gui.getSpectatorGui().onMouseScrolled(-direction);
            }
        } else {
            player.getInventory().swapPaint(direction);
        }
    }
}
