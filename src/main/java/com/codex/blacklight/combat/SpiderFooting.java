package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Feet stay in world space while the heavy shell turns. Only a change of support is sent to observers. */
public final class SpiderFooting {
    private static final int STRIDE = 9;
    private static final int[] ORDER = {0, 6, 2, 4, 1, 7, 3, 5};
    private static final String[] KEYS = {"L1","L2","L3","L4","R1","R2","R3","R4"};
    private final EncounterMob boss;
    private int cursor;

    SpiderFooting(EncounterMob boss) { this.boss = boss; }

    private Vec3 restPoint(int leg, float partial) {
        var tip = SpiderAnatomy.TIPS.get(SpiderAnatomy.shin(leg));
        return boss.getPosition(partial).add(tip.scale(1.9824204).yRot((float)Math.toRadians(180 - boss.skinYaw(partial))));
    }

    private Vec3 surface(ServerLevel level, Vec3 desired) {
        if (!level.hasChunkAt(BlockPos.containing(desired))) return null;
        var ray = level.clip(new ClipContext(desired.add(0, 3.4, 0), desired.add(0, -3.5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        return ray.getType() == HitResult.Type.BLOCK && ray.getDirection() == net.minecraft.core.Direction.UP
                ? ray.getLocation().add(0,.022,0) : null;
    }

    private static Vec3 position(CompoundTag foot, String prefix) {
        return new Vec3(foot.getDouble(prefix+"X"),foot.getDouble(prefix+"Y"),foot.getDouble(prefix+"Z"));
    }

    private static void position(CompoundTag foot, String prefix, Vec3 p) {
        foot.putDouble(prefix+"X",p.x);foot.putDouble(prefix+"Y",p.y);foot.putDouble(prefix+"Z",p.z);
    }

    private CompoundTag planted() { return boss.spiderData().getCompound("Footing"); }

    public Vec3 foot(int leg, float partial) {
        var contacts = planted();
        if (!contacts.contains(KEYS[leg])) return restPoint(leg,partial);
        var step = contacts.getCompound(KEYS[leg]);
        Vec3 end = position(step,"To");
        float phase = Mth.clamp((boss.level().getGameTime()-step.getLong("At")+partial)/STRIDE,0,1);
        if (phase>=1) return end;
        var start = position(step,"From");
        double lift = Math.min(2.3,.48 + .19*start.distanceTo(end));
        return start.lerp(end,phase*phase*(3-2*phase)).add(0,lift*Math.sin(Math.PI*phase),0);
    }

    public void tick(ServerLevel level) {
        var contacts = planted();
        long now = level.getGameTime();
        boolean empty = contacts.isEmpty();
        if (!empty) {
            for (int i=0;i<8;i++) {
                if (!contacts.contains(KEYS[i]) || boss.spider.broken(i)) continue;
                var step = contacts.getCompound(KEYS[i]);
                if (step.getLong("At")+STRIDE==now && step.getBoolean("Contact") && !busy(i)) {
                    var at=position(step,"To");SpiderFoley.step(boss,at);
                }
            }
            if ((now&1)!=0 || boss.spider.actions.move()==SpiderActions.Move.POUNCE) return;
        }
        int lifting=0;for(int i=0;i<8;i++)if(!boss.spider.broken(i) && contacts.contains(KEYS[i]) &&
                now-contacts.getCompound(KEYS[i]).getLong("At")<STRIDE)lifting++;
        if(lifting>=Math.max(1,Math.min(3,(8-Integer.bitCount(boss.spider.brokenMask()))/2)))return;
        var next=contacts.copy();
        for (int test=0;test<(empty?8:2);test++) {
            int leg=ORDER[cursor++ & 7];
            if(boss.spider.broken(leg)||busy(leg))continue;
            Vec3 neutral=restPoint(leg,1);
            Vec3 ground=surface(level,neutral);
            Vec3 end=ground==null?neutral.add(0,-1.7,0):ground;
            var old=contacts.getCompound(KEYS[leg]);
            if(!old.isEmpty() && now-old.getLong("At")<STRIDE)continue;
            var prev=old.isEmpty()?end:position(old,"To");
            // Turning may leave the old foot on a different block height. Never plant on removed support.
            var support=old.isEmpty()?null:surface(level,prev);
            boolean valid=support!=null && Math.abs(support.y-prev.y)<.16;
            if(!old.isEmpty() && prev.distanceToSqr(end)<2.3*2.3 && (valid || ground==null && !old.getBoolean("Contact")))continue;
            var step=new CompoundTag();
            position(step,"From",prev);position(step,"To",end);step.putLong("At",old.isEmpty()?now-STRIDE-1:now);
            step.putBoolean("Contact",ground!=null);next.put(KEYS[leg],step);
            if(!empty)break;
        }
        if(!next.equals(contacts)){var s=boss.spiderData().copy();s.put("Footing",next);boss.spiderData(s);}
    }

    public boolean pairedSupport(){
        int healthy=(~boss.spider.brokenMask())&255;
        return Integer.bitCount(healthy)>=4 && (healthy&15)!=0 && (healthy&240)!=0;
    }
    public record Balance(float across,float lower,float along,float roll,float pitch){}
    private static final Balance LEVEL=new Balance(0,0,0,0,0);
    private CompoundTag balanceState;
    private long balanceTime;
    private float balancePartial,balanceYaw;
    private Vec3 balanceOrigin;
    private Balance balance=LEVEL;

    /** Resolve the centre of pressure from the eight actual footholds in shell coordinates.
     * A newly broken leg unloads over ten ticks; the existing knockdown owns the immediate fall. */
    public Balance balance(float partial){
        var state=boss.spiderData();int lost=boss.spider.brokenMask();if(lost==0)return LEVEL;
        var origin=boss.getPosition(partial);var now=boss.level().getGameTime();float yaw=boss.skinYaw(partial);
        if(balanceState==state && balanceTime==now && balanceYaw==yaw && balancePartial==partial && origin.equals(balanceOrigin))return balance;
        float total=0,baselineX=0,baselineZ=0;double x=0,z=0;
        var contacts=planted();float angle=(float)Math.toRadians(yaw-180);
        for(int leg=0;leg<8;leg++){
            var bind=SpiderAnatomy.TIPS.get(SpiderAnatomy.shin(leg));
            baselineX+=bind.x/8;baselineZ+=bind.z/8;
            var contact=contacts.getCompound(KEYS[leg]);var local=bind;
            float weight=1;
            if(!contact.isEmpty()){
                if(!contact.getBoolean("Contact"))weight=.10F;
                local=foot(leg,partial).subtract(origin).yRot(angle).scale(1/1.9824204);
                float progress=Mth.clamp((now-contact.getLong("At")+partial)/STRIDE,0,1);
                weight*=1-.75F*(float)Math.sin(Math.PI*progress);
            }
            if(boss.spider.broken(leg))weight*=1-SpiderGesture.ease(now-state.getLong("Break"+leg)+partial,0,10);
            x+=local.x*weight;z+=local.z*weight;total+=weight;
        }
        // Below one effective support the abdominal shell takes the weight.
        double blend=Math.min(total,1);
        float across=(float)Mth.clamp((x/Math.max(total,.001F)-baselineX)*blend/8,-1,1);
        float along=(float)Mth.clamp((z/Math.max(total,.001F)-baselineZ)*blend/5.7,-1,1);
        float lower=-.055F*Integer.bitCount(lost)-.34F*(1-Math.min(total/3,1));
        balance=new Balance(across*.98F,lower,along*.83F,across*.31F,-along*.31F);
        balanceState=state;balanceTime=now;balanceYaw=yaw;balancePartial=partial;balanceOrigin=origin;
        return balance;
    }

    /** Take weight on a different leg before the attacking limb leaves the ground. */
    void brace(SpiderActions.Move move,int striking,Vec3 aim) {
        if(striking<0 || !(boss.level() instanceof ServerLevel level))return;
        var contacts=planted();
        var forward=aim.subtract(boss.position()).multiply(1,0,1).normalize();
        if(forward.lengthSqr()<.5)return;
        int bank=striking<4?4:0;
        for(int i=0;i<4;i++){
            int leg=bank+(striking%4+1+i)%4;
            if(boss.spider.broken(leg) || busy(leg) || !contacts.contains(KEYS[leg]))continue;
            var old=contacts.getCompound(KEYS[leg]);
            if(!old.getBoolean("Contact") || level.getGameTime()-old.getLong("At")<STRIDE)continue;
            var start=position(old,"To");
            var out=start.subtract(boss.position()).multiply(1,0,1).normalize();
            double along=move==SpiderActions.Move.SLAM?.95:move==SpiderActions.Move.SWEEP?.35:-.95;
            var neutral=restPoint(leg,1);
            var end=surface(level,neutral.add(out.scale(1.55)).add(forward.scale(along)));
            if(end==null || Math.abs(end.y-start.y)>1.65 || start.distanceToSqr(end)>16)continue;
            if(start.distanceToSqr(end)<.28)return; // Already braced from the last exchange.
            var step=new CompoundTag();position(step,"From",start);position(step,"To",end);
            step.putLong("At",level.getGameTime());step.putBoolean("Contact",true);
            var next=contacts.copy();next.put(KEYS[leg],step);
            var state=boss.spiderData().copy();state.put("Footing",next);boss.spiderData(state);return;
        }
    }

    private boolean busy(int leg) {
        int selected=boss.spiderData().getInt("Foot");
        return switch(boss.spider.actions.move()) {
            case STAB,SWEEP,POUNCE -> selected==leg;
            case MARCH -> selected==leg || boss.spiderData().getInt("RushSecond")==leg;
            case PINCER,SWALLOW -> selected>=0 && selected%4==leg%4;
            default -> false;
        };
    }
}
