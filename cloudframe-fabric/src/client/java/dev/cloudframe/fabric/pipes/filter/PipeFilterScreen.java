package dev.cloudframe.fabric.pipes.filter;

import dev.cloudframe.fabric.pipes.FabricPipeFilterManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PipeFilterScreen extends HandledScreen<PipeFilterScreenHandler> {

    private static final Identifier GENERIC_54 = Identifier.of("minecraft", "textures/gui/container/generic_54.png");

    private ButtonWidget modeButton;
    private ButtonWidget removeButton;

    public PipeFilterScreen(PipeFilterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 114 + PipeFilterScreenHandler.UI_ROWS * 18;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        int x0 = this.x;
        int y0 = this.y;

        int buttonW = 78;
        int buttonH = 20;
        int bx = x0 + this.backgroundWidth - 8 - buttonW;
        int by = y0 + 4;

        modeButton = ButtonWidget.builder(Text.literal(""), button -> {
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, 0);
            }
        }).dimensions(bx, by, buttonW, buttonH).build();

        addDrawableChild(modeButton);

        int removeW = 62;
        int removeH = 20;
        int rx = x0 + 8;
        int ry = y0 + 4;
        removeButton = ButtonWidget.builder(Text.literal("Remove"), button -> {
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, 1);
            }
        }).dimensions(rx, ry, removeW, removeH).build();
        addDrawableChild(removeButton);

        updateModeButtonText();
    }

    private void updateModeButtonText() {
        if (modeButton == null) return;
        boolean whitelist = handler.isWhitelist();
        modeButton.setMessage(Text.literal(whitelist ? "Whitelist" : "Blacklist"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateModeButtonText();
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x0 = this.x;
        int y0 = this.y;

        // Top part: uses generic_54. We only need 3 rows, so draw the top region.
        context.drawTexture(RenderPipelines.GUI_TEXTURED, GENERIC_54, x0, y0, 0, 0, this.backgroundWidth, PipeFilterScreenHandler.UI_ROWS * 18 + 17, 256, 256);
        // Player inventory region.
        context.drawTexture(RenderPipelines.GUI_TEXTURED, GENERIC_54, x0, y0 + PipeFilterScreenHandler.UI_ROWS * 18 + 17, 0, 126, this.backgroundWidth, 96, 256, 256);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Title
        context.drawText(this.textRenderer, this.title, 8, 6, 0x404040, false);
        // Player inventory
        context.drawText(this.textRenderer, this.playerInventoryTitle, 8, this.playerInventoryTitleY, 0x404040, false);

        // Hint under title (simple text, no new icons)
        Text hint = Text.literal(handler.isWhitelist()
            ? "Only listed items may enter"
            : "Listed items are blocked");
        context.drawText(this.textRenderer, hint, 8, 18 + PipeFilterScreenHandler.UI_ROWS * 18 + 2, 0x404040, false);
    }
}
