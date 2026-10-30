package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.movement.PrototypeMovementSystem;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ReleaseAuditGameTest {
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void enormousAttackQueriesOnlyLoadedActors(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"audit-query"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3)));
        var target=h.spawn(EntityType.PIG,4,2,3);
        var query=p.getBoundingBox().inflate(1_000_000_000D);
        var found=CombatTargeting.candidates(p,query,e->e==target);
        h.assertTrue(found.size()==1&&found.get(0)==target,"uncapped radius retains loaded target without iterating empty chunks");
        h.assertTrue(CombatTargeting.candidates(p,query,e->false).isEmpty(),"large query must retain predicate");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void longSightRayDoesNotLoadMissingTerrain(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"audit-sight"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3)));
        var to=p.position().add(1_000_000,0,0);
        int x=((int)to.x)>>4,z=((int)to.z)>>4;
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x,z)==null,"fixture must start unloaded");
        h.assertTrue(!CombatTargeting.visible(p,p.getEyePosition(),new AABB(to,to.add(1,1,1))),"unloaded gap occludes sight");
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x,z)==null,"ray must not generate destination");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void flyingInputCannotArmTraversal(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"audit-flight"));
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        p.getAbilities().flying=true;p.setOnGround(true);
        PrototypeMovementSystem.acceptInput(p,true,true,1,0,true,false);
        h.assertTrue(!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).jumpHeld(),"flying input must be rejected before edge mutation");
        p.getAbilities().flying=false;h.succeed();
    }
}
