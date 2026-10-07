package com.piotrek.groundworksdumptruck.client.render;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Compact driver HUD showing dump-bed fill and engine load. */
public final class DumpTruckHudOverlay implements HudElement {

    public static final Identifier ID = GroundworksDumpTruckMod.id("hud_overlay");

    public static void register() {
        HudElementRegistry.addLast(ID, new DumpTruckHudOverlay());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null
                || !(client.player.getVehicle() instanceof GroundworksDumpTruckEntity truck)) {
            return;
        }

        Font font = client.font;
        int x = 8;
        int y = 8;
        int barWidth = 140;
        int width = 208;
        int height = 52;

        float fill = Math.clamp(truck.getFillRatio(), 0.0F, 1.0F);
        float engineLoad = Math.clamp(truck.getEngineLoad(), 0.0F, 1.0F);
        int units = truck.getCarriedUnits();
        float blocks = units / 512.0F;

        extractor.fill(x - 3, y - 3, x + width, y + height, 0x88000000);
        extractor.outline(x - 3, y - 3, width + 3, height + 3, 0xFFFFAA00);

        extractor.text(font, "§6§lWywrotka Groundworks", x, y, 0xFFFFFFFF, true);

        int fillY = y + 13;
        String fillLabel = fill >= 0.999F ? "§c§lKIPA PEŁNA" : "§7Załadunek kipy";
        extractor.text(font, fillLabel, x, fillY, 0xFFCCCCCC, true);

        int fillBarY = fillY + 9;
        extractor.fill(x, fillBarY, x + barWidth, fillBarY + 5, 0xFF2A2A2A);
        int fillWidth = Math.round(barWidth * fill);
        if (fillWidth > 0) {
            int color = fill >= 0.999F ? 0xFFFF3333 : 0xFFFFAA00;
            extractor.fill(x, fillBarY, x + fillWidth, fillBarY + 5, color);
        }
        extractor.text(
                font,
                String.format("%.0f%%  %d/%d u  %.1f/%d bloków",
                        fill * 100.0F,
                        units,
                        GroundworksDumpTruckEntity.BED_CAPACITY,
                        blocks,
                        GroundworksDumpTruckEntity.BED_CAPACITY_BLOCKS),
                x,
                fillBarY + 8,
                0xFFDDDDDD,
                true
        );

        String load = String.format("§7Silnik: §f%.0f%%", engineLoad * 100.0F);
        extractor.text(font, load, x + width - font.width(load) - 3, y, 0xFFFFFFFF, true);
    }
}
