package com.piotrek.groundworksdumptruck.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.piotrek.groundworks.api.material.GranularComposition;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworksdumptruck.entity.GroundworksDumpTruckEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * Generated loose-material mesh for the dump body.
 *
 * <p>The pile is not a cuboid. Surface height is crowned and slightly irregular.
 * Mixed Groundworks loads are textured per patch according to exact integer
 * composition. Cobblestone uses the same coarse visual-cluster rule as the
 * Groundworks terrain renderer.</p>
 */
public final class DumpTruckBedLoadRenderer {

    private static final int X_SEGMENTS = 8;
    private static final int Z_SEGMENTS = 12;
    private static final long VISUAL_SEED = 0x4D554D5054525543L;

    // Inner faces of the dump body, in bed-local pixels. A full load seals
    // slightly into these faces so the pile meets the rim instead of floating
    // inside the walls, tailgate and bulkhead.
    private static final float INNER_HALF_WIDTH_PX = 16.0F;
    private static final float INNER_TAIL_Z_PX = 2.0F;
    private static final float INNER_BULKHEAD_Z_PX = 64.0F;
    private static final float FULL_RIM_SEAL_PX = 0.35F;

    private DumpTruckBedLoadRenderer() {}

    public static void submit(
            DumpTruckRenderState state,
            PoseStack stack,
            SubmitNodeCollector collector
    ) {
        if (state.carriedUnits <= 0 || state.fillRatio <= 0.0F) {
            return;
        }

        GranularComposition composition = compositionFrom(state);
        if (composition.isEmpty()) {
            return;
        }

        float fill = Math.clamp(state.fillRatio, 0.0F, 1.0F);

        stack.pushPose();
        stack.translate(
                0.0F,
                0.0F,
                GroundworksDumpTruckEntity.BED_PIVOT_Z_PX / 16.0F
        );
        stack.rotateDegrees(Axis.XP, state.bedAngle);

        int[] counts = composition.toArray();
        for (int materialId = 1; materialId < counts.length; materialId++) {
            if (counts[materialId] <= 0) {
                continue;
            }

            GranularMaterial material =
                    GranularMaterialRegistry.byId(materialId);
            Identifier texture = textureFor(material);
            if (texture == null) {
                continue;
            }

            final int selectedMaterialId = materialId;
            collector.submitCustomGeometry(
                    stack,
                    RenderTypes.entityCutout(texture),
                    (pose, consumer) -> emitLoadForMaterial(
                            consumer,
                            pose,
                            fill,
                            state.lightCoords,
                            composition,
                            selectedMaterialId
                    )
            );
        }

        stack.popPose();
    }

    private static GranularComposition compositionFrom(
            DumpTruckRenderState state
    ) {
        GranularComposition composition =
                new GranularComposition();

        addIfPresent(
                composition,
                GranularMaterialRegistry.DIRT,
                state.dirtUnits
        );
        addIfPresent(
                composition,
                GranularMaterialRegistry.SAND,
                state.sandUnits
        );
        addIfPresent(
                composition,
                GranularMaterialRegistry.GRAVEL,
                state.gravelUnits
        );
        addIfPresent(
                composition,
                GranularMaterialRegistry.COBBLESTONE,
                state.cobblestoneUnits
        );

        // Compatibility fallback for an old save or an unknown future material.
        if (composition.isEmpty()
                && state.carriedMaterialId > 0
                && state.carriedUnits > 0) {
            composition.add(
                    GranularMaterialRegistry.byId(
                            state.carriedMaterialId
                    ),
                    state.carriedUnits
            );
        }

        return composition;
    }

    private static void addIfPresent(
            GranularComposition composition,
            GranularMaterial material,
            int units
    ) {
        if (material != null
                && material.id() > 0
                && units > 0) {
            composition.add(material, units);
        }
    }

    private static Identifier textureFor(
            GranularMaterial material
    ) {
        if (material == null
                || material.id() == 0
                || material.sourceBlock() == null) {
            return null;
        }

        Identifier blockId =
                BuiltInRegistries.BLOCK.getKey(
                        material.sourceBlock()
                );

        if (blockId == null) {
            return null;
        }

        return Identifier.fromNamespaceAndPath(
                blockId.getNamespace(),
                "textures/block/" + blockId.getPath() + ".png"
        );
    }

