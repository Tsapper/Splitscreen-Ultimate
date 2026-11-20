package tsapper.simple_controller_support.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int scs$getClickCount();

    @Accessor("clickCount")
    void scs$setClickCount(int clickCount);

    /** The currently bound keyboard/mouse key. */
    @Accessor("key")
    InputConstants.Key scs$getKey();
}
