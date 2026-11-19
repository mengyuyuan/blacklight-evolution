package com.codex.blacklight.animation;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A falx and its fixed armor share the same outside corner of the animated upper arm. */
public final class BackspineFit {
    private static final BlacklightSkeleton RIG=BlacklightSkeleton.get();
    private static final int[] ARMS={RIG.searchJointByName("Arm_R").getId(),RIG.searchJointByName("Arm_L").getId()};
    private static final Matrix4f[] INVERSE_ARMS=new Matrix4f[2];
    static {
        var bind=RIG.getPoseMatrices(new BlacklightPose());
        for(int side=0;side<2;side++)INVERSE_ARMS[side]=new Matrix4f(bind[ARMS[side]]).invert();
    }
    private record Shape(Vector3f[][] triangles,Vector3f center,Vector3f radius) {}
    private record Interval(float start,float end) {}
    public record Attachment(Matrix4f socket,Matrix4f blade) {}
    private static final Shape[] SOCKET=new Shape[2],BLADE=new Shape[2];
    static {
        try(var stream=BackspineFit.class.getResourceAsStream("/assets/blacklight/models/backspines.json");
            var reader=new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)){
            var mesh=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("joints");
            for(int side=0;side<2;side++)for(String part:new String[]{"Socket","Falx"}){
                var triangles=new ArrayList<Vector3f[]>();
                var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
                for(var item:mesh.getAsJsonArray(part+side)){
                    var points=item.getAsJsonObject().getAsJsonArray("p");var tri=new Vector3f[3];
                    for(int i=0;i<3;i++){
                        var v=points.get(i).getAsJsonArray();
                        tri[i]=new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat());
                        min.min(tri[i]);max.max(tri[i]);
                    }
                    triangles.add(tri);
                }
                (part.equals("Socket")?SOCKET:BLADE)[side]=new Shape(triangles.toArray(Vector3f[][]::new),
                        new Vector3f(max).add(min).mul(.5F),max.sub(min).mul(.5F));
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Could not load falx mesh",e);}
    }
    private static final Matrix4f HEAD_BIND=RIG.getPoseMatrices(new BlacklightPose())[RIG.head.getId()];
    private final Matrix4f[] headFromArm=new Matrix4f[2];
    private final Matrix4f[] shoulders=new Matrix4f[2];
    public BackspineFit(Matrix4f[] posed){
        var inverseChest=new Matrix4f(posed[RIG.chest.getId()]).invert();
        var toHead=new Matrix4f(HEAD_BIND).mul(new Matrix4f(posed[RIG.head.getId()]).invert());
        for(int side=0;side<2;side++){
            var skin=new Matrix4f(posed[ARMS[side]]).mul(INVERSE_ARMS[side]);
            shoulders[side]=new Matrix4f(inverseChest).mul(skin);
            headFromArm[side]=new Matrix4f(toHead).mul(skin);
        }
    }

    // Moving both pieces down the outside face of the upper arm preserves their physical hinge.
    public Attachment attachment(MatrixMotionClip clip,float time,int side){
        var hinge=clip.sample("Rack"+side,time,false);
        var pivot=hinge.getTranslation(new Vector3f());
        var shell=new Matrix4f().translation(pivot).scale(hinge.getScale(new Vector3f()).x);
        var toHead=headFromArm[side];
        var slide=toHead.transformDirection(new Vector3f(0,-1,0));
        var capInHead=new Matrix4f(toHead).mul(shell);
        var bladeInHead=new Matrix4f(toHead).mul(hinge).translate(0,0,.027F);
        if(nearHead(SOCKET[side],capInHead)||nearHead(BLADE[side],bladeInHead)){
            var blocked=new ArrayList<Interval>();
            var sweep=new Sweep(slide,blocked);
            sweep.collect(SOCKET[side],capInHead);
            sweep.collect(BLADE[side],bladeInHead);
            blocked.sort(Comparator.comparingDouble(Interval::start));
            float offset=0;
            for(var range:blocked){
                if(range.start>offset)break;
                offset=Math.max(offset,range.end+.0005F);
            }
            pivot.y-=offset;
        }
        return new Attachment(new Matrix4f(shoulders[side]).mul(shell.setTranslation(pivot)),
                new Matrix4f(shoulders[side]).mul(hinge.setTranslation(pivot)));
    }

    private static boolean nearHead(Shape shape,Matrix4f m){
        var c=m.transformPosition(shape.center,new Vector3f());var r=shape.radius;
        return Math.abs(c.x)<=.285F+Math.abs(m.m00())*r.x+Math.abs(m.m10())*r.y+Math.abs(m.m20())*r.z
            && Math.abs(c.y-1.751F)<=.285F+Math.abs(m.m01())*r.x+Math.abs(m.m11())*r.y+Math.abs(m.m21())*r.z
            && Math.abs(c.z)<=.285F+Math.abs(m.m02())*r.x+Math.abs(m.m12())*r.y+Math.abs(m.m22())*r.z;
    }

    /** Swept triangle versus the hat box. Concave gaps in the crescent stay empty. */
    private static final class Sweep {
        private final Vector3f direction,a=new Vector3f(),b=new Vector3f(),c=new Vector3f();
        private final Vector3f ab=new Vector3f(),bc=new Vector3f(),ca=new Vector3f(),normal=new Vector3f();
        private final ArrayList<Interval> blocked;
        private float start,end;
        Sweep(Vector3f direction,ArrayList<Interval> blocked){this.direction=direction;this.blocked=blocked;}
        void collect(Shape shape,Matrix4f m){
            for(var face:shape.triangles){
                m.transformPosition(face[0],a).y-=1.751F;
                m.transformPosition(face[1],b).y-=1.751F;
                m.transformPosition(face[2],c).y-=1.751F;
                start=Float.NEGATIVE_INFINITY;end=Float.POSITIVE_INFINITY;
                if(!axis(1,0,0)||!axis(0,1,0)||!axis(0,0,1))continue;
                b.sub(a,ab);c.sub(b,bc);a.sub(c,ca);ab.cross(bc,normal);
                if(axis(normal.x,normal.y,normal.z)&&edge(ab)&&edge(bc)&&edge(ca))blocked.add(new Interval(start,end));
            }
        }
        private boolean edge(Vector3f e){
            return axis(0,e.z,-e.y)&&axis(-e.z,0,e.x)&&axis(e.y,-e.x,0);
        }
        private boolean axis(float x,float y,float z){
            float extent=.285F*(Math.abs(x)+Math.abs(y)+Math.abs(z));
            float p=a.dot(x,y,z),q=b.dot(x,y,z),r=c.dot(x,y,z);
            float low=Math.min(p,Math.min(q,r)),high=Math.max(p,Math.max(q,r));
            float speed=direction.dot(x,y,z);
            if(Math.abs(speed)<1e-6F*extent||extent<1e-10F)return low<=extent&&high>=-extent;
            float first=(-extent-high)/speed,last=(extent-low)/speed;
            start=Math.max(start,Math.min(first,last));end=Math.min(end,Math.max(first,last));
            return end>=0&&end>=start;
        }
    }
}