    private static void emitLoadForMaterial(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float fill,
            int packedLight,
            GranularComposition composition,
            int selectedMaterialId
    ) {
        // Stay heaped in the middle until the bed is nearly full, then reach
        // the walls. A square-root spread covered the body too evenly, too early.
        float spread = (float) Math.pow(fill, 1.55F);
        float halfWidthPx =
                lerp(7.0F, INNER_HALF_WIDTH_PX + FULL_RIM_SEAL_PX, spread);
        float backZPx =
                lerp(22.0F, INNER_TAIL_Z_PX - FULL_RIM_SEAL_PX, spread);
        float frontZPx =
                lerp(44.0F, INNER_BULKHEAD_Z_PX + FULL_RIM_SEAL_PX, spread);
        float sideDrop = lerp(0.55F, 0.10F, spread);
        float longDrop = lerp(0.46F, 0.18F, spread);

        float baseYPx = GroundworksDumpTruckEntity.BED_LOAD_BASE_Y_PX;
        float peakRisePx =
                lerp(
                        3.0F,
                        GroundworksDumpTruckEntity.BED_FULL_PILE_RISE_PX,
                        fill
                );

        Vector3f[][] vertices =
                new Vector3f[X_SEGMENTS + 1][Z_SEGMENTS + 1];

        for (int x = 0; x <= X_SEGMENTS; x++) {
            float fx = (float) x / X_SEGMENTS;
            float localX =
                    lerp(-halfWidthPx, halfWidthPx, fx);
            float nx =
                    Math.abs((fx - 0.5F) * 2.0F);

            for (int z = 0; z <= Z_SEGMENTS; z++) {
                float fz = (float) z / Z_SEGMENTS;
                float localZ =
                        lerp(backZPx, frontZPx, fz);

                float longitudinal =
                        Math.abs((fz - 0.58F) / 0.58F);

                float profile = 1.0F
                        - sideDrop * nx * nx
                        - longDrop
                        * longitudinal
                        * longitudinal
                        + lump(fx, fz) * 0.38F;

                profile = Math.clamp(
                        profile,
                        0.12F,
                        1.12F
                );

                float surfaceYPx =
                        baseYPx
                                - peakRisePx * profile;

                vertices[x][z] =
                        px(
                                localX,
                                surfaceYPx,
                                localZ
                        );
            }
        }

        for (int x = 0; x < X_SEGMENTS; x++) {
            for (int z = 0; z < Z_SEGMENTS; z++) {
                int sampled =
                        composition.sampleVisualMaterial(
                                VISUAL_SEED,
                                x,
                                7,
                                z
                        );

                if (sampled != selectedMaterialId) {
                    continue;
                }

                Vector3f v00 = vertices[x][z];
                Vector3f v01 = vertices[x][z + 1];
                Vector3f v11 = vertices[x + 1][z + 1];
                Vector3f v10 = vertices[x + 1][z];

                Vector3f normal =
                        faceNormal(v00, v01, v11);

                emit(
                        consumer,
                        pose,
                        v00,
                        normal,
                        0.0F,
                        0.0F,
                        packedLight
                );
                emit(
                        consumer,
                        pose,
                        v01,
                        normal,
                        0.0F,
                        1.0F,
                        packedLight
                );
                emit(
                        consumer,
                        pose,
                        v11,
                        normal,
                        1.0F,
                        1.0F,
                        packedLight
                );
                emit(
                        consumer,
                        pose,
                        v10,
                        normal,
                        1.0F,
                        0.0F,
                        packedLight
                );
            }
        }

        emitSideX(
                consumer,
                pose,
                vertices,
                0,
                baseYPx,
                -1.0F,
                packedLight,
                composition,
                selectedMaterialId
        );
        emitSideX(
                consumer,
                pose,
                vertices,
                X_SEGMENTS,
                baseYPx,
                1.0F,
                packedLight,
                composition,
                selectedMaterialId
        );
        emitSideZ(
                consumer,
                pose,
                vertices,
                0,
                baseYPx,
                -1.0F,
                packedLight,
                composition,
                selectedMaterialId
        );
        emitSideZ(
                consumer,
                pose,
                vertices,
                Z_SEGMENTS,
                baseYPx,
                1.0F,
                packedLight,
                composition,
                selectedMaterialId
        );
    }

