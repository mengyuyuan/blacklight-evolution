package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Five six-sided cross sections per shaft, taken in vertex order from the custom rig. */
public final class SpiderLegSkin {
    private static final Map<String,SpiderLegSkin> SHAFTS=new HashMap<>();
    static {
        try(var in=SpiderLegSkin.class.getResourceAsStream("/assets/blacklight/models/greene_leg_surfaces.json");
            var reader=new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)){
            for(var e:JsonParser.parseReader(reader).getAsJsonObject().entrySet()){
                var points=new Vec3[5][6];var rings=e.getValue().getAsJsonArray();
                for(int i=0;i<5;i++)for(int j=0;j<6;j++){
                    var a=rings.get(i).getAsJsonArray().get(j).getAsJsonArray();
                    points[i][j]=new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
                }
                SHAFTS.put(e.getKey(),new SpiderLegSkin(points,e.getKey().startsWith("tibia")));
            }
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    private record Span(Vec3 center,Vec3 cross,Vec3 up,Vec3 along,Vec3 half){}
    public record Site(String joint,double along,double around,double error){}
    private final Vec3[][] rings;
    private final Vec3[] centers=new Vec3[5];
    private final double[] lengths=new double[5];
    private final List<Span> spans=new ArrayList<>(6);
    public static SpiderLegSkin of(String joint){return SHAFTS.get(joint);}
    private SpiderLegSkin(Vec3[][] points,boolean shin){
        rings=points;
        for(int i=0;i<5;i++){
            var center=Vec3.ZERO;for(var p:points[i])center=center.add(p);
            centers[i]=center.scale(1./6);
            if(i>0)lengths[i]=lengths[i-1]+centers[i-1].distanceTo(centers[i]);
        }
        for(int i=0;i<4;i++){
            var along=centers[i+1].subtract(centers[i]).normalize();
            var up=along.cross(points[i][0].subtract(centers[i])).normalize();
            var cross=up.cross(along).normalize();
            int divisions=(int)Math.ceil((lengths[i+1]-lengths[i])/2.75);
            for(int n=0;n<divisions;n++){
                double lo=(double)n/divisions,hi=(double)(n+1)/divisions;
                var center=centers[i].lerp(centers[i+1],(lo+hi)*.5);
                // The needle tapers to a few pixels. Keep its ground-level strike surface about
                // three quarters of a block wide, without filling space between different legs.
                double x=shin && i>=2?.30:.10,y=x,z=0;
                for(int k=0;k<6;k++)for(double u:new double[]{lo,hi}){
                    var d=points[i][k].lerp(points[i+1][k],u).subtract(center);
                    x=Math.max(x,Math.abs(d.dot(cross)));y=Math.max(y,Math.abs(d.dot(up)));z=Math.max(z,Math.abs(d.dot(along)));
                }
                spans.add(new Span(center,cross,up,along,new Vec3(x+.075,y+.075,z+.07)));
            }
        }
    }
    public static String joint(int leg,boolean upper){String shin=SpiderAnatomy.shin(leg);return upper?"coxa"+shin.substring(5):shin;}
    static void add(EncounterMob b,int leg,float partial,Matrix4f root,Vec3 origin,Map<String,Matrix4f> matrices,List<HydraContact.Box> boxes){
        for(boolean upper:new boolean[]{false,true}){
            if(!upper && b.spider.severed(leg))continue;
            String name=joint(leg,upper);
            var m=matrices.computeIfAbsent(name,k->b.spider.jointMatrix(root,k,partial));
            for(var span:of(name).spans){
                var center=origin.add(new Vec3(m.transformPosition(span.center.toVector3f())));
                boxes.add(HydraContact.affine(center,span.half,axis(m,span.cross),axis(m,span.up),axis(m,span.along)));
            }
        }
    }
    private static Vec3 axis(Matrix4f m,Vec3 v){return new Vec3(m.transformDirection(v.toVector3f()));}
    private int section(double distance){
        for(int i=0;i<3;i++)if(distance<=lengths[i+1])return i;
        return 3;
    }
    /** Distances along the shaft are model units; across distances are hexagonal face coordinates. */
    public Vec3 surface(double distance,double around,double offset){
        int i=section(distance),side=Math.floorMod((int)Math.floor(around),6),next=(side+1)%6;
        double u=Mth.clamp((distance-lengths[i])/(lengths[i+1]-lengths[i]),0,1);
        double v=around-Math.floor(around);
        var a=rings[i][side].lerp(rings[i][next],v);var b=rings[i+1][side].lerp(rings[i+1][next],v);
        var p=a.lerp(b,u);return p.add(p.subtract(centers[i].lerp(centers[i+1],u)).normalize().scale(offset));
    }
    static Site closest(EncounterMob b,int leg,Vec3 hit){
        var root=SpiderAnatomy.root(b,0);var relative=hit.subtract(b.position());Site best=null;
        for(boolean upper:new boolean[]{false,true}){
            var name=joint(leg,upper);var skin=of(name);
            var local=new Vec3(b.spider.jointMatrix(root,name,0).invert().transformPosition(relative.toVector3f()));
            for(int i=0;i<4;i++){
                var a=skin.centers[i];var line=skin.centers[i+1].subtract(a);
                double u=Mth.clamp(local.subtract(a).dot(line)/line.lengthSqr(),0,1);
                double at=Mth.lerp(u,skin.lengths[i],skin.lengths[i+1]);
                for(int j=0;j<6;j++){
                    var corner=skin.surface(at,j,0);var edge=skin.surface(at,j+1,0).subtract(corner);
                    double side=Mth.clamp(local.subtract(corner).dot(edge)/Math.max(edge.lengthSqr(),1e-9),0,1);
                    double error=corner.add(edge.scale(side)).distanceToSqr(local);
                    if(best==null || error<best.error)best=new Site(name,at,j+side,error);
                }
            }
        }
        return best;
    }
}
