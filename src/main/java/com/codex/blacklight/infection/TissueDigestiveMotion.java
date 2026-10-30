package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** Internal organs contract around their buried attachment; full crop/lipid tissue has real volume. */
public final class TissueDigestiveMotion {
    private TissueDigestiveMotion(){}
    /** Reduce the common .17-radian cycle before converting to float, preserving sub-tick motion in old worlds. */
    public static float clock(long tick,float partial){return (float)(Math.IEEEremainder(tick*.17,Math.PI*2)/.17+partial);}
    public static boolean active(TissueCore core,CompoundTag state){return switch(core){
        case GIZZARD->state.getBoolean("Working")&&(state.getString("WorkingKind").equals("fibre")||state.getString("WorkingKind").equals("ferment"));
        case FERMENT->state.getBoolean("Working")&&state.getString("WorkingKind").equals("ferment");
        case GILL_RAKER->state.getBoolean("Working")&&state.getString("WorkingKind").equals("filter");
        case PANCREAS->state.getBoolean("EnzymeWorking");case FAT->state.getBoolean("FatWorking");default->state.getBoolean("Working");
    };}
    public static float load(TissueCore core,CompoundTag state){
        int stored=state.getInt(core==TissueCore.FAT?"Fat":"Queued"),capacity=state.getInt(core==TissueCore.FAT?"FatCapacity":"Capacity");
        return capacity<=0?0:com.codex.blacklight.forge.WingMath.clamp(stored/(float)capacity,0,1);
    }
    /** Shared displacement of skin, supporting ribs and buried organ roots. Upper seams stay put. */
    public static void surface(float[] vertices,Bone bone,TissueDigestion.Profile profile,CompoundTag state,float time){
        if(bone==null||!profile.digestive()||bone.role()!=0&&bone.role()!=1)return;
        V c=bone.box().centre(),up=bone.up().unit(),right=bone.right().unit(),front=bone.forward().unit();
        float h=span(bone.box(),up),w=span(bone.box(),right),d=span(bone.box(),front);if(Math.min(h,Math.min(w,d))<.001F)return;
        float fill=load(TissueCore.CROP,state),fat=load(TissueCore.FAT,state);
        float activity=(state.getBoolean("Working")?1:0)*(float)Math.sin(time*.17);
        float digestive=(float)Math.min(.055,.012*profile.gizzard()+.018*profile.ferment()+.006*profile.absorption()+.006*profile.pancreas());
        float crop=(float)Math.min(1,profile.crop()/2),lipid=(float)Math.min(1,profile.fat());
        for(int i=0;i<vertices.length;i+=3){
            V p=new V(vertices[i],vertices[i+1],vertices[i+2]),relative=p.sub(c);
            float down=com.codex.blacklight.forge.WingMath.clamp(.5F-relative.dot(up)/h,0,1),weight;
            float width,ventral;
            if(bone.role()==1){
                float rear=com.codex.blacklight.forge.WingMath.clamp(.5F-relative.dot(front)/d,0,1);weight=smooth((down-.55F)/.45F)*(.35F+.65F*rear);
                width=crop*(.09F*fill+.008F*activity);ventral=crop*.035F*fill;
            }else{
                weight=smooth((down-.25F)/.75F);width=digestive*(.45F+.55F*fill)+.12F*lipid*fat+.009F*activity;
                ventral=digestive*.5F+.075F*lipid*fat+.006F*activity;
            }
            p=p.add(right.mul(relative.dot(right)*width*weight)).sub(up.mul(h*ventral*weight));
            vertices[i]=p.x();vertices[i+1]=p.y();vertices[i+2]=p.z();
        }
    }
    private static float span(Box box,V axis){return Math.abs(axis.x())*box.span(0)+Math.abs(axis.y())*box.span(1)+Math.abs(axis.z())*box.span(2);}
    private static float smooth(float value){value=com.codex.blacklight.forge.WingMath.clamp(value,0,1);return value*value*(3-2*value);}
    public static void apply(float[] vertices,Bone bone,TissueCore core,float time,boolean active,float load){
        if(bone==null||!core.digestive()||vertices.length<48)return;
        V root=new V(0,0,0);for(int i=0;i<24;i+=3)root=root.add(new V(vertices[i],vertices[i+1],vertices[i+2]));root=root.mul(.125F);
        float fill=Float.isFinite(load)?com.codex.blacklight.forge.WingMath.clamp(load,0,1):0;float pulse=active?(float)Math.sin(time*.17):0;
        for(int i=24;i<vertices.length;i+=3){
            float t=(i/24)/(float)(vertices.length/24-1);
            V p=new V(vertices[i],vertices[i+1],vertices[i+2]),d=p.sub(root);
            float scale=switch(core){case FAT->.18F+.82F*fill;case CROP->.25F+.75F*fill;default->.88F+.12F*fill;};
            float wave=active?(float)Math.sin(time*.17-t*3)*.055F:0;
            p=root.add(d.mul(1+(scale-1)*t)).add(bone.up().mul(pulse*.07F*t)).add(bone.right().mul(d.dot(bone.right())*wave*t));
            vertices[i]=p.x();vertices[i+1]=p.y();vertices[i+2]=p.z();
        }
    }
}
