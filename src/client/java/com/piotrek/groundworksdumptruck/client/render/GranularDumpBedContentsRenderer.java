package com.piotrek.groundworksdumptruck.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * Renders cargo as an irregular loose-material mound inside the dump body.
 *
 * <p>The geometry is material-agnostic. The current Groundworks material
 * supplies its source block texture, so dirt, sand, gravel, cobblestone and
 * future registered materials can reuse the same cargo mesh.
 */
public final class GranularDumpBedContentsRenderer {

    private static final float BED_PIVOT_Z_PX = -44.0F;
    private static final int X_SEGMENTS = 8;
    private static final int Z_SEGMENTS = 12;

    private GranularDumpBedContentsRenderer() {}

    public static void submit(
            DumpTruckRenderState state,
            PoseStack stack,
            SubmitNodeCollector collector
    ) {
        if (state.carriedUnits <= 0 || state.carriedMaterialId <= 0 || state.fillRatio <= 0.0F) {
            return;
        }

        GranularMaterial material = GranularMaterialRegistry.byId(state.carriedMaterialId);
        Identifier texture = textureFor(material);
        if (texture == null) {
            return;
        }

        float fill = Math.clamp(state.fillRatio, 0.0F, 1.0F);

        stack.pushPose();

        // Follow the dump body hinge exactly.
        stack.translate(0.0F, 0.0F, BED_PIVOT_Z_PX / 16.0F);
        stack.rotateDegrees(Axis.XP, -state.bedAngle);

        collector.submitCustomGeometry(
                stack,
                RenderTypes.entityCutout(texture),
                (pose, consumer) -> emitLoad(consumer, pose, fill, state.lightCoords)
        );

        stack.popPose();
    }

    static Identifier textureFor(GranularMaterial material) {
        if (material == null || material.id() <= 0 || material.sourceBlock() == null) {
            return null;
        }

        Identifier blockId = BuiltInRegistries.BLOCK.getKey(material.sourceBlock());
        if (blockId == null) {
            return null;
        }

        return Identifier.fromNamespaceAndPath(
                blockId.getNamespace(),
                "textures/block/" + blockId.getPath() + ".png"
        );
    }

