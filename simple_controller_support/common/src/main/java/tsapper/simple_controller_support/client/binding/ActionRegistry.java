package tsapper.simple_controller_support.client.binding;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The full list of bindable actions: this mod's {@link ControllerActions} plus every {@link KeyMapping}
 * in {@code Options.keyMappings} (vanilla and modded). Rebuilt lazily so key mappings registered late by
 * other mods are picked up.
 */
public final class ActionRegistry {
    /** Movement keys are always driven by the left stick, so they are not offered for binding. */
    private static final Set<String> EXCLUDED_KEY_MAPPINGS = Set.of("key.forward", "key.back", "key.left", "key.right");

    /** Vanilla category order, matching the keyboard controls screen. Other categories follow alphabetically. */
    private static final List<String> CATEGORY_ORDER = List.of(
            BuiltinActions.CATEGORY_MENUS,
            BuiltinActions.CATEGORY_GAMEPLAY,
            KeyMapping.CATEGORY_MOVEMENT,
            KeyMapping.CATEGORY_GAMEPLAY,
            KeyMapping.CATEGORY_INVENTORY,
            KeyMapping.CATEGORY_CREATIVE,
            KeyMapping.CATEGORY_MULTIPLAYER,
            KeyMapping.CATEGORY_INTERFACE,
            KeyMapping.CATEGORY_MISC);

    private static List<BindingAction> actions = List.of();
    private static Map<String, BindingAction> byId = Map.of();
    private static List<Category> categories = List.of();
    private static KeyMapping[] builtFrom;
    private static boolean dirty = true;

    private ActionRegistry() {
    }

    public record Category(String translationKey, List<BindingAction> actions) {
    }

    static void invalidate() {
        dirty = true;
    }

    public static List<BindingAction> all() {
        rebuildIfNeeded();
        return actions;
    }

    public static List<Category> categories() {
        rebuildIfNeeded();
        return categories;
    }

    public static BindingAction byId(String id) {
        rebuildIfNeeded();
        return byId.get(id);
    }

    private static void rebuildIfNeeded() {
        KeyMapping[] keyMappings = Minecraft.getInstance().options.keyMappings;
        // Mod loaders replace the array when adding key mappings, so an identity check catches new ones
        if (!dirty && keyMappings == builtFrom) {
            return;
        }
        dirty = false;
        builtFrom = keyMappings;

        List<BindingAction> list = new ArrayList<>(ControllerActions.all());
        for (KeyMapping keyMapping : keyMappings) {
            if (!EXCLUDED_KEY_MAPPINGS.contains(keyMapping.getName())) {
                list.add(new KeyMappingAction(keyMapping));
            }
        }

        Map<String, BindingAction> ids = new LinkedHashMap<>();
        Map<String, List<BindingAction>> grouped = new LinkedHashMap<>();
        for (BindingAction action : list) {
            ids.putIfAbsent(action.id(), action);
            grouped.computeIfAbsent(action.category(), key -> new ArrayList<>()).add(action);
        }

        List<Category> sorted = new ArrayList<>();
        grouped.forEach((key, entries) -> sorted.add(new Category(key, List.copyOf(entries))));
        sorted.sort(Comparator.comparingInt((Category category) -> categoryRank(category.translationKey()))
                .thenComparing(category -> I18n.get(category.translationKey())));

        actions = List.copyOf(list);
        byId = ids;
        categories = List.copyOf(sorted);
    }

    private static int categoryRank(String category) {
        int index = CATEGORY_ORDER.indexOf(category);
        return index >= 0 ? index : CATEGORY_ORDER.size();
    }
}
