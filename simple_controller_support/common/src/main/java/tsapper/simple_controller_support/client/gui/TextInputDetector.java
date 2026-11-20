package tsapper.simple_controller_support.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.mixin.CreativeModeInventoryScreenAccessor;

/** Finds out whether a screen is currently accepting typed text, for the on-screen keyboard. */
public final class TextInputDetector {
    private TextInputDetector() {
    }

    /** The text box that would receive typed characters, or null if there is none we can see. */
    @Nullable
    public static EditBox focusedTextField(Screen screen) {
        GuiEventListener focused = screen.getFocused();
        while (focused instanceof ContainerEventHandler container && !(focused instanceof EditBox)) {
            focused = container.getFocused();
        }
        if (focused instanceof EditBox editBox && editBox.canConsumeInput()) {
            return editBox;
        }
        // The creative search box is focused by code when its tab is selected, not through the screen's focus
        if (screen instanceof CreativeModeInventoryScreen creative) {
            EditBox searchBox = ((CreativeModeInventoryScreenAccessor) creative).scs$getSearchBox();
            if (searchBox != null && searchBox.canConsumeInput()) {
                return searchBox;
            }
        }
        return null;
    }

    /** Screens whose whole purpose is typing; the keyboard opens with them. They may have no EditBox (signs, books). */
    public static boolean isTextScreen(Screen screen) {
        return screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen;
    }
}
