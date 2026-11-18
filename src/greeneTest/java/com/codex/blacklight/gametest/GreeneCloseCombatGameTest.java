package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Natural FightGoal/controller regressions. The receiver only observes real
 * hurt callbacks: no test calls damageGreeneClose, hurt, or begin(close/shoot).
 * Each test has its own batch, so another arena cannot become a boss target. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GreeneCloseCombatGameTest {
    private static final class Receiver extends Pig {
        int closeAttempts,closeAccepted,shotsAtFirst=-1;float ageAtFirst=-1;
        boolean physicalOverlapAtFirst;
        Receiver(Level level){super(EntityType.PIG,level);}
        @Override public boolean hurt(DamageSource source,float amount){
            EncounterMob attacker=source.getEntity() instanceof EncounterMob boss&&boss.greeneCloseImpact()?boss:null;
            if(attacker!=null){
                closeAttempts++;
                if(closeAttempts==1){
                    shotsAtFirst=attacker.greeneShots();ageAtFirst=attacker.greeneCloseAge(0);
                    var side=attacker.greeneCloseSide();var pose=GreeneMotion.pose(attacker,0);
                    if(side!=null)for(var track:GreeneCloseAttack.Track.values()){
                        var sphere=GreeneCloseContact.sphere(side,track,pose,attacker.position(),attacker.yBodyRot);
                        if(GreeneCloseContact.contact(sphere,getBoundingBox())!=null)physicalOverlapAtFirst=true;
                    }
                }
            }
            boolean accepted=super.hurt(source,amount);if(attacker!=null&&accepted)closeAccepted++;
            return accepted;
        }
    }
    private record Fixture(EncounterMob boss,Receiver target){}
    private static Fixture fixture(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<24;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var boss=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        // Existing standing encounter: initial emergence is tested separately.
        var standing=boss.greeneBattle().save();standing.putBoolean("OpeningPending",false);boss.greeneBattle().load(standing);
        boss.setPos(h.absoluteVec(new Vec3(8,2,4)));boss.setNoGravity(true);boss.setPersistenceRequired();
        boss.restrictTo(boss.blockPosition(),14);h.getLevel().addFreshEntity(boss);
        var target=new Receiver(h.getLevel());target.setPos(h.absoluteVec(new Vec3(8,2,8)));
        target.setNoAi(true);target.setNoGravity(true);target.noPhysics=true;target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);target.setHealth(300);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);h.getLevel().addFreshEntity(target);
        h.assertTrue(GreeneCloseAttack.aiEligible(4/GreeneMotion.table().sourceUnitBlocks(),true,true),"fixture is inside the real close source range");
        return new Fixture(boss,target);
    }
    private static CompoundTag save(EncounterMob boss){var tag=new CompoundTag();boss.saveWithoutId(tag);return tag;}
    private static boolean hasReceipt(CompoundTag tag,Receiver target){
        var list=tag.getCompound("GreeneClose").getList("Receipts",8);
        for(int i=0;i<list.size();i++)if(list.getString(i).equals(target.getUUID().toString()))return true;
        return false;
    }
    private static void finish(GameTestHelper h,Fixture f){
        for(var shot:h.getLevel().getEntitiesOfClass(GreeneProjectile.class,f.boss.getBoundingBox().inflate(40),p->p.getOwner()==f.boss))shot.discard();
        f.boss.discard();f.target.discard();h.succeed();
    }

    @GameTest(template="blackwatch_arena",batch="greene_close_natural",timeoutTicks=160)
    public static void naturalCloseHitsBeforeFirstSwarmShotAndShootingContinues(GameTestHelper h){
        var f=fixture(h);boolean[] overlap={false},done={false};
        h.onEachTick(()->{
            if(done[0])return;
            if(f.boss.greeneCloseSide()!=null&&f.boss.move()==EncounterMove.VOLLEY){
                overlap[0]=true;
                h.assertTrue(f.boss.greeneProgram()==GreeneWeaponProgram.INSECT_SWARM,"natural near-distance selector uses the forty-tick swarm windup");
            }
            if(f.target.closeAccepted==0)return;
            h.assertTrue(f.target.shotsAtFirst==0&&f.target.ageAtFirst>=26&&f.target.ageAtFirst<40,
                    "real close damage precedes first projectile; projectile damage cannot satisfy this check");
            h.assertTrue(f.target.physicalOverlapAtFirst,"actual hurt callback overlaps the same posed TIP/root spheres used by rendering");
            h.assertTrue(f.target.getHealth()<300,"real receiver accepted damage");
            if(f.boss.greeneShots()==0)return;
            h.assertTrue(overlap[0]&&f.boss.greeneCloseSide()!=null,"real socket release continues during the same Slug animation");
            h.assertTrue(f.boss.getTarget()==f.target,"acquisition retains this real on-foot target");
            done[0]=true;finish(h,f);
        });
    }
    @GameTest(template="blackwatch_arena",batch="greene_close_dodge",timeoutTicks=160)
    public static void targetLeavesTheWindupAndTheRealSwingMisses(GameTestHelper h){
        var f=fixture(h);boolean[] dodged={false},done={false};
        long[] closeStart={0},shootStart={0};GreeneCloseAttack.Side[] committedSide={null};
        h.onEachTick(()->{
            if(done[0])return;var side=f.boss.greeneCloseSide();
            if(dodged[0])h.assertTrue(side==committedSide[0],"the committed Slug action must continue through the dodge");
            if(side==null)return;
            float age=f.boss.greeneCloseAge(0);
            // The independent close branch can start during the entity's initial
            // twenty-tick shooting cooldown. Forty close ticks are therefore
            // NOT forty shoot ticks. First observe both naturally running.
            if(!dodged[0]&&age>=12&&f.boss.move()==EncounterMove.VOLLEY){
                h.assertTrue(f.boss.greeneProgram()==GreeneWeaponProgram.INSECT_SWARM,"fixture naturally committed the close-range shooting program");
                h.assertTrue(age<26&&f.target.closeAttempts==0,"dodge occurs before any close Hit");
                closeStart[0]=h.getLevel().getGameTime()-Math.round(age);
                shootStart[0]=h.getLevel().getGameTime()-Math.round(f.boss.moveAge(0));committedSide[0]=side;
                f.target.setPos(h.absoluteVec(new Vec3(1,2,15)));f.target.setDeltaMovement(Vec3.ZERO);dodged[0]=true;
            }
            if(!dodged[0])return;
            h.assertTrue(f.target.closeAttempts==0,"committed attack must not damage the old target position or substitute its new root");
            h.assertTrue(h.getLevel().getGameTime()-Math.round(age)==closeStart[0],"a restarted Slug action cannot satisfy the original swing check");
            h.assertTrue(f.boss.move()==EncounterMove.VOLLEY&&f.boss.greeneProgram()==GreeneWeaponProgram.INSECT_SWARM
                    &&h.getLevel().getGameTime()-Math.round(f.boss.moveAge(0))==shootStart[0],
                    "the same independently committed shoot action must remain active, without cancel/restart");
            if(age<44)return;
            var pose=GreeneMotion.pose(f.boss,0);
            for(var track:GreeneCloseAttack.Track.values())h.assertTrue(GreeneCloseContact.contact(
                    GreeneCloseContact.sphere(side,track,pose,f.boss.position(),f.boss.yBodyRot),f.target.getBoundingBox())==null,
                    "arena escape remains outside posed contact geometry");
            if(f.boss.greeneShots()==0){
                // onEachTick can observe the boundary before this actor's update.
                // The next observed update must have performed the age40 shot.
                h.assertTrue(f.boss.moveAge(0)<=41,"missed Slug swing must not suppress the actual age40 first-shot window");return;
            }
            h.assertTrue(f.boss.moveAge(0)>=40&&f.boss.greeneCloseSide()!=null,
                    "real projectile emission overlaps the same missed Slug action");
            done[0]=true;finish(h,f);
        });
    }
    @GameTest(template="blackwatch_arena",batch="greene_close_wall",timeoutTicks=160)
    public static void wallSeversTheRootContactEvenWhenSphereAndVictimAreBeyondIt(GameTestHelper h){
        var f=fixture(h);boolean[] walled={false},provedBlockedContact={false},done={false};
        long[] closeStart={0};GreeneCloseAttack.Side[] committedSide={null};
        h.onEachTick(()->{
            if(done[0])return;var side=f.boss.greeneCloseSide();
            if(walled[0])h.assertTrue(side==committedSide[0],"wall check must exercise the continuing committed swing, not an early cancellation");
            if(side==null)return;float age=f.boss.greeneCloseAge(0);
            if(!walled[0]&&age>=12&&f.boss.move()==EncounterMove.VOLLEY){
                h.assertTrue(age<26&&f.target.closeAttempts==0,"place cover during a genuine pre-contact windup");
                for(int x=0;x<16;x++)for(int y=2;y<24;y++)h.setBlock(x,y,6,Blocks.STONE);
                closeStart[0]=h.getLevel().getGameTime()-Math.round(age);committedSide[0]=side;walled[0]=true;
            }
            if(!walled[0])return;
            h.assertTrue(h.getLevel().getGameTime()-Math.round(age)==closeStart[0],"wall must not hide cancellation by starting a fresh action");
            h.assertTrue(f.target.closeAttempts==0,"a real Slug/root Hit cannot damage the receiver through solid cover");
            if(age>=32&&age<=38){
                var pose=GreeneMotion.pose(f.boss,0);var track=GreeneCloseAttack.Track.INNER;
                var sphere=GreeneCloseContact.sphere(side,track,pose,f.boss.position(),f.boss.yBodyRot);
                var hit=GreeneCloseContact.contact(sphere,f.target.getBoundingBox());
                h.assertTrue(hit!=null,"fixture remains in the actual root Hit sphere instead of dodging it");
                h.assertTrue(GreeneCloseContact.unobstructed(h.getLevel(),f.boss,sphere,hit.point()),
                        "sphere-to-victim alone is clear on the far side of the wall");
                h.assertTrue(!GreeneCloseContact.connected(h.getLevel(),f.boss,side,track,pose,f.boss.position(),f.boss.yBodyRot,sphere),
                        "the real body-to-sphere connection crosses the wall and must suppress dispatch");
                provedBlockedContact[0]=true;
            }
            if(age<44)return;
            h.assertTrue(provedBlockedContact[0]&&f.target.closeAccepted==0,"active Hit windows were blocked without any real close hurt callback");
            done[0]=true;finish(h,f);
        });
    }
    @GameTest(template="blackwatch_arena",batch="greene_close_collapse",timeoutTicks=160)
    public static void collapseCancelsTheWindupWithoutLateContactDamage(GameTestHelper h){
        var f=fixture(h);long[] interrupted={Long.MIN_VALUE};boolean[] done={false};
        h.onEachTick(()->{
            if(done[0])return;
            if(interrupted[0]==Long.MIN_VALUE){
                if(f.boss.greeneCloseSide()==null||f.boss.greeneCloseAge(0)<18)return;
                h.assertTrue(f.target.closeAttempts==0,"exercise cancellation before the first real Hit");
                f.boss.greeneBattle().collapse(400);interrupted[0]=h.getLevel().getGameTime();
                h.assertTrue(f.boss.greeneStage()==GreeneBattleCycle.COLLAPSING&&f.boss.greeneCloseSide()==null,
                        "real phase entry cancels the independent close controller and synchronized pose");
            }
            h.assertTrue(f.target.closeAttempts==0&&f.target.getHealth()==300,"old close/shoot actions cannot dispatch damage during collapse");
            if(h.getLevel().getGameTime()-interrupted[0]<45)return;
            h.assertTrue(f.boss.greeneCloseSide()==null,"no late reactivation beyond all old Hit windows");
            done[0]=true;finish(h,f);
        });
    }
    @GameTest(template="blackwatch_arena",batch="greene_close_reload",timeoutTicks=160)
    public static void savedSharedReceiptRejectsLaterWindowsAfterRealEntityReload(GameTestHelper h){
        var f=fixture(h);boolean[] loaded={false},done={false};
        h.onEachTick(()->{
            if(done[0])return;
            // An independent window must not be concealed by vanilla hurt cooldown.
            f.target.invulnerableTime=0;
            if(!loaded[0]){
                if(f.target.closeAccepted==0)return;
                h.assertTrue(f.target.closeAttempts==1&&f.target.shotsAtFirst==0,"save follows one actual close contact, before missile emission");
                var tag=save(f.boss);h.assertTrue(hasReceipt(tag,f.target),"real controller save contains victim's shared receipt");
                f.boss.load(tag);f.boss.setTarget(f.target);loaded[0]=true;
                h.assertTrue(hasReceipt(save(f.boss),f.target),"same-object entity reload retains the consumed UUID");
                h.assertTrue(f.boss.greeneCloseSide()!=null,"reload resumes the remaining source action without restarting it");
            }
            h.assertTrue(f.target.closeAttempts==1,"INNER/OUTER must reject the saved victim even with no hurt immunity");
            if(f.boss.greeneCloseAge(0)<44)return;
            h.assertTrue(f.boss.greeneCloseSide()!=null,"check the same continuing action before another attack can begin");
            var sphere=GreeneCloseContact.sphere(f.boss.greeneCloseSide(),GreeneCloseAttack.Track.OUTER,
                    GreeneMotion.pose(f.boss,0),f.boss.position(),f.boss.yBodyRot);
            h.assertTrue(GreeneCloseContact.contact(sphere,f.target.getBoundingBox())!=null,
                    "target remains physically in the later root sphere; this is a receipt check, not a dodge");
            done[0]=true;finish(h,f);
        });
    }
}
