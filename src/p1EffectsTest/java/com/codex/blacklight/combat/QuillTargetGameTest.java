package com.codex.blacklight.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class QuillTargetGameTest {
    @GameTest(template="wall_test", timeoutTicks=40)
    public static void shoulderReacquiresVillagersWhenTheLockedTargetIsHidden(GameTestHelper h) {
        var level = h.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "quill-reacquire"));
        var origin = h.absoluteVec(new Vec3(3.5, 160, 3.5));
        owner.setPos(origin); owner.setYRot(90);
        var state = QuillHarvest.state(owner);
        state.infect(); state.setPrototypeMode(true);
        state.unlock(PowerForm.BACKSPINES); state.activate(PowerForm.BACKSPINES);
        var locked = EntityType.HUSK.create(level);
        var spare = EntityType.VILLAGER.create(level);
        var wall = BlockPos.containing(origin.add(0, 0, 2));
        try {
            locked.moveTo(origin.x, origin.y, origin.z + 5, 0, 0);
            spare.moveTo(origin.x + 4, origin.y, origin.z, 0, 0);
            level.addFreshEntity(locked); level.addFreshEntity(spare);
            CombatTargeting.requestLock(owner, locked.getId());
            h.assertTrue(CombatTargeting.locked(owner)==locked,"expected an explicit hostile lock");
            for (int x=-1; x<=1; x++) for (int y=0; y<4; y++)
                level.setBlockAndUpdate(wall.offset(x,y,0), Blocks.STONE.defaultBlockState());
            for (int i=0; i<80; i++) {
                if (i == 40) {
                    int layers = com.codex.blacklight.config.GameplayBalanceConfig.QUILL_LAYERS.get();
                    for (int n = 0; n <= layers; n++)
                        QuillHarvest.plant(owner, spare, spare.getBoundingBox().getCenter(), 0);
                    h.assertTrue(QuillHarvest.mark(spare).getInt("layers") == layers, "mark cap fixture");
                }
                var aim=QuillHarvest.attackTarget(owner, 8, false);
                h.assertTrue(aim!=null && aim.target()==spare,"missing visible neutral on shot "+i);
            }
            h.assertTrue(QuillHarvest.attackTarget(owner,8,true)==null,"passive orbit must leave the villager alone");
            for (int x=-1; x<=1; x++) for (int y=0; y<4; y++)
                level.removeBlock(wall.offset(x,y,0),false);
            h.assertTrue(QuillHarvest.attackTarget(owner,8,false).target()==locked,"visible explicit lock must regain priority");
            locked.discard();
            owner.setOnGround(true); owner.setXRot(0); state.addBiomass(80);
            BackspineCombat.input(owner,true,false);
            BackspineCombat.input(owner,false,false);
            for(int i=0;i<14;i++){owner.tickCount++;BackspineCombat.tick(owner);}
            var shots=level.getEntitiesOfClass(com.codex.blacklight.entity.BackspineQuill.class,
                    owner.getBoundingBox().inflate(5), shot -> shot.getOwner()==owner);
            h.assertTrue(!shots.isEmpty(),"no physical quill launched");
            for(var shot:shots){
                var expected=spare.getBoundingBox().getCenter().subtract(shot.position()).normalize();
                h.assertTrue(shot.getDeltaMovement().normalize().dot(expected)>.8,"real shoulder shot followed the crosshair");
                shot.discard();
            }
            spare.setInvisible(true);
            h.assertTrue(QuillHarvest.attackTarget(owner,8,false)==null,"invisible target must not be acquired");
            h.succeed();
        } finally {
            CombatTargeting.requestLock(owner,-1);
            for (var organ : level.getEntitiesOfClass(com.codex.blacklight.entity.QuillOrgan.class,
                    owner.getBoundingBox().inflate(10), q -> owner.getUUID().equals(q.owner()))) organ.discard();
            locked.discard(); spare.discard();
            for (int x=-1; x<=1; x++) for (int y=0; y<4; y++)
                level.removeBlock(wall.offset(x,y,0),false);
        }
    }
}
