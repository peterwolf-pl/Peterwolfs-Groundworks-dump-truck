package com.piotrek.groundworksdumptruck.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.server.permissions.Permissions;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import com.piotrek.groundworksdumptruck.route.DumpTruckAutopilot;
import com.piotrek.groundworksdumptruck.route.DumpTruckShuttle;
import com.piotrek.groundworksdumptruck.route.DumpTruckRoute;
import com.piotrek.groundworksdumptruck.route.DumpTruckRouteManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Commands for recording and playing autonomous haul routes for the Groundworks Dump Truck.
 */
public final class DumpTruckCommand {

    private DumpTruckCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dumptruck")
                        .then(Commands.literal("fill")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                                .then(Commands.literal("gold_gravel")
                                        .executes(ctx -> fillGoldGravel(ctx.getSource()))))
                        // Full syntax: /dumptruck route record start [name]
                        .then(Commands.literal("route")
                                .then(Commands.literal("record")
                                        .executes(ctx -> startRecord(ctx.getSource(), "default"))
                                        .then(Commands.literal("start")
                                                .executes(ctx -> startRecord(ctx.getSource(), "default"))
                                                .then(Commands.argument("name", StringArgumentType.word())
                                                        .executes(ctx -> startRecord(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                                        .then(Commands.literal("stop")
                                                .executes(ctx -> stopRecord(ctx.getSource()))))
                                .then(Commands.literal("play")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> playRoute(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true, false))
                                                .then(Commands.literal("force")
                                                        .executes(ctx -> playRoute(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true, true)))))
                                .then(Commands.literal("loop")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> playRoute(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true, false))))
                                .then(Commands.literal("stop")
                                        .executes(ctx -> stopAutopilot(ctx.getSource())))
                                .then(Commands.literal("list")
                                        .executes(ctx -> listRoutes(ctx.getSource())))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> deleteRoute(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))
                        // Convenient shortcuts: /dumptruck record [start/stop] [name]
                        .then(Commands.literal("record")
                                .executes(ctx -> startRecord(ctx.getSource(), "default"))
                                .then(Commands.literal("start")
                                        .executes(ctx -> startRecord(ctx.getSource(), "default"))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> startRecord(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.literal("stop")
                                        .executes(ctx -> stopRecord(ctx.getSource())))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> startRecord(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("play")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> playRoute(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true, false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> playRoute(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true, true)))))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stopAutopilot(ctx.getSource())))
                        .then(Commands.literal("shuttle")
                                .executes(ctx -> startShuttle(ctx.getSource()))
                                .then(Commands.literal("start").executes(ctx -> startShuttle(ctx.getSource())))
                                .then(Commands.literal("stop").executes(ctx -> stopAutopilot(ctx.getSource())))
                                .then(Commands.literal("status").executes(ctx -> showStatus(ctx.getSource()))))
                        .then(Commands.literal("status")
                                .executes(ctx -> showStatus(ctx.getSource())))
        );
    }

    private static int fillGoldGravel(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)
                || !(player.getVehicle() instanceof GroundworksDumpTruckEntity truck)) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.must_be_in_truck"));
            return 0;
        }
        var gravel = GranularMaterialRegistry.GRAVEL;
        if (gravel == null) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.gravel_unavailable"));
            return 0;
        }
        if (truck.storedComposition().unitsOf(gravel.id()) != truck.storedUnits()) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.empty_bed_first"));
            return 0;
        }
        int added = truck.acceptMaterial(gravel, truck.capacity() - truck.storedUnits());
        source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.gold_gravel_filled",
                truck.storedUnits(), truck.capacity(), added), false);
        return 1;
    }

    private static GroundworksDumpTruckEntity findTargetTruck(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return null;
        }

        // 1. If seated inside a truck
        if (player.getVehicle() instanceof GroundworksDumpTruckEntity truck) {
            return truck;
        }

        // 2. Raycast in eye look direction up to 14 blocks
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 reach = eye.add(look.scale(14.0D));
        AABB box = player.getBoundingBox().expandTowards(look.scale(14.0D)).inflate(2.0D);

        for (GroundworksDumpTruckEntity truck : player.level().getEntitiesOfClass(GroundworksDumpTruckEntity.class, box)) {
            AABB truckBox = truck.getBoundingBox().inflate(0.5D);
            if (truckBox.clip(eye, reach).isPresent()) {
                return truck;
            }
        }

        // 3. Fallback: nearest truck within 8 blocks
        List<GroundworksDumpTruckEntity> nearby = player.level().getEntitiesOfClass(
                GroundworksDumpTruckEntity.class, player.getBoundingBox().inflate(8.0D));
        if (!nearby.isEmpty()) {
            nearby.sort(Comparator.comparingDouble(t -> t.distanceToSqr(player)));
            return nearby.get(0);
        }

        return null;
    }

    private static int startRecord(CommandSourceStack source, String routeName) {
        GroundworksDumpTruckEntity truck = findTargetTruck(source);
        if (truck == null) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.must_be_in_or_looking_at"));
            return 0;
        }

        truck.getAutopilot().startRecording(routeName);
        source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.record_started",
                truck.getId(), routeName), false);
        return 1;
    }

    private static int stopRecord(CommandSourceStack source) {
        GroundworksDumpTruckEntity truck = findTargetTruck(source);
        if (truck == null) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.must_be_in_or_looking_at"));
            return 0;
        }

        DumpTruckAutopilot autopilot = truck.getAutopilot();
        if (!autopilot.isRecording()) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.not_recording"));
            return 0;
        }

        DumpTruckRoute route = autopilot.stopRecording(source.getServer());
        if (route == null || route.isEmpty()) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.no_waypoints"));
            return 0;
        }

        Component dumpingStatus = route.hasDumping()
                ? Component.translatable("command.pw_groundworks_dump_truck.yes")
                : Component.translatable("command.pw_groundworks_dump_truck.none");

        source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.record_stopped",
                truck.getId(), route.name(), route.size(), route.totalLengthMeters(), dumpingStatus), false);
        return 1;
    }

    private static int playRoute(CommandSourceStack source, String routeName, boolean loop, boolean forceStart) {
        GroundworksDumpTruckEntity truck = findTargetTruck(source);
        if (truck == null) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.must_be_in_or_looking_at"));
            return 0;
        }

        DumpTruckRoute route = DumpTruckRouteManager.getRoute(source.getServer(), routeName);
        if (route == null || route.isEmpty()) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.route_not_found", routeName));
            return 0;
        }

        truck.getAutopilot().startPlayback(route, loop, forceStart);

        if (truck.getAutopilot().getState() == DumpTruckAutopilot.AutopilotState.WAITING_FOR_LOAD) {
            source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.route_loaded_waiting",
                    truck.getId(), routeName), false);
        } else {
            source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.route_started",
                    truck.getId(), routeName), false);
        }
        return 1;
    }

    private static int startShuttle(CommandSourceStack source) {
        GroundworksDumpTruckEntity truck = findTargetTruck(source);
        if (truck == null || !(truck.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.must_be_in_or_looking_at"));
            return 0;
        }

        DumpTruckShuttle.Result result = truck.getAutopilot().startShuttle(level);
        return switch (result) {
            case NO_EXCAVATOR -> {
                source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.no_excavator"));
                yield 0;
            }
            case NO_SCREEN -> {
                source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.shuttle_no_screen",
                        truck.getId()), false);
                yield 1;
            }
            case ARMED -> {
                source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.shuttle_armed",
                        truck.getId()), false);
                yield 1;
            }
        };
    }

    private static int stopAutopilot(CommandSourceStack source) {
        GroundworksDumpTruckEntity truck = findTargetTruck(source);
        if (truck == null) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.must_be_in_or_looking_at"));
            return 0;
        }

        truck.getAutopilot().stop();
        source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.stopped", truck.getId()), false);
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        GroundworksDumpTruckEntity truck = findTargetTruck(source);
        if (truck == null) {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.must_be_in_or_looking_at"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "command.pw_groundworks_dump_truck.status_full",
                truck.getId(),
                truck.getAutopilot().getStatusReport(),
                truck.storedUnits(),
                truck.capacity(),
                truck.getFillRatio() * 100.0F,
                truck.getBedAngle()
        ), false);
        return 1;
    }

    private static int listRoutes(CommandSourceStack source) {
        List<String> routes = DumpTruckRouteManager.listRouteNames(source.getServer());
        if (routes.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.no_routes"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.routes_list",
                routes.size(), String.join(", ", routes)), false);
        return 1;
    }

    private static int deleteRoute(CommandSourceStack source, String routeName) {
        boolean deleted = DumpTruckRouteManager.deleteRoute(source.getServer(), routeName);
        if (deleted) {
            source.sendSuccess(() -> Component.translatable("command.pw_groundworks_dump_truck.route_deleted", routeName), false);
            return 1;
        } else {
            source.sendFailure(Component.translatable("command.pw_groundworks_dump_truck.route_not_found_delete", routeName));
            return 0;
        }
    }
}
