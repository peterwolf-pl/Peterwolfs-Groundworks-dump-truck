package com.piotrek.groundworksdumptruck.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.groundworksdumptruck.GroundworksDumpTruckMod;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class DumpTruckKeyBindings {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(GroundworksDumpTruckMod.id("controls"));

    public static KeyMapping KEY_BED_UP;
    public static KeyMapping KEY_BED_DOWN;

    private DumpTruckKeyBindings() {}

    public static void register() {
        KEY_BED_UP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_dump_truck.bed_up",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_UP,
                CATEGORY
        ));

        KEY_BED_DOWN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_dump_truck.bed_down",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_DOWN,
                CATEGORY
        ));
    }
}
