package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.TissueVascular;
import com.codex.blacklight.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;

/** A short living duct grows from the torso, pumps, and retracts after the server-owned transfer. */
final class TissueVascularFx {
    private TissueVascularFx(){}
    static void render(BlacklightInfectedHost host,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(!host.hasVascularVisual()||host.isInvisible()||!host.isAlive())return;var state=host.vascularVisual();
        var pos=BlockPos.of(state.getLong("Target"));if(!host.level().hasChunkAt(pos))return;var core=host.level().getBlockState(pos);boolean cancelled=state.contains("CancelledAt");if(!cancelled&&!core.is(ModBlocks.BUILDING_CORE.get()))return;
        float time=host.level().getGameTime()-state.getLong("Started")+partial;float extension=TissueVascular.visualExtension(state,(double)host.level().getGameTime()+partial);if(extension<.002F)return;
        var base=host.getPosition(partial);var from=TissueVascular.root(host,partial);var to=cancelled?new Vec3(state.getDouble("EndX"),state.getDouble("EndY"),state.getDouble("EndZ")):Vec3.atCenterOf(pos).relative(core.getValue(com.codex.blacklight.world.BuildingInfectionCoreBlock.FACING),.7);
        if(from.distanceToSqr(to)>25)return;var path=TissueVascular.path(from.subtract(base),to.subtract(base),time,extension);var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);double pulse=state.getBoolean("Committed")?1+.12*Math.sin(time*.65-i*.6):1;
            ConsumedMobVisuals.tube(stack,out,light,a,b,.055*pulse,0xff2b2028);
            var axis=b.subtract(a).normalize();var side=axis.cross(new Vec3(0,1,0)).normalize().scale(.047);ConsumedMobVisuals.tube(stack,out,light,a.add(side),b.add(side),.012,0xff74313b);
        }
    }
}
