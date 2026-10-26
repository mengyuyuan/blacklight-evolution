package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class TissueTongueRender {
    private TissueTongueRender(){}
    static void aim(BlacklightInfectedHost host,float partial,float[] vertices,Matrix4f modelToView,Matrix4f worldRelativeToView){
        if(worldRelativeToView==null||host.organActionCore()!=TissueCore.TONGUE||Math.abs(modelToView.determinant())<1e-8F)return;
        var raw=host.level().getEntity(host.organActionTarget());
        if(!(raw instanceof LivingEntity target)||!target.isAlive()||target.isRemoved()||!host.hasLineOfSight(target)
                ||host.distanceTo(target)>host.getBbWidth()+TissueOrganCycle.reach(TissueCore.TONGUE)+2)return;
        var relative=target.getPosition(partial).add(0,target.getBbHeight()*.42,0).subtract(host.getPosition(partial));
        var p=new Matrix4f(modelToView).invert().mul(worldRelativeToView)
                .transformPosition(new Vector3f((float)relative.x,(float)relative.y,(float)relative.z));
        TissueTongueShape.aim(vertices,new TissueMorphogenesis.V(p.x,p.y,p.z),TissueTongueShape.extension(host.organActionElapsed(partial)));
    }
}
