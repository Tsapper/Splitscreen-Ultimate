package tsapper.splitscreen_ultimate.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tsapper.splitscreen_ultimate.client.session.SessionClient;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) { super(title); }
    @Inject(method = "init", at = @At("TAIL"))
    private void su$retry(CallbackInfo ci) {
        var session = SessionClient.INSTANCE;
        if (session != null && session.main()) addRenderableWidget(Button.builder(
                Component.translatable("splitscreen_ultimate.retry"), button -> session.retry())
                .bounds(width - 110, 8, 102, 20).build());
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void su$status(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var session = SessionClient.INSTANCE;
        if (session == null || !session.main()) return;
        int y = 8;
        graphics.fill(4, 4, 300, 40 + session.statusLines().size() * 11, 0xB0000000);
        graphics.drawString(font, Component.translatable(session.mainHasController()
                ? "splitscreen_ultimate.join" : "splitscreen_ultimate.claim_main"), 8, y, 0xFFFFFF);
        y += 13;
        for (String line : session.statusLines()) { graphics.drawString(font, line, 8, y, 0xDDDDDD); y += 11; }
        if (!session.error().isEmpty()) graphics.drawString(font, font.plainSubstrByWidth(session.error(), 280), 8, y + 2, 0xFF7777);
    }
}
