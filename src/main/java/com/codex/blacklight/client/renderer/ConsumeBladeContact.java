package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.WeaponCutContact;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.*;

/** Reads the positions actually emitted by the visible weapon, after wrist IK,
 * growth and morphing. It supplies cosmetic cuts, never client-authoritative damage. */
public final class ConsumeBladeContact implements AutoCloseable {
    public record Sample(int actor,double time,PowerForm form,String edge,List<Vec3> points){}
    /** Optional render audit; it observes geometry and cannot authorize damage. */
    public static java.util.function.Consumer<Sample> observer;
    private static final ThreadLocal<ConsumeBladeContact> CURRENT=new ThreadLocal<>();
    private static final Map<Object,Map<String,Binding>> MAPPINGS=new WeakHashMap<>();
    private record Triangle(Vec3[] source,Vec3[] world){Triangle(){this(new Vec3[3],new Vec3[3]);}}
    private record Probe(int face,double a,double b,double c){}
    private record Binding(Map<String,List<Probe>> edges,Set<Integer> faces){
        Binding(Map<String,List<Probe>> edges){this(edges,new HashSet<>());edges.values().forEach(probes->probes.forEach(p->faces.add(p.face)));}
    }
    private final AbstractClientPlayer actor;private final PowerForm form;private final Object model;private final String joint;
    private final Matrix4f matrix;private final Vec3 camera;private final float partial;
    private final Map<Integer,Triangle> triangles=new LinkedHashMap<>();
    private final ConsumeBladeContact previous;
    private final Binding binding;
    private ConsumeBladeContact(AbstractClientPlayer actor,PowerForm form,Object model,String joint,PoseStack stack,float partial){
        this.actor=actor;this.form=form;this.model=model;this.joint=joint;this.partial=partial;
        matrix=new Matrix4f(stack.last().pose());camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        binding=MAPPINGS.getOrDefault(model,Map.of()).get(form+":"+joint);
        previous=CURRENT.get();CURRENT.set(this);
    }
    public static ConsumeBladeContact begin(AbstractClientPlayer actor,PowerForm form,Object model,String joint,PoseStack stack,float partial){
        if(actor.getData(ModAttachments.GRAB_STAGE)!=ConsumeVisual.CONSUME&&observer==null||StandalonePlayerRendering.nativeHandPass()
                ||VisionPowerRenderer.drawingSilhouettes()||actor.getData(ModAttachments.PLAYER_STATE).getActivePower()!=form
                ||form!=PowerForm.CLAWS&&form!=PowerForm.BLADE&&form!=PowerForm.WHIPFIST)return null;
        return new ConsumeBladeContact(actor,form,model,joint,stack,partial);
    }
    public static void vertex(int face,int corner,Vec3 source,Vec3 drawn){
        var capture=CURRENT.get();if(capture==null)return;
        var triangle=capture.triangles.computeIfAbsent(face,k->new Triangle());triangle.source[corner]=source;
        var point=capture.matrix.transformPosition(drawn.toVector3f());triangle.world[corner]=capture.camera.add(point.x,point.y,point.z);
    }
    public static boolean accepts(int face){var capture=CURRENT.get();return capture!=null&&(capture.binding==null||capture.binding.faces.contains(face));}
    private Map<String,List<Probe>> mapping(){
        var result=new LinkedHashMap<String,List<Probe>>();
        for(var edge:WeaponCutContact.sharpProfiles(form,joint).entrySet()){
            var probes=new ArrayList<Probe>();
            for(var point:edge.getValue()){
                Probe best=null;double gap=.04*.04;
                for(var entry:triangles.entrySet()){
                    var p=entry.getValue().source;if(Arrays.stream(p).anyMatch(Objects::isNull))continue;
                    var at=SweptBladeSurface.closest(point,p[0],p[1],p[2]);double distance=at.distanceToSqr(point);if(distance>gap)continue;
                    var ab=p[1].subtract(p[0]);var ac=p[2].subtract(p[0]);var ap=at.subtract(p[0]);
                    double aa=ab.dot(ab),bb=ab.dot(ac),cc=ac.dot(ac),denominator=aa*cc-bb*bb;if(Math.abs(denominator)<1e-16)continue;
                    double b=(cc*ap.dot(ab)-bb*ap.dot(ac))/denominator,c=(aa*ap.dot(ac)-bb*ap.dot(ab))/denominator;
                    best=new Probe(entry.getKey(),1-b-c,b,c);gap=distance;
                }
                if(best==null){probes.clear();break;}probes.add(best);
            }
            if(probes.size()>=2)result.put(edge.getKey(),List.copyOf(probes));
        }
        return result;
    }
    @Override public void close(){
        try{
            if(triangles.isEmpty())return;
            var cache=MAPPINGS.computeIfAbsent(model,k->new HashMap<>());String key=form+":"+joint;
            var mapped=cache.get(key);if(mapped==null){
                mapped=new Binding(mapping());
                // During growth only the short thumb/reverse blade may be
                // opaque enough to emit probes. Never freeze that incomplete
                // binding: retry all faces until every authored edge is mapped.
                if(mapped.edges.keySet().containsAll(WeaponCutContact.sharpProfiles(form,joint).keySet()))cache.put(key,mapped);
            }
            for(var edge:mapped.edges.entrySet()){
                var points=new ArrayList<Vec3>();
                for(var probe:edge.getValue()){
                    var triangle=triangles.get(probe.face);if(triangle==null||Arrays.stream(triangle.world).anyMatch(Objects::isNull)){points.clear();break;}
                    points.add(triangle.world[0].scale(probe.a).add(triangle.world[1].scale(probe.b)).add(triangle.world[2].scale(probe.c)));
                }
                if(points.size()>=2){
                    if(observer!=null)observer.accept(new Sample(actor.getId(),actor.level().getGameTime()+partial,form,edge.getKey(),List.copyOf(points)));
                    if(actor.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.CONSUME)AdaptiveRagdoll.consumeBlade(actor,form,edge.getKey(),points,partial);
                }
            }
        }finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
}
