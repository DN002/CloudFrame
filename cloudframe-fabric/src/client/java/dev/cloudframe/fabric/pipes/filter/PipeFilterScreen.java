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

    public PipeFilterScreen(PipeFilterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 114 + PipeFilterScreenHandler.UI_ROWS * 18;
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        int x0 = this.x;
        int y0 = this.y;

        // Keep the toggle in the title/header area without overlapping the first row of slots (starts at y+18).
        int buttonW = 86;
        int buttonH = 14;
        int bx = x0 + this.backgroundWidth - 8 - buttonW;
        int by = y0 + 2;

        modeButton = ButtonWidget.builder(Text.literal(""), button -> {
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, 0);
            }
        }).dimensions(bx, by, buttonW, buttonH).build();

        addDrawableChild(modeButton);

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

        // Title (drawn here with absolute coords so it can't be clipped by foreground transforms)
        context.drawText(this.textRenderer, this.title, x0 + 8, y0 + 6, 0x404040, true);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);

        // Hint under title (keep within header area, above first row of slots)
        Text hint = Text.literal(handler.isWhitelist()
            ? "Allows only listed items (empty = allow all)"
            : "Blocks listed items (empty = allow all)");
        context.drawText(this.textRenderer, hint, 8, 16, 0x404040, true);
    }
}
