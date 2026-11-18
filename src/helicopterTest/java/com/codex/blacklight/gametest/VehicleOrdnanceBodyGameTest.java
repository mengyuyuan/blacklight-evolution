package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleOrdnanceBodyGameTest {
    private static EncounterMob greene(HelicopterPilotGameTest.Fixture f){
        var boss=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(f.level);
        // Explicit existing encounter fixture; fresh opening has its own regression.
        var standing=boss.greeneBattle().save();standing.putBoolean("OpeningPending",false);boss.greeneBattle().load(standing);

        boss.setPos(f.x,81,16);boss.setNoAi(true);boss.setPersistenceRequired();f.level.addFreshEntity(boss);f.entities.add(boss);return boss;
    }
    private static boolean damaged(EncounterMob boss){
        var tag=new CompoundTag();boss.saveWithoutId(tag);
        float full=boss.greeneSegmentMaximum();
        return tag.getFloat("EncounterArmor0")<full||tag.getFloat("EncounterArmor1")<full||tag.getFloat("EncounterArmor2")<full;
    }
    @GameTest(template="helicopter_arena",batch="ordnanceBody",timeoutTicks=80)
    public static void realApProjectileDamagesTheContactedGreeneAppendage(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,300000);var craft=f.craft(0);
        var player=HelicopterPilotGameTest.player(h,f,false);var boss=greene(f);
        h.runAfterDelay(30,()->{
            for(var region:boss.hurtRegions(0))if(region.bone().startsWith("weak_"))for(var box:region.boxes()){
                Vec3 to=box.center,away=to.subtract(boss.position()).multiply(1,0,1).normalize();
                if(away.lengthSqr()<.01)continue;Vec3 from=to.add(away.scale(8)).add(0,2,0);
                var hit=ProjectileUtil.getEntityHitResult(f.level,craft,from,to,new AABB(from,to).inflate(.3),e->e.isAlive()&&e.isPickable());
                if(hit==null||!(hit.getEntity() instanceof GreenePart part)||part.slot>=3)continue;
                var shot=new VehicleOrdnanceProjectile(f.level,player,craft,"tank_ap");shot.setPos(from);
                shot.setDeltaMovement(to.subtract(from).normalize().scale(3.2));f.level.addFreshEntity(shot);f.entities.add(shot);
                return;
            }
            h.assertTrue(false,"fixture must expose an actual appendage to the native projectile query");
        });
        h.runAfterDelay(45,()->{try(f){h.assertTrue(damaged(boss),"native AP impact must damage independent appendage armor");}h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="ordnanceBody",timeoutTicks=80)
    public static void hellfireInnerSphereFindsAnAppendageOutsideTheParentRootBox(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,301000);var player=HelicopterPilotGameTest.player(h,f,false);var boss=greene(f);
        h.runAfterDelay(30,()->{
            try(f){
                var point=boss.hurtRegions(0).stream().filter(r->r.bone().startsWith("weak_")).flatMap(r->r.boxes().stream())
                        .map(b->b.center).max(java.util.Comparator.comparingDouble(p->CombatTargeting.distanceSquared(p,boss.getBoundingBox()))).orElseThrow();
                h.assertTrue(CombatTargeting.distanceSquared(point,boss.getBoundingBox())>1.01,"one-unit inner sphere lies outside the ordinary root damage box");
                var blast=HellfireBlastEntity.start(f.level,player,point,new Vec3(0,1,0));h.assertTrue(blast!=null,"real Hellfire blast starts");f.entities.add(blast);
                h.assertTrue(damaged(boss),"inner wave at real limb skin damages that limb before later wider rings");
            }
            h.succeed();
        });
    }
}
