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

        // Title
        context.drawText(this.textRenderer, Text.literal("Trash Can"), x0 + 8, y0 + 6, 0x404040, false);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.playerInventoryTitle, 8, this.playerInventoryTitleY, 0x404040, false);

        Text hint = Text.literal("Incoming items are deleted (preview only)");
        context.drawText(this.textRenderer, hint, 8, 18 + UI_ROWS * 18 + 2, 0x404040, false);
    }
}
