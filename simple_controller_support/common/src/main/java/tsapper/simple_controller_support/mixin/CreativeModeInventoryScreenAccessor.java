package tsapper.simple_controller_support.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Accessor("searchBox")
    EditBox scs$getSearchBox();

    /** Tab x relative to leftPos. */
    @Invoker("getTabX")
    int scs$getTabX(CreativeModeTab tab);

    /** Tab y relative to topPos. */
    @Invoker("getTabY")
    int scs$getTabY(CreativeModeTab tab);
}
