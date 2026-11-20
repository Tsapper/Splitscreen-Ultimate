package tsapper.simple_controller_support.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSelectionList.class)
public interface AbstractSelectionListAccessor {
    @Accessor("itemHeight")
    int scs$getItemHeight();

    @Invoker("getRowTop")
    int scs$getRowTop(int index);
}
