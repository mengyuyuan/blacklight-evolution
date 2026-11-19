package com.codex.blacklight.animation;

import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** The action key carries the leading shoulder to observers, including late joiners. */
public final class ScapulaCycle {
    private ScapulaCycle() {}

    public static String actionKey(String key,boolean right) {
        if(!right||!attack(key))return key;
        int mark=key.indexOf('_',7)+1;
        return key.substring(0,mark)+"r_"+key.substring(mark);
    }
    private static boolean attack(String key){
        return key.startsWith("spines_light_")||key.startsWith("spines_air_")||key.startsWith("spines_ground_");
    }
    public static boolean rightLead(String key) {
        return key.startsWith("spines_light_r_")||key.startsWith("spines_air_r_")||key.startsWith("spines_ground_r_");
    }
    public static String sourceKey(String key) {
        if(!rightLead(key))return key;
        int mark=key.indexOf('_',7)+1;
        return key.substring(0,mark)+key.substring(mark+2);
    }
    public static boolean rightForLane(boolean leadRight,int lane){
        return lane==0?leadRight:lane<0;
    }
    public static int shotTick(String key,boolean right){
        boolean lead=rightLead(key)==right;
        if(key.startsWith("spines_light_")){
            if(key.endsWith("_burst"))return lead?4:10;
            return lead?(key.endsWith("_lift")?4:5):-1;
        }
        if(!attack(key))return -1;
        if(key.startsWith("spines_air_")&&(key.endsWith("_throw")||key.endsWith("_charged")))return 1;
        if(key.startsWith("spines_ground_")&&key.endsWith("_throw")&&!lead)return 10;
        return key.endsWith("_throw")?4:key.endsWith("_charged")?5:-1;
    }
    public static Matrix4f seat(Matrix4f hinge,int lane,float deployment){
        float angle=switch(Math.abs(lane)){case 1 -> -1F;case 2 -> 1.25F;default -> 0F;};
        return ScapulaVolley.socket(hinge,angle*deployment,1+(ScapulaVolley.size(lane)-1)*deployment);
    }

    /** Reflect local bone deltas in the target bone's bind axes, including the pelvis. */
    private record Pair(String other,Quaternionf axes,Quaternionf inverse){}
    private static final class Bind {
        static final Map<String,Pair> PAIRS=pairs();
        private static Map<String,Pair> pairs(){
            var rig=BlacklightSkeleton.get();
            var bind=rig.getPoseMatrices(new BlacklightPose());
            var result=new HashMap<String,Pair>();
            for(var bone:rig.bones()){
                String name=bone.getName(),other=name;
                if(name.endsWith("_L")||name.endsWith("_R"))
                    other=name.substring(0,name.length()-1)+(name.endsWith("_L")?"R":"L");
                var to=rig.searchJointByName(other);
                if(to==null)continue;
                // -reflectX is a proper rotation and conjugates rotations just like reflectX.
                var axes=bind[to.getId()].getUnnormalizedRotation(new Quaternionf()).normalize().invert()
                        .mul(new Quaternionf(1,0,0,0)).mul(bind[bone.getId()].getUnnormalizedRotation(new Quaternionf()).normalize());
                result.put(name,new Pair(other,axes,new Quaternionf(axes).invert()));
            }
            return Map.copyOf(result);
        }
    }
    public static BlacklightPose pose(String key,float time,BlacklightPose source){
        float weight=rightLead(key)?1:0;
        if(key.endsWith("_burst")){
            float transfer=ScapulaVolley.ease((time*20-4.75F)/4);
            weight=rightLead(key)?1-transfer:transfer;
        }
        if(weight==0)return source;
        var reflected=new BlacklightPose();
        source.getJointTransformData().forEach((name,delta)->{
            Pair pair=Bind.PAIRS.get(name);
            if(pair==null)return;
            var mirror=delta.copy();
            pair.axes().transform(mirror.translation()).negate();
            mirror.rotation().set(pair.axes()).mul(delta.rotation()).mul(pair.inverse()).normalize();
            reflected.getJointTransformData().put(pair.other(),mirror);
        });
        return weight>=1?reflected:BlacklightPose.interpolatePose(source,reflected,weight);
    }

    public record Chamber(float open,float growth,float view,int primary,float primarySpread,
                          boolean secondary,ScapulaVolley.Strain tissue){
        public Chamber limitGrowth(float limit){
            return growth<=limit?this:new Chamber(open,limit,view,primary,primarySpread,secondary,tissue);
        }
    }
    public static Chamber chamber(String key,float t,double clock,boolean right,float open,float growth){
        boolean lead=rightLead(key)==right,air=key.startsWith("spines_air_");
        int shot=shotTick(key,right);
        if(attack(key)&&(shot<0||t<shot))growth=1;
        int primary=0;float spread=0,view=1;boolean secondary=false;
        var tissue=ScapulaVolley.strain(key,t,clock);
        if(air){
            secondary=true;
            if(!lead){primary=right?-1:1;spread=tissue.inner();}
        }else if(key.startsWith("spines_ground_")){
            if(key.endsWith("_draw")&&!lead){
                open=.12F+.88F*ScapulaVolley.ease((t-2)/8);
            }else if(key.endsWith("_throw")){
                float since=t-shot;
                // The second quill stays seated throughout the first discharge.
                open=since<0?(lead?1:.72F+.28F*ScapulaVolley.ease((t-4)/5)):
                        1-.88F*ScapulaVolley.ease(since/4);
                growth=since<0?1:ScapulaVolley.ease((since-1.35F)/4.65F);
                tissue=ScapulaVolley.strain(key,t-(lead?0:6),clock);
                view=lead?1-.64F*ScapulaVolley.ease((t-5)/3):.36F+.64F*ScapulaVolley.ease((t-4)/4);
            }
        }else if(key.endsWith("_burst")){
            if(lead){
                if(t>=4){open=1-.88F*ScapulaVolley.ease((t-4)/4);growth=ScapulaVolley.ease((t-5)/4);}
                if(t>=7)tissue=ScapulaVolley.strain("",0,clock);
                view=1-ScapulaVolley.ease((t-4)/5);
            }else{
                if(t<10){open=.12F+.88F*ScapulaVolley.ease((t-5)/4);growth=1;}
                if(t<7)tissue=ScapulaVolley.strain("",0,clock);
                view=ScapulaVolley.ease((t-5)/4);
            }
        }else if(attack(key)&&!lead){
            open=.12F;growth=1;view=0;tissue=ScapulaVolley.strain("",0,clock);
        }
        return new Chamber(open,growth,view,primary,spread,secondary,tissue);
    }
}
