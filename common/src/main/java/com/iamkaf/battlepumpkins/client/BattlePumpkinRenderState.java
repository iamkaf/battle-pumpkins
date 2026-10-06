package com.iamkaf.battlepumpkins.client;

import com.iamkaf.battlepumpkins.pumpkin.Face;
import com.iamkaf.battlepumpkins.pumpkin.Light;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class BattlePumpkinRenderState extends EntityRenderState {
    public Face face = Face.BLANK;
    public Light light = Light.NONE;
    public boolean lit;
    public boolean dormant;
    public float bodyYaw;
    /** Height of the current hop, in blocks. */
    public float hop;
    /** 1 when standing, below 1 when squashed on landing. */
    public float squash = 1.0F;
    public boolean hurt;
}
