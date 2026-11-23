package com.codex.blacklight.client.renderer.layer;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.model.BlacklightPlayerModel;
import com.codex.blacklight.client.model.BlacklightSkinWeights;
import static com.codex.blacklight.client.renderer.ConsumeAnatomy.*;
import static com.codex.blacklight.client.renderer.layer.SymbiotePaths.*;
import com.codex.blacklight.client.renderer.ConsumedMobVisuals;
import com.codex.blacklight.item.SymbioteCharm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Moving surface reflections follow the equipped regions through both player renderers. */
public final class SymbioteLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final Matrix4f[] inverseBind, skin;

    public SymbioteLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> owner) {
        super(owner);
        inverseBind = PlayerMotionController.skeleton().getPoseMatrices(new BlacklightPose());
        skin = new Matrix4f[inverseBind.length];
        for (int i = 0; i < skin.length; i++) {
            inverseBind[i].invert();
            skin[i] = new Matrix4f();
        }
    }

    @Override public void render(PoseStack stack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        int worn = SymbioteCharm.wearing(player, EquipmentSlot.HEAD) ? CROWN : 0;
        if (SymbioteCharm.wearing(player, EquipmentSlot.CHEST)) worn |= CHEST;
        if (SymbioteCharm.wearing(player, EquipmentSlot.LEGS)) worn |= LEGS;
        if (SymbioteCharm.wearing(player, EquipmentSlot.FEET)) worn |= FEET;
        if (worn == 0) return;
        var model = getParentModel();
        boolean nativePose = model instanceof BlacklightPlayerModel nativeBody && nativeBody.matrices() != null;
        if (nativePose) {
            var body = (BlacklightPlayerModel) model;
            var matrices = body.matrices();
            for (int i = 0; i < skin.length; i++) {
                if (body.mercerFrame() != null) skin[i].set(body.mercerFrame().skin()[i]);
                else skin[i].set(matrices[i]).mul(inverseBind[i]);
            }
        }
        var frame = new Frame(stack, light, player.tickCount + (double)partial, nativePose);
        var surface = buffers.getBuffer(RenderType.entityTranslucent(ConsumedMobVisuals.WHITE));
        long seed = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits();
        boolean slim = "slim".equals(player.getModelName());
        for (int i = 0; i < FLOWS.length; i++) {
            Flow flow = FLOWS[i];
            if ((worn & flow.slots()) == flow.slots()) frame.draw(surface, flow, seed, i, slim);
        }
    }

    private final class Frame {
        final PoseStack stack;
        final int light;
        final double time;
        final boolean nativePose;
        Frame(PoseStack stack, int light, double time, boolean nativePose) {
            this.stack=stack; this.light=light; this.time=time; this.nativePose=nativePose;
        }

        Vec3 point(int region,double x,double y,double z) {
            if (nativePose) {
                var weight=BlacklightSkinWeights.sample(region,(float)y,false);
                var rig=PlayerMotionController.skeleton();
                var v=new Vector3f((float)-x/16,1.501F-(float)y/16,(float)z/16);
                var other=new Vector3f(v);
                skin[rig.searchJointByName(weight.first()).getId()].transformPosition(v);
                skin[rig.searchJointByName(weight.second()).getId()].transformPosition(other);
                v.lerp(other,weight.secondWeight());
                return new Vec3(-v.x,1.501F-v.y,v.z);
            }
            var model=getParentModel();
            ModelPart part=switch(region) {
                case LEFT_LEG -> model.leftLeg;
                case BODY -> model.body;
                case HEAD -> model.head;
                case RIGHT_ARM -> model.rightArm;
                case LEFT_ARM -> model.leftArm;
                case RIGHT_LEG -> model.rightLeg;
                default -> throw new IllegalArgumentException("Unexpected body region: " + region);
            };
            var bind=part.getInitialPose();
            // Transform to the current model space before drawing, including the crouch offset and limb pivot.
            var v=new Vector3f((float)(x-bind.x),(float)(y-bind.y),(float)(z-bind.z)).mul(1/16F);
            v.mul(part.xScale,part.yScale,part.zScale).rotateX(part.xRot).rotateY(part.yRot).rotateZ(part.zRot);
            return new Vec3(v.x+part.x/16,v.y+part.y/16,v.z+part.z/16);
        }

        void draw(VertexConsumer out, Flow flow, long seed, int index, boolean slim) {
            // A route changes only while invisible, even when several players share an animation tick.
            long stream = seed + 0x9e3779b97f4a7c15L * (1L + index);
            double clock = time / (30 + 17 * noise(stream)) + noise(~stream);
            long cycle = (long)Math.floor(clock);
            double phase = clock - cycle;
            double opacity = Math.min(1, Math.min(phase, 1-phase) * 8);
            if (opacity <= .015) return;
            long route = stream + cycle * 0x632be59bd9b4e019L;
            double start = 2*Math.PI * noise(route);
            double turn = (noise(route+1) < .5 ? -1 : 1) * (1.8 + 3.1*noise(route+2));
            double wave = 2*Math.PI * noise(route+3);
            boolean descending = noise(route+4) < .5;
            int region = flow.region();
            boolean arm = region == LEFT_ARM || region == RIGHT_ARM;
            boolean leg = region == LEFT_LEG || region == RIGHT_LEG;
            int side = region == LEFT_ARM || region == LEFT_LEG ? 1 : -1;
            double cx = arm ? side*(slim ? 5.5 : 6) : leg ? side*1.9 : 0;
            double rx = region == BODY || region == HEAD ? 4 : arm && slim ? 1.5 : 2;
            double rz = region == HEAD ? 4 : 2;
            double clearance = region == HEAD ? .61 : .36;
            double trailLength = .52;
            double head = phase * (1 + trailLength);
            double maxHalfWidth = region == BODY ? .62 : region == HEAD ? .50 : .56;
            Vec3 lastL=null, lastM=null, lastR=null;
            int oldInk=0, oldRim=0;
            for (int j=0; j<=20; j++) {
                double q=j/20.0, t=head-trailLength*(1-q);
                if(t<0 || t>1) continue;
                double angle=start+turn*t+.38*Math.sin(6*t+wave);
                double nx=Math.sin(angle), nz=-Math.cos(angle);
                // Ray intersection keeps the ribbon outside the outer skin at a cuboid corner.
                double edge=Math.max(Math.abs(nx),Math.abs(nz));
                double x=cx+(rx+clearance)*nx/edge, z=(rz+clearance)*nz/edge;
                double y=flow.low()+(flow.high()-flow.low())*(descending?t:1-t);
                double taper=Math.sin(Math.PI*q);
                double half=maxHalfWidth*(.325+.675*taper)*taper;
                // Offset on the tangent plane itself, including on the narrow sides of slim arms.
                double dx=-nz*half, dz=nx*half;
                Vec3 left=point(region,x-dx,y,z-dz), mid=point(region,x+dx*.40,y,z+dz*.40);
                Vec3 right=point(region,x+dx,y,z+dz);
                double strength=opacity*taper;
                int ink=((int)(216*strength)<<24)|0x0b0c14;
                int rim=((int)(122*strength)<<24)|0x706d83;
                if(lastM!=null) {
                    Vec3 normal=mid.subtract(lastM).cross(right.subtract(left)).normalize();
                    strip(out,lastL,lastM,left,mid,normal,oldInk,ink);
                    strip(out,lastM,lastR,mid,right,normal,oldRim,rim);
                }
                lastL=left; lastM=mid; lastR=right; oldInk=ink; oldRim=rim;
            }
        }

        void strip(VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 n,int first,int last) {
            ConsumedMobVisuals.vertex(stack,out,light,a,n,first,.5f,.5f);
            ConsumedMobVisuals.vertex(stack,out,light,b,n,first,.5f,.5f);
            ConsumedMobVisuals.vertex(stack,out,light,d,n,last,.5f,.5f);
            ConsumedMobVisuals.vertex(stack,out,light,c,n,last,.5f,.5f);
        }
    }

}
