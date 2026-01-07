package dev.cloudframe.fabric.content.trash;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TrashCanScreen extends HandledScreen<TrashCanScreenHandler> {

    private static final Identifier GENERIC_54 = Identifier.of("minecraft", "textures/gui/container/generic_54.png");

    private static final int UI_ROWS = 1;

    public TrashCanScreen(TrashCanScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 114 + UI_ROWS * 18;
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x0 = this.x;
        int y0 = this.y;

        // Top container region.
        context.drawTexture(RenderPipelines.GUI_TEXTURED, GENERIC_54, x0, y0, 0, 0, this.backgroundWidth, UI_ROWS * 18 + 17, 256, 256);
        // Player inventory region.
        context.drawTexture(RenderPipelines.GUI_TEXTURED, GENERIC_54, x0, y0 + UI_ROWS * 18 + 17, 0, 126, this.backgroundWidth, 96, 256, 256);

        // Title (drawn here with absolute coords so it can't be clipped by foreground transforms)
        context.drawText(this.textRenderer, this.title, x0 + 8, y0 + 6, 0x404040, true);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);

        // Hint
        Text hint = Text.literal("Newest \u2190\u2192 Oldest (preview only)");
        context.drawText(this.textRenderer, hint, 8, 16, 0x404040, true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Draw tooltips for hovered items
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
