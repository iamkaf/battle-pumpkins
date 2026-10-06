package com.iamkaf.battlepumpkins.client;

import com.iamkaf.battlepumpkins.Constants;
import com.iamkaf.battlepumpkins.pumpkin.BattlePumpkin;
import com.iamkaf.battlepumpkins.pumpkin.Face;
import com.iamkaf.battlepumpkins.pumpkin.Light;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//? if >=26.1
import net.minecraft.client.renderer.state.level.CameraRenderState;
//? if <26.1
/*import net.minecraft.client.renderer.state.CameraRenderState;*/
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Draws a Battle Pumpkin as a pumpkin block made of the vanilla pumpkin textures, so resource packs restyle it and a
 * sleeping one passes for a block. Carved pixels are drawn over the front: dark when unlit, full bright in the light's
 * colors when lit. Moving pumpkins hop and squash on landing.
 */
public class BattlePumpkinRenderer extends EntityRenderer<BattlePumpkin, BattlePumpkinRenderState> {
    private static final RenderType SIDE = RenderTypes.entitySolid(Identifier.withDefaultNamespace("textures/block/pumpkin_side.png"));
    private static final RenderType TOP = RenderTypes.entitySolid(Identifier.withDefaultNamespace("textures/block/pumpkin_top.png"));
    private static final RenderType CARVING = RenderTypes.entitySolid(Constants.id("textures/entity/carving.png"));
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float PIXEL = 1.0F / Face.SIZE;
    /** Lifts carved pixels off the front face so they do not z-fight with the skin. */
    private static final float CARVING_OFFSET = 0.002F;
    private static final float HOP_HEIGHT = 0.4F;
    /** How quickly hops follow each other as the walk animation advances; faster walking hops faster. */
    private static final float HOP_CADENCE = 0.5F;

