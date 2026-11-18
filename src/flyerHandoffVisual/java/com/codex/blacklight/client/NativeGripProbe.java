package com.codex.blacklight.client;

import com.codex.blacklight.client.model.ClawArmModel;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;

/** QA-only anchor capture inside the actual third-person weapon draw.
 * Includes the actual model stack and the skin's blended wrist deformation;
 * never calls the animation sampler again after the frame. */
public final class NativeGripProbe {
    public record Sample(Vec3 point,Vec3 directPoint,double decompositionError,double time,int stage,float growth,boolean liquid) {}
    private static final Map<Integer,Sample> SAMPLES=new HashMap<>();
    private static AbstractClientPlayer owner;
    private static float partial;
    private static Matrix4f directHand;
    public static void reset(){SAMPLES.clear();owner=null;}
    public static void begin(AbstractClientPlayer player,float p,PoseStack stack,Matrix4f[] poses){
        owner=player;partial=p;directHand=new Matrix4f(stack.last().pose())
                .mul(poses[com.codex.blacklight.animation.BlacklightSkeleton.get().handR.getId()]);
    }
    public static void end(){owner=null;}
    public static Sample sample(int id){return SAMPLES.get(id);}
    public static void draw(ClawArmModel model,String joint,PoseStack stack,float growth,Matrix4f wrist,
                            com.codex.blacklight.client.animation.BiomassSurface.Frame surface){
        if(owner==null||!joint.equals("Hand_R"))return;
        var form=ModAttachments.getData(owner,ModAttachments.PLAYER_STATE).getActivePower();
        if(form!=PowerForm.CLAWS&&form!=PowerForm.BLADE)return;
        var local=model.anchor(form==PowerForm.BLADE?"bladeGrip_R":"palm_R",new Vector3f(Float.NaN));
        if(!Float.isFinite(local.x))return;
        local=new Vector3f(local);
        if(wrist!=null){
            float w=Math.max(0,Math.min(1,(local.y-.28F)/.10F));w=w*w*(3-2*w);
            local.lerp(wrist.transformPosition(new Vector3f(local)),w);
        }
        var direct=directHand.transformPosition(new Vector3f(local));
        var drawn=stack.last().pose().transformPosition(local);
        // Forge entity stacks contain the view rotation; return both probes to world space.
        var inverseView=com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix();inverseView.transform(drawn);inverseView.transform(direct);
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        SAMPLES.put(owner.getId(),new Sample(camera.add(drawn.x,drawn.y,drawn.z),camera.add(direct.x,direct.y,direct.z),drawn.distance(direct),
                (double)owner.level().getGameTime()+partial,ModAttachments.getData(owner,ModAttachments.GRAB_STAGE),growth,surface.liquid()));
    }
}
