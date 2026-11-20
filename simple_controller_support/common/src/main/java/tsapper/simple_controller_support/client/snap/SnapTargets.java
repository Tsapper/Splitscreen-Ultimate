package tsapper.simple_controller_support.client.snap;

import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.jetbrains.annotations.Nullable;
import tsapper.simple_controller_support.SimpleControllerSupportMod;
import tsapper.simple_controller_support.client.snap.SnapTargetSource.Query;
import tsapper.simple_controller_support.mixin.AbstractContainerScreenAccessor;
import tsapper.simple_controller_support.mixin.AbstractSelectionListAccessor;
import tsapper.simple_controller_support.mixin.CreativeModeInventoryScreenAccessor;
import tsapper.simple_controller_support.mixin.RecipeBookComponentAccessor;
import tsapper.simple_controller_support.mixin.RecipeBookPageAccessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Finds everything clickable on a screen for D-pad cursor snapping: widgets, list rows, container slots,
 * creative tabs and the recipe book, plus whatever registered {@link SnapTargetSource}s report.
 */
public final class SnapTargets {
    private static final List<SnapTargetSource> SOURCES = new CopyOnWriteArrayList<>();
    private static final int SLOT_SIZE = 16;
    /** Rows render this much shorter than the list's item height. */
    private static final int ROW_GAP = 4;

    private SnapTargets() {
    }

    public static void registerSource(SnapTargetSource source) {
        SOURCES.add(source);
    }

    public static void unregisterSource(SnapTargetSource source) {
        SOURCES.remove(source);
    }

    /** All targets, without duplicates (same centre) and without visible targets that lie off screen. */
    public static List<SnapTarget> collect(Screen screen, Query query) {
        List<SnapTarget> targets = new ArrayList<>();
        Set<Long> centres = new HashSet<>();
        ScreenRectangle screenArea = new ScreenRectangle(0, 0, screen.width, screen.height);
        Consumer<SnapTarget> out = target -> {
            ScreenRectangle rect = target.rect();
            if (rect.width() <= 0 || rect.height() <= 0) {
                return;
            }
            if (target.reveal() == null && !screenArea.containsPoint((int) target.centerX(), (int) target.centerY())) {
                return;
            }
            long key = ((long) (int) (target.centerX() * 2) << 32) | ((int) (target.centerY() * 2) & 0xFFFFFFFFL);
            if (centres.add(key)) {
                targets.add(target);
            }
        };

        collectVanilla(screen, query, out);
        for (SnapTargetSource source : SOURCES) {
            try {
                source.collect(screen, query, out);
            } catch (RuntimeException e) {
                SimpleControllerSupportMod.LOGGER.warn("Snap target source {} failed", source, e);
            }
        }
        return targets;
    }

    private static void collectVanilla(Screen screen, Query query, Consumer<SnapTarget> out) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
            int left = accessor.scs$getLeftPos();
            int top = accessor.scs$getTopPos();
            for (Slot slot : containerScreen.getMenu().slots) {
                if (slot.isActive()) {
                    out.accept(new SnapTarget(left + slot.x, top + slot.y, SLOT_SIZE, SLOT_SIZE));
                }
            }