    public BattlePumpkinRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
    }

    @Override
    public BattlePumpkinRenderState createRenderState() {
        return new BattlePumpkinRenderState();
    }

    @Override
    public void extractRenderState(BattlePumpkin pumpkin, BattlePumpkinRenderState state, float partialTick) {
        super.extractRenderState(pumpkin, state, partialTick);
        state.face = pumpkin.face();
        state.light = pumpkin.light();
        state.lit = pumpkin.isLit();
        state.dormant = pumpkin.isDormant();
        state.bodyYaw = Mth.rotLerp(partialTick, pumpkin.yBodyRotO, pumpkin.yBodyRot);
        state.hurt = pumpkin.hurtTime > 0;

        // Even a slow stroll gets full hops; the strength only fades them in and out as it starts and stops.
        float strength = Math.min(pumpkin.walkAnimation.speed(partialTick) * 4.0F, 1.0F);
        float arc = Math.abs(Mth.sin(pumpkin.walkAnimation.position(partialTick) * HOP_CADENCE));
        boolean hopping = !pumpkin.isDormant() && !pumpkin.isInSittingPose() && strength > 0.05F;
        state.hop = hopping ? arc * strength * HOP_HEIGHT : 0.0F;
        // Squashed flat as it lands, back to full height in the air.
        state.squash = hopping ? 1.0F - (1.0F - arc) * (1.0F - arc) * strength * 0.25F : 1.0F;
    }

    /** A sleeping pumpkin casts no shadow, so it passes for a block. */
    @Override
    protected float getShadowRadius(BattlePumpkinRenderState state) {
        return state.dormant ? 0.0F : super.getShadowRadius(state);
    }

    @Override
    public void submit(BattlePumpkinRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0F, state.hop, 0.0F);
        poseStack.rotateAround(new Quaternionf().rotationY((180.0F - state.bodyYaw) * Mth.DEG_TO_RAD), 0.0F, 0.0F, 0.0F);
        float stretch = 1.0F + (1.0F - state.squash) * 0.5F;
        poseStack.scale(stretch, state.squash, stretch);

        int light = state.lightCoords;
        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(state.hurt));
        collector.submitCustomGeometry(poseStack, SIDE, (pose, buffer) -> {
            Quad.FRONT.draw(buffer, pose, -1, overlay, light);
            Quad.BACK.draw(buffer, pose, -1, overlay, light);
            Quad.LEFT.draw(buffer, pose, -1, overlay, light);
            Quad.RIGHT.draw(buffer, pose, -1, overlay, light);
        });
        collector.submitCustomGeometry(poseStack, TOP, (pose, buffer) -> {
            Quad.TOP.draw(buffer, pose, -1, overlay, light);
            Quad.BOTTOM.draw(buffer, pose, -1, overlay, light);
        });
        if (!state.face.isBlank()) {
            Light glow = state.lit ? state.light : Light.NONE;
            int carvedLight = state.lit ? FULL_BRIGHT : light;
            collector.submitCustomGeometry(poseStack, CARVING, (pose, buffer) -> drawCarving(buffer, pose, state.face, glow, overlay, carvedLight));
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * One quad per carved pixel on the front face, which faces -Z before the body rotation. A lit face points its
     * normal up, the direction entity shading leaves brightest, so the glow keeps the light's colors. Drawing it in
     * the solid pass keeps it independent of each version's translucent draw order.
     */
    private static void drawCarving(VertexConsumer buffer, PoseStack.Pose pose, Face face, Light glow, int overlay, int light) {
        float z = -0.5F - CARVING_OFFSET;
        float normalY = glow.glows() ? 1.0F : 0.0F;
        float normalZ = glow.glows() ? 0.0F : -1.0F;
        for (int y = 0; y < Face.SIZE; y++) {
            for (int x = 0; x < Face.SIZE; x++) {
                if (!face.carved(x, y)) {
                    continue;
                }
                int color = face.surrounded(x, y) ? glow.innerColor() : glow.edgeColor();
                float left = 0.5F - x * PIXEL;
                float right = left - PIXEL;
                float top = 1.0F - y * PIXEL;
                float bottom = top - PIXEL;
                vertex(buffer, pose, left, top, z, 0.0F, 0.0F, color, overlay, light, 0.0F, normalY, normalZ);
                vertex(buffer, pose, left, bottom, z, 0.0F, 1.0F, color, overlay, light, 0.0F, normalY, normalZ);
                vertex(buffer, pose, right, bottom, z, 1.0F, 1.0F, color, overlay, light, 0.0F, normalY, normalZ);
                vertex(buffer, pose, right, top, z, 1.0F, 0.0F, color, overlay, light, 0.0F, normalY, normalZ);
            }
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int color, int overlay, int light, float normalX, float normalY, float normalZ) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    /**
     * The six faces of a block centred on the entity, each listed counter-clockwise as seen from outside, starting at
     * the corner the texture's top left maps to.
     */
    private enum Quad {
        FRONT(0, 0, -1, 0.5F, 1, -0.5F, 0.5F, 0, -0.5F, -0.5F, 0, -0.5F, -0.5F, 1, -0.5F),
        BACK(0, 0, 1, -0.5F, 1, 0.5F, -0.5F, 0, 0.5F, 0.5F, 0, 0.5F, 0.5F, 1, 0.5F),
        RIGHT(1, 0, 0, 0.5F, 1, 0.5F, 0.5F, 0, 0.5F, 0.5F, 0, -0.5F, 0.5F, 1, -0.5F),
        LEFT(-1, 0, 0, -0.5F, 1, -0.5F, -0.5F, 0, -0.5F, -0.5F, 0, 0.5F, -0.5F, 1, 0.5F),
        TOP(0, 1, 0, -0.5F, 1, -0.5F, -0.5F, 1, 0.5F, 0.5F, 1, 0.5F, 0.5F, 1, -0.5F),
        BOTTOM(0, -1, 0, -0.5F, 0, 0.5F, -0.5F, 0, -0.5F, 0.5F, 0, -0.5F, 0.5F, 0, 0.5F);

        private static final float[] U = {0.0F, 0.0F, 1.0F, 1.0F};
        private static final float[] V = {0.0F, 1.0F, 1.0F, 0.0F};

        private final float normalX;
        private final float normalY;
        private final float normalZ;
        private final float[] corners;

        Quad(float normalX, float normalY, float normalZ, float... corners) {
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
            this.corners = corners;
        }

        void draw(VertexConsumer buffer, PoseStack.Pose pose, int color, int overlay, int light) {
            for (int i = 0; i < 4; i++) {
                vertex(buffer, pose, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2], U[i], V[i],
                        color, overlay, light, normalX, normalY, normalZ);
            }
        }
    }
}
