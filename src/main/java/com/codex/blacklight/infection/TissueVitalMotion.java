package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** One bounded surface field per animated body region. Several lungs do not stack several torsos. */
public final class TissueVitalMotion {
    private TissueVitalMotion(){}
    public static float clock(long tick,float partial){return (float)(Math.IEEEremainder(tick*.11,Math.PI*2)/.11+partial);}
    public static final class Field {
        private final Bone bone;private final V centre,up,right,front;private final float h,w,d,breath,pulse,stored;
        public Field(Bone bone,TissueVitals.Profile p,CompoundTag state,float time){
            this.bone=bone;centre=bone.box().centre();up=bone.up().unit();right=bone.right().unit();front=bone.forward().unit();
            h=span(bone.box(),up);w=span(bone.box(),right);d=span(bone.box(),front);
            String route=state.getString("Route");boolean wet=route.equals("GILL")||route.equals("WATER"),air=route.equals("AIR");
            double ventilation=wet?p.gill():route.equals("VENT")?p.trachea():air?Math.max(p.lung(),Math.max(p.airflow()*(state.getBoolean("Moving")?1:.4),state.getBoolean("WetBody")?0:p.book())):0;
            float gas=(float)Math.sin(time*.11),effort=state.getBoolean("Working")?1.3F:1;
            breath=ventilation<=0?0:(float)(.018+.025*ventilation/(1+ventilation))*gas*effort;
            double pump=Math.max(p.heart(),wet?p.branchial():0);float oxygen=com.codex.blacklight.forge.WingMath.clamp(state.getInt("Oxygen")/100F,0,1);
            float contraction=(float)Math.pow(Math.max(0,Math.sin(time*.22)),5);
            pulse=pump<=0?0:(float)(.01+.012*pump/(1+pump))*contraction*(.25F+.75F*oxygen)*(state.getInt("Flash")>0?1.4F:1);
            stored=p.spleen()>0&&state.getInt("SpleenCapacity")>0?.025F*com.codex.blacklight.forge.WingMath.clamp(state.getInt("Spleen")/(float)state.getInt("SpleenCapacity"),0,1):0;
        }
        public V point(V v){
            if(bone.role()!=0&&bone.role()!=1||Math.min(h,Math.min(w,d))<.001)return v;
            var relative=v.sub(centre);float down=com.codex.blacklight.forge.WingMath.clamp(.5F-relative.dot(up)/h,0,1);
            float seam=smooth(down/.3F),band=(float)Math.sin(Math.PI*down)*seam;
            float expansion=bone.role()==1?breath*.45F*band:(breath+pulse+stored)*band;
            return v.add(right.mul(relative.dot(right)*expansion)).add(front.mul(relative.dot(front)*expansion*.6F));
        }
        public void apply(float[] vertices){for(int i=0;i<vertices.length;i+=3){var v=point(new V(vertices[i],vertices[i+1],vertices[i+2]));vertices[i]=v.x();vertices[i+1]=v.y();vertices[i+2]=v.z();}}
    }
    public static Field field(Bone bone,TissueVitals.Profile p,CompoundTag state,float time){return bone==null||!p.active()||bone.role()!=0&&bone.role()!=1?null:new Field(bone,p,state,time);}
    /** Movable vent lips and internal sacs share the surface clock; their first ring stays buried. */
    public static void module(float[] v,Bone bone,TissueCore core,CompoundTag state,float time){
        if(!core.vital()||bone==null||v.length<48)return;V root=new V(0,0,0);for(int i=0;i<24;i+=3)root=root.add(new V(v[i],v[i+1],v[i+2]));root=root.mul(.125F);
        float wave=(float)Math.sin(time*.11);boolean wet=state.getString("Route").equals("GILL")||state.getString("Route").equals("WATER");
        float contraction=(float)Math.pow(Math.max(0,Math.sin(time*.22)),5),fill=state.getInt("SpleenCapacity")>0?com.codex.blacklight.forge.WingMath.clamp(state.getInt("Spleen")/(float)state.getInt("SpleenCapacity"),0,1):0;
        for(int i=24;i<v.length;i+=3){float t=(i/24)/(float)(v.length/24-1);V p=new V(v[i],v[i+1],v[i+2]),delta=p.sub(root);
            boolean air=state.getString("Route").equals("AIR");
            float amount=switch(core){case GILL_LAMELLA->wet?.1F*wave:-.12F;case HEART->-.12F*contraction;case BRANCHIAL_HEART->wet?-.12F*contraction:0;case SPLEEN->-.35F+.35F*fill;case TRACHEA->air||state.getString("Route").equals("VENT")?.05F*wave:0;case AIRFLOW->air?(state.getBoolean("Moving")?.11F:.04F)*wave:0;case BOOK_LUNG->air&&!state.getBoolean("WetBody")?(state.getBoolean("Moving")?.02F:.08F)*wave:0;default->air?.08F*wave:0;};
            p=root.add(delta.mul(1+amount*t));v[i]=p.x();v[i+1]=p.y();v[i+2]=p.z();
        }
    }
    private static float span(Box box,V a){return Math.abs(a.x())*box.span(0)+Math.abs(a.y())*box.span(1)+Math.abs(a.z())*box.span(2);}
    private static float smooth(float v){v=com.codex.blacklight.forge.WingMath.clamp(v,0,1);return v*v*(3-2*v);}
}
