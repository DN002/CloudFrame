package dev.cloudframe.fabric.quarry.controller;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class QuarryControllerScreen extends HandledScreen<QuarryControllerScreenHandler> {

    private static final Identifier GENERIC_54 = Identifier.of("minecraft", "textures/gui/container/generic_54.png");

    private static final int UI_ROWS = 3;

    // Slot positions (row 0)
    private static final int SLOT_ROW0_Y = 18;
    private static final int SLOT_ROW1_Y = 18 + 1 * 18;
    private static final int SLOT_REDSTONE_TORCH_X = 8 + 0 * 18;
    private static final int SLOT_LEVER_X = 8 + 1 * 18;
    private static final int SLOT_SILK_X = 8 + 2 * 18;
    private static final int SLOT_SPEED_X = 8 + 3 * 18;
    private static final int SLOT_BARRIER_X = 8 + 4 * 18;
    private static final int SLOT_POWER_X = 8 + 7 * 18;
    private static final int SLOT_HOPPER_X = 8 + 8 * 18;

    // Power bar placement (title row)
    private static final int TITLE_TEXT_X = 8;
    private static final int TITLE_TEXT_Y = 6;
    private static final int TITLE_BAR_GAP = 6;
    private static final int TITLE_BAR_MARGIN_RIGHT = 8;
    private static final int TITLE_BAR_H = 8;

    // Cached title-row power bar bounds for hover tooltip
    private int powerBarHoverX;
    private int powerBarHoverY;
    private int powerBarHoverW;
    private int powerBarHoverH;

    // Power bar animation (client-side smoothing)
    private static final double POWER_RAMP_SECONDS = 20.0; // how long 0 -> 100% should take when toggling on
    private static final double POWER_RAMP_TICKS = 20.0 * POWER_RAMP_SECONDS;
    private static final double POWER_FILL_RISE_STEP = 1.0 / POWER_RAMP_TICKS;

    private double animatedPowerFillRatio = 0.0;
    private double animatedPowerUsingCfePerTick = 0.0;
    private double lastNonZeroTargetUsingCfePerTick = 0.0;
    private boolean lastPowerActive = false;
    private int lastQuarryState = Integer.MIN_VALUE;

    private static final class CachedPowerAnim {
        final double fillRatio;
        final double usingCfePerTick;
        final double lastNonZeroTargetUsingCfePerTick;
        final boolean lastPowerActive;
        final int lastQuarryState;
        final long updatedAtMs;

        CachedPowerAnim(
            double fillRatio,
            double usingCfePerTick,
            double lastNonZeroTargetUsingCfePerTick,
            boolean lastPowerActive,
            int lastQuarryState,
            long updatedAtMs
        ) {
            this.fillRatio = fillRatio;
            this.usingCfePerTick = usingCfePerTick;
            this.lastNonZeroTargetUsingCfePerTick = lastNonZeroTargetUsingCfePerTick;
            this.lastPowerActive = lastPowerActive;
            this.lastQuarryState = lastQuarryState;
            this.updatedAtMs = updatedAtMs;
        }
    }

    private static final java.util.Map<Long, CachedPowerAnim> POWER_ANIM_CACHE = new java.util.HashMap<>();
    private static final long POWER_ANIM_CACHE_TTL_MS = 30_000L;

    // Slot positions (row 1)
    private static final int SLOT_CHUNK_PREVIEW_X = 8 + 6 * 18;
    private static final int SLOT_CHUNKLOAD_X = 8 + 7 * 18;
    private static final int SLOT_SILENT_X = 8 + 8 * 18;

    public QuarryControllerScreen(QuarryControllerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 114 + UI_ROWS * 18;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
    }

    private void updatePowerAnimationOneTick() {
        if (!handler.hasRegisteredQuarry()) {
            animatedPowerFillRatio = 0.0;
            animatedPowerUsingCfePerTick = 0.0;
            lastNonZeroTargetUsingCfePerTick = 0.0;
            lastPowerActive = false;
            lastQuarryState = 0;
            return;
        }

        int quarryState = handler.getQuarryState();
        int bufferCap = Math.max(0, handler.getPowerBufferCapacityCfe());
        int bufferStored = Math.max(0, handler.getPowerBufferStoredCfe());

        boolean activeNow = handler.isQuarryActive();

        double targetFill = 0.0;
        if (bufferCap > 0) {
            targetFill = Math.min(1.0, Math.max(0.0, (double) bufferStored / (double) bufferCap));
        }

        // Display real server values immediately (no slow ramp).
        // The quarry already enforces power gating server-side; the UI should not imply otherwise.
        animatedPowerFillRatio = targetFill;
        animatedPowerUsingCfePerTick = activeNow ? (double) Math.max(0, handler.getPowerRequiredCfePerTick()) : 0.0;

        lastPowerActive = activeNow;
        lastQuarryState = quarryState;
        cachePowerAnim(System.currentTimeMillis());
    }

    /**
     * Driven from Fabric's client tick event to ensure animation progresses even
     * when render timing is odd or player age isn't advancing as expected.
     */
    public void onClientTick() {
        updatePowerAnimationOneTick();
    }

    @Override
    public void removed() {
        cachePowerAnim(System.currentTimeMillis());
        super.removed();
    }

    private long getPowerAnimCacheKey() {
        try {
            BlockPos p = handler != null ? handler.getControllerPos() : null;
            if (p == null) return Long.MIN_VALUE;
            return p.asLong();
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }

    private void cachePowerAnim(long nowMs) {
        long key = getPowerAnimCacheKey();
        if (key == Long.MIN_VALUE) return;

        POWER_ANIM_CACHE.put(key, new CachedPowerAnim(
            Math.min(1.0, Math.max(0.0, animatedPowerFillRatio)),
            Math.max(0.0, animatedPowerUsingCfePerTick),
            Math.max(0.0, lastNonZeroTargetUsingCfePerTick),
            lastPowerActive,
            lastQuarryState,
            nowMs
        ));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int topHeight = UI_ROWS * 18 + 17;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, GENERIC_54, this.x, this.y, 0.0f, 0.0f, this.backgroundWidth, topHeight, 256, 256);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, GENERIC_54, this.x, this.y + topHeight, 0.0f, 126.0f, this.backgroundWidth, 96, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Fill EMPTY controller slots with black stained glass panes.
        // Important: panes must be drawn BEFORE our icon widgets so they don't overlap.
        ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        for (int row = 0; row < UI_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                if (isWidgetSlot(row, col)) continue;
                int rx = 8 + col * 18;
                int ry = 18 + row * 18;
                context.drawItem(pane, this.x + rx, this.y + ry);
            }
        }

        // Power bar: driven by received/required CFE/t. Render beside the title.
        drawPowerBarTitleRow(context);

        // Draw old-style icon widgets in top grid slots.
        drawIcon(context, new ItemStack(Items.REDSTONE_TORCH), SLOT_REDSTONE_TORCH_X, SLOT_ROW0_Y);
        drawIcon(context, new ItemStack(Items.LEVER), SLOT_LEVER_X, SLOT_ROW0_Y);
        drawIcon(context, new ItemStack(Items.BARRIER), SLOT_BARRIER_X, SLOT_ROW0_Y);

        // Redstone mode widget: comparator + powered indicator.
        boolean powered = handler.getPowerStatus() == 1;
        drawIcon(context, new ItemStack(Items.COMPARATOR), SLOT_POWER_X, SLOT_ROW0_Y);
        if (!powered) {
            drawDisabledOverlay(context, SLOT_POWER_X, SLOT_ROW0_Y);
        }

        // Routing toggle: hopper icon.
        drawIcon(context, new ItemStack(Items.HOPPER), SLOT_HOPPER_X, SLOT_ROW0_Y);

        // Chunkloading toggle (row 1): compass.
        drawIcon(context, new ItemStack(Items.COMPASS), SLOT_CHUNKLOAD_X, SLOT_ROW1_Y);
        if (!handler.isChunkLoadingEnabled()) {
            drawDisabledOverlay(context, SLOT_CHUNKLOAD_X, SLOT_ROW1_Y);
        }

        // Chunk preview (row 1): map.
        drawIcon(context, new ItemStack(Items.MAP), SLOT_CHUNK_PREVIEW_X, SLOT_ROW1_Y);

        // Silent mode toggle (row 1): note block.
        drawIcon(context, new ItemStack(Items.NOTE_BLOCK), SLOT_SILENT_X, SLOT_ROW1_Y);
        if (!handler.isSilentMode()) {
            drawDisabledOverlay(context, SLOT_SILENT_X, SLOT_ROW1_Y);
        }

        // Tooltips for icons
        drawIconTooltips(context, mouseX, mouseY);

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private void drawPowerBarTitleRow(DrawContext context) {
        // Draw to the right of the title text, filling the empty header space.
        int titleWidth = this.textRenderer.getWidth(this.title);
        int relX = TITLE_TEXT_X + titleWidth + TITLE_BAR_GAP;
        int relY = TITLE_TEXT_Y;

        int w = (this.backgroundWidth - TITLE_BAR_MARGIN_RIGHT) - relX;
        if (w < 24) {
            // If the title is too long for the header bar, right-align a minimal bar.
            w = 48;
            relX = this.backgroundWidth - TITLE_BAR_MARGIN_RIGHT - w;
        }

        // Center vertically relative to the title baseline.
        int x0 = this.x + relX;
        int y0 = this.y + relY;
        int h = TITLE_BAR_H;

        // Cache for hover tooltip detection.
        this.powerBarHoverX = relX;
        this.powerBarHoverY = relY;
        this.powerBarHoverW = w;
        this.powerBarHoverH = h;

        // Border
        int border = 0xFF000000;
        context.fill(x0 - 1, y0 - 1, x0 + w + 1, y0, border);
        context.fill(x0 - 1, y0 + h, x0 + w + 1, y0 + h + 1, border);
        context.fill(x0 - 1, y0, x0, y0 + h, border);
        context.fill(x0 + w, y0, x0 + w + 1, y0 + h, border);

        // Background
        context.fill(x0, y0, x0 + w, y0 + h, 0xFF2B2B2B);

        if (!handler.hasRegisteredQuarry()) {
            // Unregistered: no fill.
            drawPowerBarTicks(context, x0, y0, w, h);
            return;
        }

        int required = Math.max(0, handler.getPowerRequiredCfePerTick());

        float ratio = (float) Math.min(1.0, Math.max(0.0, animatedPowerFillRatio));
        int fillW = Math.max(0, Math.min(w, Math.round(ratio * (float) w)));

        // Fill color: always light red as requested for power readouts.
        int fill = 0xFFCC5555;
        if (fillW > 0) {
            context.fill(x0, y0, x0 + fillW, y0 + h, fill);
        }

        // If power is blocked (insufficient), add a subtle overlay to indicate the bar is not "good".
        if (required > 0 && handler.isPowerBlocked()) {
            context.fill(x0, y0, x0 + w, y0 + h, 0x40800000);
        }

        drawPowerBarTicks(context, x0, y0, w, h);
    }

    private void drawPowerBarTicks(DrawContext context, int x0, int y0, int w, int h) {
        // Denser ticks: 0..100% in 10% steps (major at 0/50/100).
        int tick = 0xFFB0B0B0;
        for (int i = 0; i <= 10; i++) {
            // Don't draw ticks on the outer edges; keep borders clean and symmetric.
            if (i == 0 || i == 10) continue;

            int x = x0 + Math.round(((float) w * (float) i) / 10.0f);
            if (x <= x0) x = x0 + 1;
            if (x >= x0 + w) x = x0 + w - 1;
            boolean major = (i == 5);
            int tickH = major ? h : Math.max(2, h - 2);
            int yStart = major ? y0 : (y0 + 1);
            context.fill(x, yStart, x + 1, yStart + tickH, tick);
        }
    }

    private static boolean isWidgetSlot(int row, int col) {
        if (row == 0) {
            return col == 0 || col == 1 || col == 2 || col == 3 || col == 4 || col == 7 || col == 8;
        }
        if (row == 1) {
            return col == 6 || col == 7 || col == 8;
        }
        return false;
    }

    private static String formatEtaMinutesSeconds(int seconds) {
        if (seconds <= 0) return "—";

        long s = seconds;
        long hours = s / 3600L;
        s %= 3600L;
        long minutes = s / 60L;
        s %= 60L;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + s + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + s + "s";
        }
        return s + "s";
    }

    private String getOwnerNameFromControllerBE() {
        if (client == null || client.world == null) return null;
        var be = client.world.getBlockEntity(handler.getControllerPos());
        if (be instanceof QuarryControllerBlockEntity qbe) {
            String name = qbe.getOwnerName();
            if (name != null && !name.isBlank()) return name;
        }
        return null;
    }

    private void drawIcon(DrawContext context, ItemStack stack, int relX, int relY) {
        context.drawItem(stack, this.x + relX, this.y + relY);
    }

    private void drawDisabledOverlay(DrawContext context, int relX, int relY) {
        // Light gray overlay to indicate "not installed".
        int sx = this.x + relX;
        int sy = this.y + relY;
        context.fill(sx, sy, sx + 16, sy + 16, 0x80AAAAAA);
    }

    private boolean isHoveringSlot(int relX, int relY, int slotRelX, int slotRelY) {
        return relX >= slotRelX && relX < slotRelX + 16 && relY >= slotRelY && relY < slotRelY + 16;
    }

    private boolean isHoveringRect(int relX, int relY, int rectRelX, int rectRelY, int w, int h) {
        return relX >= rectRelX && relX < rectRelX + w && relY >= rectRelY && relY < rectRelY + h;
    }

    private void drawIconTooltips(DrawContext context, int mouseX, int mouseY) {
        int relX = mouseX - this.x;
        int relY = mouseY - this.y;

        // Power bar tooltip (title row)
        if (isHoveringRect(relX, relY, powerBarHoverX, powerBarHoverY, powerBarHoverW, powerBarHoverH)) {
            java.util.List<Text> lines = new java.util.ArrayList<>();
            lines.add(Text.literal("Power").formatted(Formatting.WHITE));

            if (!handler.hasRegisteredQuarry()) {
                lines.add(Text.literal("N/A (unregistered)").formatted(Formatting.YELLOW));
                lines.add(Text.literal("Register the controller first").formatted(Formatting.GRAY));
            } else {
                int required = Math.max(0, handler.getPowerRequiredCfePerTick());
                int received = Math.max(0, handler.getPowerReceivedCfePerTick());

                int stored = Math.max(0, handler.getPowerBufferStoredCfe());
                int cap = Math.max(0, handler.getPowerBufferCapacityCfe());

                int animatedUsing = Math.max(0, (int) Math.round(animatedPowerUsingCfePerTick));

                lines.add(Text.literal("Buffer: " + stored + "/" + cap + " CFE").formatted(Formatting.RED));

                if (required > 0) {
                    lines.add(Text.literal("Using: " + animatedUsing + " CFE/t").formatted(Formatting.RED));
                    lines.add(Text.literal("Receiving: " + received + " CFE/t").formatted(Formatting.RED));
                } else {
                    lines.add(Text.literal("Status: Paused").formatted(Formatting.GRAY));
                }

                if (handler.isPowerBlocked()) {
                    lines.add(Text.literal("Insufficient Power").formatted(Formatting.RED));
                }
            }

            context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_LEVER_X, SLOT_ROW0_Y)) {
            java.util.List<Text> lines = new java.util.ArrayList<>();

            if (!handler.hasRegisteredQuarry()) {
                lines.add(Text.literal("Unregistered (use Wrench to register)").formatted(Formatting.YELLOW));
            } else {
                lines.add(Text.literal("Toggle quarry on/off").formatted(Formatting.GRAY));

                // If the quarry is paused and has no valid output, starting will be blocked.
                if (handler.getQuarryState() == 1 && !handler.hasValidOutput()) {
                    lines.add(Text.literal("Cannot start: Output not connected").formatted(Formatting.RED));
                    lines.add(Text.literal("Connect pipes to a chest/inventory").formatted(Formatting.DARK_GRAY));
                }
            }

            context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_BARRIER_X, SLOT_ROW0_Y)) {
            context.drawTooltip(this.textRenderer, java.util.List.of(
                Text.literal("Remove controller").formatted(Formatting.RED),
                Text.literal("Drops controller + installed augments").formatted(Formatting.GRAY)
            ), mouseX, mouseY);
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_HOPPER_X, SLOT_ROW0_Y)) {
            if (!handler.hasRegisteredQuarry()) {
                context.drawTooltip(this.textRenderer, java.util.List.of(
                    Text.literal("Routing: (unregistered)").formatted(Formatting.YELLOW),
                    Text.literal("Use Wrench to register").formatted(Formatting.GRAY)
                ), mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer, java.util.List.of(
                    Text.literal("Routing: " + (handler.isOutputRoundRobin() ? "Round Robin" : "Single"))
                        .formatted(Formatting.GRAY),
                    Text.literal("Left-click to toggle").formatted(Formatting.DARK_GRAY)
                ), mouseX, mouseY);
            }
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_POWER_X, SLOT_ROW0_Y)) {
            java.util.List<Text> lines = new java.util.ArrayList<>();
            String mode = switch (handler.getRedstoneMode()) {
                case 1 -> "Redstone Enabled";
                case 2 -> "Redstone Disabled";
                default -> "Always On";
            };
            lines.add(Text.literal("Redstone Mode: " + mode).formatted(Formatting.GRAY));
            lines.add((handler.getPowerStatus() == 1
                ? Text.literal("Redstone Powered: Yes").formatted(Formatting.GREEN)
                : Text.literal("Redstone Powered: No").formatted(Formatting.RED)));
            if (handler.isRedstoneBlocked()) {
                lines.add(Text.literal("Blocked by redstone mode").formatted(Formatting.RED));
            }
            lines.add(Text.literal("Left-click to cycle").formatted(Formatting.DARK_GRAY));
            context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_CHUNK_PREVIEW_X, SLOT_ROW1_Y)) {
            context.drawTooltip(this.textRenderer, java.util.List.of(
                Text.literal("Preview Chunkloading Chunks").formatted(Formatting.WHITE),
                Text.literal("Outlines affected chunks with particles").formatted(Formatting.GRAY),
                Text.literal("Left-click to toggle").formatted(Formatting.DARK_GRAY)
            ), mouseX, mouseY);
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_CHUNKLOAD_X, SLOT_ROW1_Y)) {
            java.util.List<Text> lines = new java.util.ArrayList<>();
            lines.add(Text.literal("Chunkloading").formatted(Formatting.WHITE));
            lines.add(Text.literal("Chunks affected: " + handler.getAffectedChunkCount()).formatted(Formatting.GRAY));
            lines.add(Text.literal("Status: " + (handler.isChunkLoadingEnabled() ? "Enabled" : "Disabled"))
                .formatted(handler.isChunkLoadingEnabled() ? Formatting.GREEN : Formatting.RED));
            if (!handler.isChunkLoadingEnabled() && handler.getAffectedChunkCount() > 1) {
                lines.add(Text.literal("Warning: May pause when you leave").formatted(Formatting.YELLOW));
            }
            lines.add(Text.literal("Left-click to toggle").formatted(Formatting.DARK_GRAY));
            context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_SILENT_X, SLOT_ROW1_Y)) {
            context.drawTooltip(this.textRenderer, java.util.List.of(
                Text.literal("Silent Mode: " + (handler.isSilentMode() ? "ON" : "OFF"))
                    .formatted(handler.isSilentMode() ? Formatting.GREEN : Formatting.RED),
                Text.literal("Suppresses mining effects").formatted(Formatting.GRAY),
                Text.literal("Left-click to toggle").formatted(Formatting.DARK_GRAY)
            ), mouseX, mouseY);
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_SILK_X, SLOT_ROW0_Y)) {
            if (handler.isSilkTouch()) {
                context.drawTooltip(this.textRenderer, java.util.List.of(
                    Text.literal("Silk Touch Augment").formatted(Formatting.WHITE),
                    Text.literal("Left-click to remove").formatted(Formatting.GRAY)
                ), mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer, java.util.List.of(
                    Text.literal("Silk Touch Augment").formatted(Formatting.WHITE),
                    Text.literal("Drag augment into this slot").formatted(Formatting.DARK_GRAY)
                ), mouseX, mouseY);
            }
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_SPEED_X, SLOT_ROW0_Y)) {
            if (handler.getSpeedLevel() > 0) {
                context.drawTooltip(this.textRenderer, java.util.List.of(
                    Text.literal("Speed Augment (Tier " + handler.getSpeedLevel() + ")").formatted(Formatting.WHITE),
                    Text.literal("Left-click to remove").formatted(Formatting.GRAY)
                ), mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer, java.util.List.of(
                    Text.literal("Speed Augment").formatted(Formatting.WHITE),
                    Text.literal("Drag augment into this slot").formatted(Formatting.DARK_GRAY)
                ), mouseX, mouseY);
            }
            return;
        }

        if (isHoveringSlot(relX, relY, SLOT_REDSTONE_TORCH_X, SLOT_ROW0_Y)) {
            java.util.List<Text> lines = new java.util.ArrayList<>();

            if (!handler.hasRegisteredQuarry()) {
                lines.add(Text.literal("Status: Unregistered").formatted(Formatting.YELLOW));
            } else {
                String state = switch (handler.getQuarryState()) {
                    case 4 -> "Scanning Metadata";
                    case 3 -> "Scanning";
                    case 2 -> "Mining";
                    case 1 -> "Paused";
                    default -> "Unknown";
                };
                lines.add(Text.literal("Status: " + state).formatted(Formatting.GRAY));
                lines.add(Text.literal("Level (Y): " + handler.getQuarryLevelY()).formatted(Formatting.GRAY));
                if (handler.isOutputJammed()) {
                    lines.add(Text.literal("Output: Jammed (full)").formatted(Formatting.RED));
                } else {
                    lines.add((handler.hasValidOutput()
                        ? Text.literal("Output: OK").formatted(Formatting.GREEN)
                        : Text.literal("Output: Not connected").formatted(Formatting.RED)));
                }

                if (handler.getQuarryState() == 1 && !handler.hasValidOutput()) {
                    lines.add(Text.literal("Cannot start until output is connected").formatted(Formatting.RED));
                }
                lines.add(Text.literal("Progress (est): " + handler.getProgressPercent() + "%").formatted(Formatting.GRAY));
                int eta = handler.getEtaSecondsEstimate();
                lines.add(Text.literal("ETA (est): " + formatEtaMinutesSeconds(eta)).formatted(Formatting.GRAY));
                lines.add(Text.literal("Remaining (est): " + handler.getRemainingEstimate() + " blocks").formatted(Formatting.GRAY));

                String ownerName = getOwnerNameFromControllerBE();
                if (ownerName == null) {
                    java.util.UUID owner = handler.getOwnerUuid();
                    if (owner != null && client != null && client.getNetworkHandler() != null) {
                        var entry = client.getNetworkHandler().getPlayerListEntry(owner);
                        if (entry != null && entry.getProfile() != null) {
                            Object profile = entry.getProfile();
                            try {
                                ownerName = (String) profile.getClass().getMethod("getName").invoke(profile);
                            } catch (ReflectiveOperationException ignored) {
                                // Some versions expose GameProfile as a record: name()
                                try {
                                    ownerName = (String) profile.getClass().getMethod("name").invoke(profile);
                                } catch (ReflectiveOperationException ignored2) {
                                    ownerName = null;
                                }
                            }
                        }
                    }
                }

                lines.add(Text.literal("Owner: " + (ownerName != null ? ownerName : "Unknown"))
                    .formatted(Formatting.GRAY));
            }

            context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() == 0 && client != null && client.interactionManager != null) {
            int relX = (int) click.x() - this.x;
            int relY = (int) click.y() - this.y;

            if (isHoveringSlot(relX, relY, SLOT_LEVER_X, SLOT_ROW0_Y)) {
                client.interactionManager.clickButton(handler.syncId, 0);
                return true;
            }

            if (isHoveringSlot(relX, relY, SLOT_BARRIER_X, SLOT_ROW0_Y)) {
                client.interactionManager.clickButton(handler.syncId, 1);
                return true;
            }

            if (isHoveringSlot(relX, relY, SLOT_HOPPER_X, SLOT_ROW0_Y)) {
                client.interactionManager.clickButton(handler.syncId, 4);
                return true;
            }

            if (isHoveringSlot(relX, relY, SLOT_POWER_X, SLOT_ROW0_Y)) {
                client.interactionManager.clickButton(handler.syncId, 2);
                return true;
            }

            if (isHoveringSlot(relX, relY, SLOT_CHUNKLOAD_X, SLOT_ROW1_Y)) {
                client.interactionManager.clickButton(handler.syncId, 3);
                return true;
            }

            if (isHoveringSlot(relX, relY, SLOT_CHUNK_PREVIEW_X, SLOT_ROW1_Y)) {
                client.interactionManager.clickButton(handler.syncId, 6);
                return true;
            }

            if (isHoveringSlot(relX, relY, SLOT_SILENT_X, SLOT_ROW1_Y)) {
                client.interactionManager.clickButton(handler.syncId, 5);
                return true;
            }
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);
        // No extra text; status is shown via torch tooltip.
    }
}
