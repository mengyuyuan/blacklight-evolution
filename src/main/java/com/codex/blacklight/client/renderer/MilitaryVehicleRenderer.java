package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.MilitaryVehicleModel;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Native authored ModelParts with the same positive-Y coordinate system as server mounts. */
public final class MilitaryVehicleRenderer extends EntityRenderer<MilitaryVehicle> {
    private final MilitaryVehicleModel model;
    private final com.codex.blacklight.client.vehicle.VehicleViewRendering interior;
    private final WeakHashMap<MilitaryVehicle,Travel> movement=new WeakHashMap<>();
    private record Travel(Vec3 position,double distance){}
    public MilitaryVehicleRenderer(EntityRendererProvider.Context context,String chassis) {
        super(context);model=new MilitaryVehicleModel(chassis);interior=new com.codex.blacklight.client.vehicle.VehicleViewRendering(model,chassis);shadowRadius=1.5F;
    }
    @Override public ResourceLocation getTextureLocation(MilitaryVehicle entity) {
        return ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/military_vehicles_"+entity.faction().id()+".png");
    }
    @Override public void render(MilitaryVehicle entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        Vec3 at=entity.getPosition(partial);var previous=movement.get(entity);double distance=previous==null?0:previous.distance;
        if(previous!=null) {
            double delta=at.subtract(previous.position).horizontalDistance();
            if(delta<2)distance+=delta;
        }
        distance%=Math.PI*100;movement.put(entity,new Travel(at,distance));
        model.pose(entity.turretYaw(),entity.gunPitch(),distance);
        stack.pushPose();
        try {
            stack.mulPose(Axis.YP.rotationDegrees(yaw));
            var mc=net.minecraft.client.Minecraft.getInstance();
            boolean local=mc.player!=null&&mc.player.getVehicle()==entity&&mc.getCameraEntity()==mc.player&&mc.options.getCameraType().isFirstPerson();
            String seat=local?entity.seatOf(mc.player.getUUID()):null;
            var view=seat==null?null:com.codex.blacklight.client.vehicle.VehicleCamera.view(entity.definition(),seat,Vec3.ZERO,0,entity.turretYaw(),entity.gunPitch(),
                    mc.player.getViewYRot(partial)+yaw,mc.player.getViewXRot(partial),true,false);
            interior.render(stack,buffers.getBuffer(RenderType.entityCutout(getTextureLocation(entity))),light,OverlayTexture.NO_OVERLAY,local,view);
        } finally {stack.popPose();}
        super.render(entity,yaw,partial,stack,buffers,light);
    }
}
