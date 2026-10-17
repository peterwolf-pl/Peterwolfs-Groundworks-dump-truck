package com.piotrek.groundworksdumptruck.client.render;

import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Compact driver HUD showing dump-bed fill and engine load. */
public final class DumpTruckHudOverlay implements HudElement {

    public static final Identifier ID = GroundworksDumpTruckMod.id("hud_overlay");

    public static void register() {
        HudElementRegistry.addLast(ID, new DumpTruckHudOverlay());
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null
                || !(client.player.getVehicle() instanceof GroundworksDumpTruckEntity truck)) {
            return;
        }

        Font font = client.font;
        int x = 8;
        int y = 8;
        int barWidth = 140;
        int width = 220;
        String apState = truck.getAutopilotState();
        String activeRoute = truck.getActiveRoute();
        boolean hasAutopilot = apState != null && !apState.isEmpty() && !apState.equals("IDLE");

        int height = hasAutopilot ? 65 : 52;

        float fill = Math.clamp(truck.getFillRatio(), 0.0F, 1.0F);
        float engineLoad = Math.clamp(truck.getEngineLoad(), 0.0F, 1.0F);
        int units = truck.getCarriedUnits();
        float blocks = units / 512.0F;

        graphics.fill(x - 3, y - 3, x + width, y + height, 0x88000000);
        graphics.renderOutline(x - 3, y - 3, width + 3, height + 3, 0xFFFFAA00);

        graphics.drawString(font, Component.translatable("hud.pw_groundworks_dump_truck.title"), x, y, 0xFFFFAA00, true);

        int fillY = y + 13;
        Component fillLabel = fill >= 0.999F
                ? Component.translatable("hud.pw_groundworks_dump_truck.bed_full")
                : Component.translatable("hud.pw_groundworks_dump_truck.bed_load");
        graphics.drawString(font, fillLabel, x, fillY, 0xFFCCCCCC, true);

        int fillBarY = fillY + 9;
        graphics.fill(x, fillBarY, x + barWidth, fillBarY + 5, 0xFF2A2A2A);
        int fillWidth = Math.round(barWidth * fill);
        if (fillWidth > 0) {
            int color = fill >= 0.999F ? 0xFFFF3333 : 0xFFFFAA00;
            graphics.fill(x, fillBarY, x + fillWidth, fillBarY + 5, color);
        }
        graphics.drawString(
                font,
                Component.translatable(
                        "hud.pw_groundworks_dump_truck.capacity",
                        String.format("%.0f%%", fill * 100.0F),
                        units,
                        GroundworksDumpTruckEntity.BED_CAPACITY,
                        String.format("%.1f", blocks),
                        GroundworksDumpTruckEntity.BED_CAPACITY_BLOCKS
                ),
                x,
                fillBarY + 8,
                0xFFDDDDDD,
                true
        );

        Component load = Component.translatable(
                "hud.pw_groundworks_dump_truck.engine_load",
                String.format("%.0f%%", engineLoad * 100.0F)
        );
        graphics.drawString(font, load, x + width - font.width(load) - 3, y, 0xFFFFFFFF, true);

        if (hasAutopilot) {
            String routeName = activeRoute != null && !activeRoute.isEmpty() ? activeRoute : "default";
            Component apText = switch (apState) {
                case "RECORDING" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.recording", routeName);
                case "WAITING_FOR_LOAD" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.waiting_load");
                case "WAITING_IN_QUEUE" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.waiting_queue");
                case "DRIVING" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.driving", routeName);
                case "REVERSING" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.reversing");
                case "DUMPING" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.dumping");
                case "PARKED" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.parked");
                case "TO_SCREEN" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.to_screen");
                case "RETURNING" -> Component.translatable("hud.pw_groundworks_dump_truck.ap.returning");
                default -> CommonComponents.EMPTY;
            };
            graphics.drawString(font, apText, x, y + 53, 0xFFFFFFFF, true);
        }
    }
}