    private static void emitLoad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float fill,
            int packedLight
    ) {
        // Low loads form a compact pile near the centre. More material expands
        // the footprint until it nearly touches the steel side walls.
        float footprint = (float) Math.sqrt(fill);
        float halfWidthPx = lerp(9.0F, 19.0F, footprint);
        float rearZPx = lerp(25.0F, 4.0F, footprint);
        float frontZPx = lerp(42.0F, 60.0F, footprint);

        float floorYPx = -6.2F;
        float peakRisePx = lerp(3.0F, 15.5F, fill);

        Vector3f[][] vertices = new Vector3f[X_SEGMENTS + 1][Z_SEGMENTS + 1];

        for (int x = 0; x <= X_SEGMENTS; x++) {
            float fx = (float) x / X_SEGMENTS;
            float localX = lerp(-halfWidthPx, halfWidthPx, fx);
            float nx = Math.abs((fx - 0.5F) * 2.0F);

            for (int z = 0; z <= Z_SEGMENTS; z++) {
                float fz = (float) z / Z_SEGMENTS;
                float localZ = lerp(rearZPx, frontZPx, fz);

                float longitudinal = Math.abs((fz - 0.48F) * 2.0F);
                float profile = 1.0F
                        - 0.48F * nx * nx
                        - 0.42F * longitudinal * longitudinal;

                // Deterministic variation gives the surface a loose pile shape
                // without flicker or per-frame random geometry.
                float irregular = 0.12F
                        * (float) Math.sin((fx * 3.0F + fz * 4.0F) * Math.PI)
                        + 0.06F
                        * (float) Math.cos((fx * 5.0F - fz * 2.0F) * Math.PI);

                float rise = peakRisePx * Math.clamp(profile + irregular, 0.06F, 1.0F);
                vertices[x][z] = px(localX, floorYPx - rise, localZ);
            }
        }

        // Faceted top surface.
        for (int x = 0; x < X_SEGMENTS; x++) {
            for (int z = 0; z < Z_SEGMENTS; z++) {
                Vector3f v00 = vertices[x][z];
                Vector3f v01 = vertices[x][z + 1];
                Vector3f v11 = vertices[x + 1][z + 1];
                Vector3f v10 = vertices[x + 1][z];

                Vector3f normal = faceNormal(v00, v01, v11);

                emit(consumer, pose, v00, normal,
                        (float) x / X_SEGMENTS,
                        (float) z / Z_SEGMENTS,
                        packedLight);
                emit(consumer, pose, v01, normal,
                        (float) x / X_SEGMENTS,
                        (float) (z + 1) / Z_SEGMENTS,
                        packedLight);
                emit(consumer, pose, v11, normal,
                        (float) (x + 1) / X_SEGMENTS,
                        (float) (z + 1) / Z_SEGMENTS,
                        packedLight);
                emit(consumer, pose, v10, normal,
                        (float) (x + 1) / X_SEGMENTS,
                        (float) z / Z_SEGMENTS,
                        packedLight);
            }
        }

        // Close the mound down to the bed floor. This makes the cargo appear
        // volumetric from the sides without ever becoming a rectangular cuboid.
        emitSideX(consumer, pose, vertices, 0, floorYPx, -1.0F, packedLight);
        emitSideX(consumer, pose, vertices, X_SEGMENTS, floorYPx, 1.0F, packedLight);
        emitSideZ(consumer, pose, vertices, 0, floorYPx, -1.0F, packedLight);
        emitSideZ(consumer, pose, vertices, Z_SEGMENTS, floorYPx, 1.0F, packedLight);
    }

    private static void emitSideX(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f[][] vertices,
            int xIndex,
            float floorYPx,
            float normalX,
            int packedLight
    ) {
        Vector3f normal = new Vector3f(normalX, 0.0F, 0.0F);

        for (int z = 0; z < Z_SEGMENTS; z++) {
            Vector3f top0 = vertices[xIndex][z];
            Vector3f top1 = vertices[xIndex][z + 1];
            Vector3f bottom1 = new Vector3f(top1.x, floorYPx / 16.0F, top1.z);
            Vector3f bottom0 = new Vector3f(top0.x, floorYPx / 16.0F, top0.z);

            emit(consumer, pose, bottom0, normal, 0.0F, 1.0F, packedLight);
            emit(consumer, pose, bottom1, normal, 1.0F, 1.0F, packedLight);
            emit(consumer, pose, top1, normal, 1.0F, 0.0F, packedLight);
            emit(consumer, pose, top0, normal, 0.0F, 0.0F, packedLight);
        }
    }

    private static void emitSideZ(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f[][] vertices,
            int zIndex,
            float floorYPx,
            float normalZ,
            int packedLight
    ) {
        Vector3f normal = new Vector3f(0.0F, 0.0F, normalZ);

        for (int x = 0; x < X_SEGMENTS; x++) {
            Vector3f top0 = vertices[x][zIndex];
            Vector3f top1 = vertices[x + 1][zIndex];
            Vector3f bottom1 = new Vector3f(top1.x, floorYPx / 16.0F, top1.z);
            Vector3f bottom0 = new Vector3f(top0.x, floorYPx / 16.0F, top0.z);

            emit(consumer, pose, bottom0, normal, 0.0F, 1.0F, packedLight);
            emit(consumer, pose, bottom1, normal, 1.0F, 1.0F, packedLight);
            emit(consumer, pose, top1, normal, 1.0F, 0.0F, packedLight);
            emit(consumer, pose, top0, normal, 0.0F, 0.0F, packedLight);
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
        consumer.addVertex(pose, vertex.x, vertex.y, vertex.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }

    private static Vector3f faceNormal(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f ab = new Vector3f(b).sub(a);
        Vector3f ac = new Vector3f(c).sub(a);
        return ab.cross(ac).normalize();
    }

    private static Vector3f px(float x, float y, float z) {
        return new Vector3f(x / 16.0F, y / 16.0F, z / 16.0F);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
