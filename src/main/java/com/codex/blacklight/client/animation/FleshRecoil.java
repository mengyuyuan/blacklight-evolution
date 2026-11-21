package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.combat.FleshStrike;
import com.codex.blacklight.network.InfectedImpactPayload;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.WeakHashMap;

/** Reaction to a confirmed strike, evaluated in victim space on the render clock. */
public final class FleshRecoil {
    private record Contact(FleshStrike strike, double start, float side, float facing, float strength, int region, float pull) {}
    public record Pose(float pitch, float twist, float roll, float head, float brace, float dip, float leg, float arm) {
        Pose(float pitch,float twist,float roll,float head,float brace,float dip){this(pitch,twist,roll,head,brace,dip,0,0);}
    }
    private static final WeakHashMap<LivingEntity, Contact> contacts = new WeakHashMap<>();

    private FleshRecoil() {}

    public static void accept(LivingEntity victim, InfectedImpactPayload receipt, double start) {
        FleshStrike strike = receipt.strike();
        if (strike == FleshStrike.GENERIC || (strike == FleshStrike.QUILL && receipt.size() < .4F) || !victim.isAlive()
                || CombatTargeting.anchoredBody(victim)) return;
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 forward = new Vec3(-right.z, 0, right.x);
        Vec3 normal = new Vec3(receipt.dx(), receipt.dy(), receipt.dz());
        Vec3 offset = new Vec3(receipt.x(), receipt.y(), receipt.z()).subtract(victim.position());
        double lateral = offset.dot(right) / Math.max(.15, victim.getBbWidth());
        if (Math.abs(lateral) < .12) lateral = normal.dot(right);
        float side = (float)Math.copySign(1, Math.abs(lateral) < .04 ? ((receipt.seed() & 1) == 0 ? 1 : -1) : lateral);
        float facing = normal.dot(forward) < -.18 ? -1 : 1;
        float strength = Math.min(1.35F, .44F + receipt.size() * .67F + (receipt.charged() ? .10F : 0));
        var previous = contacts.get(victim);
        if (previous != null && previous.start == start && previous.strength >= strength) return;
        int region=receipt.region();
        if(region==2 || region==8)side=-1;
        if(region==4 || region==16)side=1;
        contacts.put(victim,new Contact(strike,start,side,facing,strength,region,
                (float)(Math.abs(normal.dot(right))>.12?Math.signum(normal.dot(right)):side)));
    }

    public static Pose sample(LivingEntity victim, float partial) {
        Contact contact = contacts.get(victim);
        if (contact == null || !victim.isAlive()) return null;
        float age = (float)(victim.level().getGameTime() - contact.start + partial);
        if (age < 0) return null;
        float duration = switch (contact.strike) {
            case FIST, CLAW -> 7;
            case EDGE -> 9;
            case QUILL -> 6;
            case LASH -> 10;
            case HAMMER -> 12;
            default -> 11;
        };
        if (age >= duration) {
            contacts.remove(victim);
            return null;
        }
        // The receiver snaps first. Arms and weight settle a beat later.
        float snap = pulse(age, 1.0F, duration * .83F) * contact.strength;
        float weight = pulse(age, 2.5F, duration) * contact.strength;
        float rebound = pulse(Math.max(0, age - duration * .43F), 1.6F, duration * .55F);
        float turn = contact.side * (snap - .24F * rebound);
        float pitch = contact.facing * (snap - .22F * rebound);
        if(contact.strike==FleshStrike.LASH && contact.region!=0){
            float shoulder=pulse(age,.65F,5.4F)*contact.strength*contact.side;
            float follow=pulse(Math.max(0,age-.6F),1.55F,8)*contact.strength*contact.pull;
            return new Pose(.12F*pitch,.72F*follow,.28F*follow,.14F*pitch,.32F*weight,.52F*weight,0,.80F*shoulder);
        }
        return switch (contact.strike) {
            case CLAW -> new Pose(-.14F*pitch, .51F*turn, .19F*turn, -.24F*pitch, .33F*weight, .68F*weight);
            case EDGE -> new Pose(-.21F*pitch, .70F*turn, .24F*turn, -.27F*pitch, .46F*weight, 1.12F*weight);
            case QUILL -> new Pose(-.31F*pitch, .34F*turn, .18F*turn, -.36F*pitch, .27F*weight, .46F*weight,
                    (contact.region&24)!=0?1.6F*weight:0,(contact.region&6)!=0?.65F*turn:0);
            case FIST -> new Pose(-.32F*pitch, .20F*turn, .09F*turn, -.52F*pitch, .36F*weight, .8F*weight);
            case LASH -> new Pose(.18F*pitch, 1.03F*turn, .52F*turn, .30F*pitch, .94F*weight, 1.4F*weight);
            case HAMMER -> new Pose(.76F*pitch, .18F*turn, .30F*turn, .42F*pitch, .62F*weight,
                    ((contact.region&24)!=0?4.5F:2.6F)*weight,(contact.region&24)!=0?weight*contact.side:0,
                    (contact.region&6)!=0?weight*contact.side:0);
            default -> new Pose(-.82F*pitch, .28F*turn, .24F*turn, -.43F*pitch, 1.02F*weight, -.6F*weight);
        };
    }

    private static float pulse(float age, float peak, float end) {
        if (age >= end) return 0;
        float x = age < peak ? age / peak : (end - age) / (end - peak);
        return x*x*(3-2*x);
    }
}