            if (screen instanceof CreativeModeInventoryScreen creative) {
                CreativeModeInventoryScreenAccessor creativeAccessor = (CreativeModeInventoryScreenAccessor) creative;
                // Same area checkTabHovering uses
                for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
                    int tabX = creativeAccessor.scs$getTabX(tab);
                    // NeoForge pages its tabs; tabs not on the current page get column -1, i.e. a negative x
                    if (tab.shouldDisplay() && tabX >= 0) {
                        out.accept(new SnapTarget(left + tabX + 3, top + creativeAccessor.scs$getTabY(tab) + 3, 21, 27));
                    }
                }
                // Not a child of the screen
                EditBox searchBox = creativeAccessor.scs$getSearchBox();
                if (searchBox != null && searchBox.isVisible()) {
                    out.accept(new SnapTarget(searchBox.getRectangle()));
                }
            }
        }

        collectChildren(screen.children(), query, null, out);
    }

    private static void collectChildren(List<? extends GuiEventListener> children, Query query,
                                        @Nullable ScreenRectangle clip, Consumer<SnapTarget> out) {
        for (GuiEventListener child : children) {
            collectListener(child, query, clip, out);
        }
    }

    private static void collectListener(GuiEventListener listener, Query query, @Nullable ScreenRectangle clip,
                                        Consumer<SnapTarget> out) {
        if (listener instanceof AbstractSelectionList<?> list) {
            collectList(list, query, out);
        } else if (listener instanceof RecipeBookComponent recipeBook) {
            collectRecipeBook(recipeBook, out);
        } else if (listener instanceof AbstractWidget widget) {
            if (!widget.visible) {
                return;
            }
            if (widget instanceof ContainerEventHandler container && !container.children().isEmpty()) {
                collectChildren(container.children(), query, clip, out);
            } else if (widget.active) {
                addWidget(widget, clip, out);
            }
        } else if (listener instanceof ContainerEventHandler container) {
            collectChildren(container.children(), query, clip, out);
        }
    }

    private static void addWidget(AbstractWidget widget, @Nullable ScreenRectangle clip, Consumer<SnapTarget> out) {
        ScreenRectangle rect = widget.getRectangle();
        if (clip != null) {
            int centreX = rect.left() + rect.width() / 2;
            int centreY = rect.top() + rect.height() / 2;
            if (!clip.containsPoint(centreX, centreY)) {
                return;
            }
            rect = rect.intersection(clip);
            if (rect == null) {
                return;
            }
        }
        out.accept(new SnapTarget(rect));
    }

    /**
     * Visible rows become targets: rows with widgets (options lists) contribute their widgets, plain rows
     * (worlds, servers) the row itself. While the cursor is in the list, the rows just outside the visible area
     * are targets too, so snapping up/down past the edge scrolls the list.
     */
    private static void collectList(AbstractSelectionList<?> list, Query query, Consumer<SnapTarget> out) {
        if (!list.visible) {
            return;
        }
        AbstractSelectionListAccessor accessor = (AbstractSelectionListAccessor) list;
        int itemHeight = accessor.scs$getItemHeight();
        int rowHeight = itemHeight - ROW_GAP;
        int listTop = list.getY();
        int listBottom = list.getBottom();
        int left = list.getRowLeft() - 2;
        int width = list.getRowRight() - left;
        ScreenRectangle listArea = list.getRectangle();
        boolean revealRows = query.direction().getAxis() == ScreenAxis.VERTICAL
                && list.isMouseOver(query.cursorX(), query.cursorY());

        List<?> entries = list.children();
        for (int i = 0; i < entries.size(); i++) {
            int top = accessor.scs$getRowTop(i);
            int bottom = top + rowHeight;
            if (top >= listTop && bottom <= listBottom) {
                if (entries.get(i) instanceof ContainerEventHandler entry) {
                    collectChildren(entry.children(), query, listArea, out);
                } else {
                    out.accept(new SnapTarget(left, top, width, rowHeight));
                }
            } else if (revealRows && (top < listTop && bottom > listTop - itemHeight
                    || bottom > listBottom && top < listBottom + itemHeight)) {
                int index = i;
                out.accept(new SnapTarget(new ScreenRectangle(left, top, width, rowHeight), () -> {
                    scrollIntoView(list, accessor, index, rowHeight);
                    return new ScreenRectangle(left, accessor.scs$getRowTop(index), width, rowHeight);
                }));
            }
        }
    }

    private static void scrollIntoView(AbstractSelectionList<?> list, AbstractSelectionListAccessor accessor,
                                       int index, int rowHeight) {
        int top = accessor.scs$getRowTop(index);
        if (top < list.getY()) {
            list.setScrollAmount(list.getScrollAmount() + top - list.getY());
        } else if (top + rowHeight > list.getBottom()) {
            list.setScrollAmount(list.getScrollAmount() + top + rowHeight - list.getBottom());
        }
    }

    private static void collectRecipeBook(RecipeBookComponent recipeBook, Consumer<SnapTarget> out) {
        if (!recipeBook.isVisible()) {
            return;
        }
        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) recipeBook;
        List<AbstractWidget> widgets = new ArrayList<>(accessor.scs$getTabButtons());
        widgets.add(accessor.scs$getFilterButton());
        widgets.add(accessor.scs$getSearchBox());
        RecipeBookPage page = accessor.scs$getRecipeBookPage();
        RecipeBookPageAccessor pageAccessor = (RecipeBookPageAccessor) page;
        widgets.addAll(pageAccessor.scs$getButtons());
        widgets.add(pageAccessor.scs$getForwardButton());
        widgets.add(pageAccessor.scs$getBackButton());
        for (AbstractWidget widget : widgets) {
            if (widget != null && widget.visible && widget.active) {
                addWidget(widget, null, out);
            }
        }
    }
}
