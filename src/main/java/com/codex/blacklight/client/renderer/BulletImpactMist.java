package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;

/** Original bounded smoke shader and world-space plumes. Driven only by the
 * replicated impact origin/session; never follows the actor into the crater. */
@EventBusSubscriber(modid="blacklight",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BulletImpactMist extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType MIST=create("blacklight_impact_mist",DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,8192,false,true,CompositeState.builder()
                    .setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private BulletImpactMist(){super("blacklight_mist_factory",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,8192,false,true,()->{},()->{});}
    @SubscribeEvent public static void register(RegisterShadersEvent event)throws IOException {
        shader=null;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("blacklight","impact_mist"),
                DefaultVertexFormat.POSITION_TEX_COLOR),loaded->shader=loaded);
    }
    public static boolean ready(){return shader!=null;}
    private static ResourceLocation puff;
    /** Soft round puff for the shader-pack path; the custom mist program is unknown to Iris/Oculus. */
    private static ResourceLocation puff(){
        if(puff!=null)return puff;
        var image=new com.mojang.blaze3d.platform.NativeImage(32,32,false);
        for(int y=0;y<32;y++)for(int x=0;x<32;x++){
            double dx=(x+.5)/16-1,dy=(y+.5)/16-1,r=Math.sqrt(dx*dx+dy*dy);
            double billow=.5+.5*Math.sin(x*1.7+y*.9)*Math.cos(y*1.3-x*.4);
            int a=(int)(255*Math.clamp((1-r)*(1-r)*(.75+.25*billow)*1.6,0,1));
            image.setPixelRGBA(x,y,(a<<24)|0xFFFFFF);
        }
        puff=ResourceLocation.fromNamespaceAndPath("blacklight","dynamic/impact_puff");
        Minecraft.getInstance().getTextureManager().register(puff,new net.minecraft.client.renderer.texture.DynamicTexture(image));
        return puff;
    }
    public static int draw(PoseStack stack,DevastatorEffectRenderer.Frame frame,int budget) {
        boolean packed=com.codex.blacklight.client.ShaderPackCompat.active();
        if((shader==null&&!packed)||budget<4)return 0;
        RenderType material=packed?RenderType.entityTranslucentEmissive(puff()):MIST;
        double age=frame.elapsedSeconds()-frame.effectStartSeconds();
        var mc=Minecraft.getInstance();var camera=mc.gameRenderer.getMainCamera();
        var rotation=camera.rotation();
        var rx=new org.joml.Vector3f(1,0,0).rotate(rotation);
        var uy=new org.joml.Vector3f(0,1,0).rotate(rotation);
        var right=new Vec3(rx.x,rx.y,rx.z);var up=new Vec3(uy.x,uy.y,uy.z);
        // Visual coverage is bounded; actual terrain depth/energy remain uncapped.
        double radius=Math.min(32,Math.max(4,frame.radius()));
        var buffers=com.codex.blacklight.client.ShaderPackCompat.buffers();var out=buffers.getBuffer(material);int count=0;
        var profile=com.codex.blacklight.combat.DevastatorEffectTimeline.impact(frame.fallHeight());
        // Short, upright red impact clusters at every height. Only a fall over
        // 50 blocks adds the broad, lingering ground mist behind those clusters.
        for(int i=0;i<(profile.mist()?120:24)&&count+4<=budget;i++) {
            boolean cluster=i<24;int particle=cluster?i:i-24,band=particle/24;
            double seed=noise(i,frame.session()),delay=cluster?seed*.025:band==3?seed*.14:band*.066667+seed*.035;
            double alpha=cluster?com.codex.blacklight.combat.DevastatorEffectTimeline.impactClusterOpacity(age-delay)
                    :profile.mistGain()*com.codex.blacklight.combat.DevastatorEffectTimeline.impactMistOpacity(age,delay);
            if(alpha<=.002)continue;
            double t=Math.max(0,age-delay),spread=1-Math.exp(-t*(cluster?16:3.8));
            double angle=i*2.3999632297+(frame.session()&255)*.019;
            double distance=cluster?profile.clusterRadius()*(.25+seed*.75)*spread
                    :band==3?radius*.13*spread:radius*(.38+band*.26)*spread;
            double y=cluster?.12+profile.clusterHeight()*(.25+seed*.75)*Math.sin(Math.min(1,t/.8)*Math.PI)
                    :band==3?.2+(1.6+radius*.14)*(1-Math.exp(-t*2.1)):.18+t*(.38+seed*.3);
            var center=frame.origin().add(Math.cos(angle)*distance,y,Math.sin(angle)*distance);
            double size=cluster?(.12+profile.clusterRadius()*.09)*(1+t)
                    :band==3?.65+radius*.055+t*.65:.55+radius*.075+t*.45;
            double tilt=(seed-.5)*.7;
            var tiltedRight=right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
            var tiltedUp=up.scale(Math.cos(tilt)).subtract(right.scale(Math.sin(tilt)));
            var horizontal=tiltedRight.scale(size*(cluster?.8:band==3?1:1.55));var vertical=tiltedUp.scale(size*(cluster?2.6:band==3?1.3:.65));
            int red=(int)(cluster?165+seed*45:100+seed*45),green=(int)(8+seed*11),blue=(int)(15+seed*13);
            int a=(int)(alpha*(cluster?185:band==3?44:66));
            vertex(stack,out,packed,center.subtract(horizontal).subtract(vertical),0,0,red,green,blue,a);
            vertex(stack,out,packed,center.add(horizontal).subtract(vertical),1,0,red,green,blue,a);
            vertex(stack,out,packed,center.add(horizontal).add(vertical),1,1,red,green,blue,a);
            vertex(stack,out,packed,center.subtract(horizontal).add(vertical),0,1,red,green,blue,a);
            count+=4;
        }
        buffers.endBatch(material);return count;
    }
    private static double noise(int id,long session){return .5+.5*Math.sin(id*12.9898+(session&1023)*.071);}
    private static void vertex(PoseStack stack,VertexConsumer out,boolean packed,Vec3 p,float u,float v,int r,int g,int b,int a){
        var vertex=out.addVertex(stack.last(),(float)p.x,(float)p.y,(float)p.z).setColor(r,g,b,a).setUv(u,v);
        if(packed)vertex.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(stack.last(),0,1,0);
    }
}

