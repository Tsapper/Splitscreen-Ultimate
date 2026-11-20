package tsapper.simple_controller_support.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("tabButtons")
    List<RecipeBookTabButton> scs$getTabButtons();

    @Accessor("filterButton")
    StateSwitchingButton scs$getFilterButton();

    @Accessor("searchBox")
    EditBox scs$getSearchBox();

    @Accessor("recipeBookPage")
    RecipeBookPage scs$getRecipeBookPage();
}