    private static void emitSideX(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f[][] vertices,
            int xIndex,
            float baseYPx,
            float normalX,
            int packedLight,
            GranularComposition composition,
            int selectedMaterialId
    ) {
        Vector3f normal =
                new Vector3f(normalX, 0.0F, 0.0F);

        for (int z = 0; z < Z_SEGMENTS; z++) {
            int sampled =
                    composition.sampleVisualMaterial(
                            VISUAL_SEED,
                            xIndex,
                            3,
                            z
                    );

            if (sampled != selectedMaterialId) {
                continue;
            }

            Vector3f top0 =
                    vertices[xIndex][z];
            Vector3f top1 =
                    vertices[xIndex][z + 1];
            Vector3f bottom0 =
                    new Vector3f(
                            top0.x,
                            baseYPx / 16.0F,
                            top0.z
                    );
            Vector3f bottom1 =
                    new Vector3f(
                            top1.x,
                            baseYPx / 16.0F,
                            top1.z
                    );

            emit(
                    consumer,
                    pose,
                    bottom0,
                    normal,
                    0.0F,
                    1.0F,
                    packedLight
            );
            emit(
                    consumer,
                    pose,
                    bottom1,
                    normal,
                    1.0F,
                    1.0F,
                    packedLight
            );
            emit(
                    consumer,
                    pose,
                    top1,
                    normal,
                    1.0F,
                    0.0F,
                    packedLight
            );
            emit(
                    consumer,
                    pose,
                    top0,
                    normal,
                    0.0F,
                    0.0F,
                    packedLight
            );
        }
    }

    private static void emitSideZ(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f[][] vertices,
            int zIndex,
            float baseYPx,
            float normalZ,
            int packedLight,
            GranularComposition composition,
            int selectedMaterialId
    ) {
        Vector3f normal =
                new Vector3f(0.0F, 0.0F, normalZ);

        for (int x = 0; x < X_SEGMENTS; x++) {
            int sampled =
                    composition.sampleVisualMaterial(
                            VISUAL_SEED,
                            x,
                            3,
                            zIndex
                    );

            if (sampled != selectedMaterialId) {
                continue;
            }

            Vector3f top0 =
                    vertices[x][zIndex];
            Vector3f top1 =
                    vertices[x + 1][zIndex];
            Vector3f bottom0 =
                    new Vector3f(
                            top0.x,
                            baseYPx / 16.0F,
                            top0.z
                    );
            Vector3f bottom1 =
                    new Vector3f(
                            top1.x,
                            baseYPx / 16.0F,
                            top1.z
                    );

            emit(
                    consumer,
                    pose,
                    bottom0,
                    normal,
                    0.0F,
                    1.0F,
                    packedLight
            );
            emit(
                    consumer,
                    pose,
                    bottom1,
                    normal,
                    1.0F,
                    1.0F,
                    packedLight
            );
            emit(
                    consumer,
                    pose,
                    top1,
                    normal,
                    1.0F,
                    0.0F,
                    packedLight
            );
            emit(
                    consumer,
                    pose,
                    top0,
                    normal,
                    0.0F,
                    0.0F,
                    packedLight
            );
        }
    }

    private static void emit(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f vertex,
            Vector3f normal,
            float u,
            float v,
            int packedLight
    ) {
        consumer.addVertex(
                        pose,
                        vertex.x,
                        vertex.y,
                        vertex.z
                )
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(
                        pose,
                        normal.x,
                        normal.y,
                        normal.z
                );
    }

    private static Vector3f faceNormal(
            Vector3f a,
            Vector3f b,
            Vector3f c
    ) {
        Vector3f ab =
                new Vector3f(b).sub(a);
        Vector3f ac =
                new Vector3f(c).sub(a);

        return ab.cross(ac).normalize();
    }

    private static Vector3f px(
            float x,
            float y,
            float z
    ) {
        return new Vector3f(
                x / 16.0F,
                y / 16.0F,
                z / 16.0F
        );
    }

    private static float lump(float x, float z) {
        float coarse = hash(x * 2.6F + 0.2F, z * 1.7F + 0.4F);
        float fine = hash(x * 6.4F + 1.3F, z * 5.1F + 0.8F);
        return coarse * 0.72F + fine * 0.28F - 0.5F;
    }

    private static float hash(float x, float z) {
        float n = (float) Math.sin(x * 127.1F + z * 311.7F) * 43758.5453F;
        return n - (float) Math.floor(n);
    }

    private static float lerp(
            float a,
            float b,
            float t
    ) {
        return a + (b - a) * t;
    }
}
