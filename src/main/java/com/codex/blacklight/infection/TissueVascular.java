package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModBlocks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** One paid, retractable connection carries existing digested food, never a fresh meal or capture credit. */
public final class TissueVascular {
    public static final int CONTACT=12,RETRACT=26,END=40,OWN_RESERVE=96,OPEN_COST=2;
    private static final Map<ServerLevel,Budget> BUDGETS=new WeakHashMap<>();
    private static final class Budget{long tick=Long.MIN_VALUE;int searches;}
    private BlockPos target;private int elapsed=-1,cooldown,delivered,approach,visualTail;private long started,nextSearch;private boolean committed;
    public boolean active(){return elapsed>=0;}
    public boolean seeking(){return target!=null&&!active();}
    public int cooldown(){return cooldown;}public int delivered(){return delivered;}
    public static int amount(double power,int food,int room){if(!Double.isFinite(power)||power<=0)return 0;return (int)Math.max(0,Math.min(Math.min(Math.max(0L,(long)food-OWN_RESERVE),Math.max(0,room)),8+(int)Math.ceil(Math.min(8,power)*8)));}
    public static float extension(float time){if(!Float.isFinite(time)||time<0||time>=END)return 0;float t=time<CONTACT?time/CONTACT:time<RETRACT?1:(END-time)/(END-RETRACT);t=com.codex.blacklight.forge.WingMath.clamp(t,0,1);return t*t*(3-2*t);}
    public static float visualExtension(CompoundTag state,double now){if(state.isEmpty()||!Double.isFinite(now))return 0;if(!state.contains("CancelledAt"))return extension((float)(now-state.getLong("Started")));float t=(float)com.codex.blacklight.forge.WingMath.clamp((now-state.getLong("CancelledAt"))/8,0,1),from=state.getFloat("CancelFrom");return Float.isFinite(from)?com.codex.blacklight.forge.WingMath.clamp(from,0,1)*(1-t*t*(3-2*t)):0;}
    private static boolean work(ServerLevel level){long now=level.getGameTime();var budget=BUDGETS.computeIfAbsent(level,k->new Budget());if(budget.tick!=now){budget.tick=now;budget.searches=0;}if(budget.searches>=4)return false;budget.searches++;return true;}
    public static Vec3 root(BlacklightInfectedHost host,float partial){return host.getPosition(partial).add(0,host.getBbHeight()*.48+host.tissueStanceOffset(),0);}
    private static boolean free(BlacklightInfectedHost host){return TissueFeeding.free(host)&&!host.isNoAi()&&!host.isEvolved()&&host.getHealth()>=host.getMaxHealth()*.6F
            &&(host.getTarget()==null||!host.getTarget().isAlive()||host.distanceToSqr(host.getTarget())>16*16);}
    private ColonyNutrients.Mouth colony(BlacklightInfectedHost host){return host.level() instanceof ServerLevel level&&target!=null?ColonyNutrients.resolve(level,target):null;}
    public static Vec3 mouth(ServerLevel level,BlockPos pos){var s=level.getBlockState(pos);return Vec3.atCenterOf(pos).relative(s.getValue(com.codex.blacklight.world.BuildingInfectionCoreBlock.FACING),.7);}
    private static boolean valid(BlacklightInfectedHost host,ColonyNutrients.Mouth c,boolean contact){
        if(!(host.level() instanceof ServerLevel level)||c==null||!WorldOutbreakSystem.active(level)
                ||!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)||!level.hasChunkAt(c.pos())||!level.isPositionEntityTicking(c.pos())
                ||!ColonyNutrients.isPort(level.getBlockState(c.pos())))return false;
        if(!contact)return host.distanceToSqr(Vec3.atCenterOf(c.pos()))<=16*16;
        var from=root(host,0);var to=mouth(level,c.pos());if(from.distanceToSqr(to)>25)return false;
        for(int x:new int[]{(int)Math.floor(Math.min(from.x,to.x)),(int)Math.floor(Math.max(from.x,to.x))})for(int z:new int[]{(int)Math.floor(Math.min(from.z,to.z)),(int)Math.floor(Math.max(from.z,to.z))})if(!level.hasChunkAt(new BlockPos(x,c.pos().getY(),z)))return false;
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,host)).getType()==HitResult.Type.MISS;
    }
    public boolean start(BlacklightInfectedHost host,BuildingInfectionData.Colony c){return host.level() instanceof ServerLevel level&&c!=null&&start(host,ColonyNutrients.resolve(level,c.origin));}
    public boolean startAt(BlacklightInfectedHost host,BlockPos pos){return host.level() instanceof ServerLevel level&&start(host,ColonyNutrients.resolve(level,pos));}
    private boolean start(BlacklightInfectedHost host,ColonyNutrients.Mouth c){
        double power=host.organPower(TissueCore.VASCULAR);if(active()||cooldown>0||!free(host)||!valid(host,c,true)||amount(power,host.tissueNutrition()-OPEN_COST,c.stock().room())==0||!host.spendTissueNutrition(OPEN_COST))return false;
        target=c.pos();elapsed=0;started=host.level().getGameTime();committed=false;delivered=0;host.getNavigation().stop();publish(host);return true;
    }
    public void interrupt(BlacklightInfectedHost host){
        if(!active()&&!seeking())return;boolean connected=active();var previous=host.vascularVisual();
        elapsed=-1;target=null;approach=0;cooldown=Math.max(cooldown,80);host.getNavigation().stop();
        if(connected&&!previous.isEmpty()){long now=host.level().getGameTime();previous.putFloat("CancelFrom",visualExtension(previous,now));previous.putLong("CancelledAt",now);previous.putBoolean("Committed",false);host.setVascularVisual(previous);visualTail=8;}else publish(host);
    }
    private void publish(BlacklightInfectedHost host){var tag=new CompoundTag();if(active()&&target!=null){tag.putLong("Target",target.asLong());tag.putLong("Started",started);tag.putInt("Delivered",delivered);tag.putBoolean("Committed",committed);var old=host.level().getBlockState(target);if(old.hasProperty(com.codex.blacklight.world.BuildingInfectionCoreBlock.FACING)){var end=Vec3.atCenterOf(target).relative(old.getValue(com.codex.blacklight.world.BuildingInfectionCoreBlock.FACING),.7);tag.putDouble("EndX",end.x);tag.putDouble("EndY",end.y);tag.putDouble("EndZ",end.z);}}host.setVascularVisual(tag);}
    public boolean tick(BlacklightInfectedHost host){
        if(visualTail>0&&--visualTail==0)publish(host);
        if(cooldown>0)cooldown--;
        if(active()){
            var c=colony(host);if(!free(host)||!valid(host,c,true)){interrupt(host);return false;}
            host.getNavigation().stop();host.setXxa(0);host.setZza(0);host.setDeltaMovement(host.getDeltaMovement().multiply(.35,1,.35));
            elapsed++;
            if(elapsed==CONTACT&&!committed){
                int n=amount(host.organPower(TissueCore.VASCULAR),host.tissueNutrition(),c.stock().room());
                if(n>0&&host.spendTissueNutrition(n)){int accepted=c.stock().receive(n);if(accepted!=n)throw new IllegalStateException("Vascular destination changed during a server-owned transfer");delivered=n;c.changed().run();}
                committed=true;publish(host);
            }
            if(elapsed>=END){elapsed=-1;target=null;cooldown=160;publish(host);return false;}return true;
        }
        if(cooldown>0||host.organPower(TissueCore.VASCULAR)<=0||host.tissueNutrition()<192||!free(host)||!(host.level() instanceof ServerLevel level)){if(seeking())interrupt(host);return false;}
        if(seeking()){
            var c=colony(host);if(!valid(host,c,false)||c.stock().reserve()>=TissueMetabolism.MAX_RESERVE/2||++approach>160){interrupt(host);return false;}
            if(start(host,c))return true;
            if(approach%20==0&&work(level)){var entry=mouth(level,c.pos());host.getNavigation().moveTo(entry.x,c.pos().getY(),entry.z,.85);}return false;
        }
        long now=level.getGameTime();if(now<nextSearch)return false;nextSearch=now+40+Math.floorMod(host.getId(),17);
        if(!work(level)){nextSearch=now+1+Math.floorMod(host.getId(),5);return false;}
        var choice=ColonyNutrients.nearby(level,host.position(),16).stream().filter(c->c.stock().reserve()<TissueMetabolism.MAX_RESERVE/2&&valid(host,c,false))
                .min(Comparator.comparingDouble(c->host.distanceToSqr(Vec3.atCenterOf(c.pos())))).orElse(null);
        if(choice==null)return false;if(start(host,choice))return true;target=choice.pos();approach=0;
        var entry=mouth(level,choice.pos());host.getNavigation().moveTo(entry.x,choice.pos().getY(),entry.z,.85);return false;
    }
    public CompoundTag write(){var t=new CompoundTag();t.putInt("Cooldown",Math.max(cooldown,active()?160:0));return t;}
    public static TissueVascular read(CompoundTag t){var v=new TissueVascular();v.cooldown=com.codex.blacklight.forge.WingMath.clamp(t.getInt("Cooldown"),0,160);return v;}
    public static List<Vec3> path(Vec3 from,Vec3 to,float time){
        return path(from,to,time,extension(time));
    }
    public static List<Vec3> path(Vec3 from,Vec3 to,float time,float reach){
        var end=from.lerp(to,reach);var control=from.lerp(end,.5).add(0,-.18*reach,0);var points=new ArrayList<Vec3>(21);
        var side=to.subtract(from).cross(new Vec3(0,1,0)).normalize();
        for(int i=0;i<=20;i++){double t=i/20D;var p=from.scale((1-t)*(1-t)).add(control.scale(2*(1-t)*t)).add(end.scale(t*t));points.add(p.add(side.scale(Math.sin(Math.PI*t)*Math.sin(time*.22-t*7)*.035*reach)));}return points;
    }
    public static void pulse(float[] vertices,CompoundTag state,float time){
        if(state.isEmpty()||vertices.length<48)return;float e=visualExtension(state,(double)state.getLong("Started")+time);if(e==0)return;float x=0,y=0,z=0;for(int i=0;i<24;i+=3){x+=vertices[i]/8;y+=vertices[i+1]/8;z+=vertices[i+2]/8;}
        for(int i=24;i<vertices.length;i+=3){float t=i/(float)vertices.length,s=1+.045F*(float)Math.sin(time*.6-t*6)*e;vertices[i]=x+(vertices[i]-x)*s;vertices[i+1]=y+(vertices[i+1]-y)*s;vertices[i+2]=z+(vertices[i+2]-z)*s;}
    }
}
