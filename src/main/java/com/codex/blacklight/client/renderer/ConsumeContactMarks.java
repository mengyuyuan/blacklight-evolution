package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.power.PowerForm;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Accepted cosmetic contacts. Geometry, sound and audit observers share the same receiver/scene identity. */
public final class ConsumeContactMarks {
    public record Hit(LivingEntity victim,ConsumeVisual visual,String kind,int ordinal,Vec3 world,int region,int before,int after) {}
    public static java.util.function.Consumer<Hit> observer;
    static final class SceneHits {
        private long start=Long.MIN_VALUE;
        private int owner=-1;
        private final Set<String> hits=new HashSet<>();
        boolean accept(long scene,int actor,String kind,int ordinal){
            if(start!=scene||owner!=actor){hits.clear();start=scene;owner=actor;}
            return hits.add(kind+":"+ordinal);
        }
    }
    private static final Map<LivingEntity,SceneHits> SEEN=new WeakHashMap<>();
    private ConsumeContactMarks() {}
    static void confirmed(LivingEntity victim,ConsumeVisual visual,String kind,int ordinal,Vec3 world,int region,int before,int after){
        if(victim.isRemoved()||!visual.consuming()||!Double.isFinite(world.lengthSqr()))return;
        if(!SEEN.computeIfAbsent(victim,k->new SceneHits()).accept(visual.started(),visual.consumerId(),kind,ordinal))return;
        ConsumeContactAudio.confirmed(victim,visual,kind,ordinal,world);
        if(observer!=null)observer.accept(new Hit(victim,visual,kind,ordinal,world,region,before,after));
    }
    static String mountedKind(ConsumeVisual visual){
        if(visual.stage()==ConsumeVisual.HUNTER_CONSUME)return "tear";
        return switch(PowerForm.values()[visual.form()]){
            case CLAWS,BLADE -> "slash";
            case WHIPFIST,MUSCLE_MASS -> "tear";
            case HAMMERFISTS -> "crush";
            default -> "dent";
        };
    }
    static Vec3 barycentric(Vec3 p,Vec3 a,Vec3 b,Vec3 c){
        Vec3 ab=b.subtract(a),ac=c.subtract(a),ap=p.subtract(a);
        double bb=ab.dot(ab),bc=ab.dot(ac),cc=ac.dot(ac),pb=ap.dot(ab),pc=ap.dot(ac);
        double det=bb*cc-bc*bc;
        if(det<1e-16)return new Vec3(1,0,0);
        double wb=Math.max(0,Math.min(1,(pb*cc-pc*bc)/det));
        double wc=Math.max(0,Math.min(1-wb,(pc*bb-pb*bc)/det));
        return new Vec3(1-wb-wc,wb,wc);
    }
    static Vec3 weighted(Vec3 w,Vec3 a,Vec3 b,Vec3 c){
        return a.scale(w.x).add(b.scale(w.y)).add(c.scale(w.z));
    }
}
