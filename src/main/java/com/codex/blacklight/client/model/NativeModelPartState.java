package com.codex.blacklight.client.model;

import net.minecraft.client.model.geom.ModelPart;

/** Reusable snapshot of the native body projection, including biological limb masks. */
final class NativeModelPartState {
    private final ModelPart[] parts;
    private final float[] transforms;
    private final boolean[] visible,skipDraw;

    NativeModelPartState(ModelPart... parts) {
        this.parts=parts.clone();
        transforms=new float[parts.length*9];
        visible=new boolean[parts.length];skipDraw=new boolean[parts.length];
    }

    void capture() {
        for(int i=0;i<parts.length;i++) {
            var part=parts[i];int j=i*9;
            transforms[j]=part.x;transforms[j+1]=part.y;transforms[j+2]=part.z;
            transforms[j+3]=part.xRot;transforms[j+4]=part.yRot;transforms[j+5]=part.zRot;
            transforms[j+6]=part.xScale;transforms[j+7]=part.yScale;transforms[j+8]=part.zScale;
            visible[i]=part.visible;skipDraw[i]=part.skipDraw;
        }
    }

    void restore() {
        for(int i=0;i<parts.length;i++) {
            var part=parts[i];int j=i*9;
            part.x=transforms[j];part.y=transforms[j+1];part.z=transforms[j+2];
            part.xRot=transforms[j+3];part.yRot=transforms[j+4];part.zRot=transforms[j+5];
            part.xScale=transforms[j+6];part.yScale=transforms[j+7];part.zScale=transforms[j+8];
            part.visible=visible[i];part.skipDraw=skipDraw[i];
        }
    }
}
