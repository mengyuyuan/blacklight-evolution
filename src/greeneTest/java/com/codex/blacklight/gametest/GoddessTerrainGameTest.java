package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoddessTerrainGameTest {
    static final class Arena implements AutoCloseable {
        // Forge places different batches over the same structure. Keep their manually excavated lanes independent.
        private static int arenaIndex;
        final ServerLevel level;
        final EncounterMob boss;
        final Vec3 base;
        final List<ServerPlayer> players=new ArrayList<>();
        Arena(GameTestHelper h){
            this(h,h.absolutePos(new BlockPos(8,23+16*(arenaIndex++%14),8)));
        }
        Arena(GameTestHelper h,BlockPos site){
            level=h.getLevel();base=Vec3.atBottomCenterOf(site);
            for(int x=-6;x<=37;x++)for(int z=-16;z<=16;z++)level.setBlockAndUpdate(site.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            boss=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
            boss.moveTo(base.x,base.y,base.z,0,0); boss.startGreeneOutbreak();boss.setNoAi(true);
            var state=boss.greeneBattle().save();state.putInt("WaveCount",2);state.putInt("ArmourWave",2);state.putBoolean("OpeningPending",false);
            boss.greeneBattle().load(state); level.addFreshEntity(boss);
        }
        ServerPlayer player(String name,Vec3 at){
            var profile=new GameProfile(UUID.randomUUID(),name);
            var p=new ServerPlayer(level.getServer(),level,profile);
            p.connection=FakePlayerFactory.get(level,profile).connection;
            for(int i=0;i<61;i++)p.tick();
            p.setPos(at);level.addNewPlayer(p);p.setGameMode(GameType.SURVIVAL);
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
            p.setHealth(p.getMaxHealth());players.add(p); return p;
        }
        void begin(GoddessAttacks.Move move,ServerPlayer target,int seq){
            var tag=boss.goddess.attacks.save();tag.putString("Move",move.name());tag.putInt("Sequence",seq);
            tag.putUUID("Target",target.getUUID());tag.putInt("Age",0);tag.remove("Terrain");
            boss.goddess.attacks.load(tag);
        }
        void tick(int start,int end){for(int age=start;age<=end;age++){
            for(var p:players)p.invulnerableTime=0;
            boss.goddess.attacks.tick();
        }}
        public void close(){for(var p:players)level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);boss.discard();}
    }
    @GameTest(template="blackwatch_arena",batch="goddess_terrain",timeoutTicks=90)
    public static void sonicFrontUsesFrozenMouthAndStoneCover(GameTestHelper h){
        try(var a=new Arena(h)){
            var target=a.player("PitchTarget",a.base.add(32,0,0));
            var near=a.player("PitchNear",a.base.add(28,0,1));
            var far=a.player("PitchWall",a.base.add(30,0,3));
            near.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000);near.setHealth(near.getMaxHealth());
            var move=GoddessAttacks.Move.SONIC_CRY;a.begin(move,target,70);
            a.tick(1,GoddessTerrainCast.PING);
            var cast=a.boss.goddess.attacks.terrain;
            var mouth=cast.mouth(0);var axis=cast.axis(0);
            near.setPos(mouth.add(axis.scale(12)).add(0,-.9,0));
            far.setPos(mouth.add(axis.scale(22)).add(0,-.9,0));
            var wall=BlockPos.containing(mouth.add(axis.scale(17)));
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
                a.level.setBlockAndUpdate(wall.offset(x,y,z),Blocks.DEEPSLATE.defaultBlockState());
            float start=near.getHealth(),sheltered=far.getHealth();
            a.tick(16,25);
            h.assertTrue(Math.abs(near.getHealth()-start*.94F)<.02 && far.getHealth()==sheltered,"near actor hears first front, solid stone shields second actor");
            float after=near.getHealth();
            a.boss.goddess.attacks.load(a.boss.goddess.attacks.save());
            near.setPos(mouth.add(axis.scale(54)).add(0,-.9,0)); a.tick(26,40);
            h.assertTrue(near.getHealth()==after,"one loss per sound even after loading");
            target.setPos(a.base.add(-25,0,-23)); a.tick(41,44);
            var second=cast.axis(1);
            h.assertTrue(axis.dot(second)<.92,"queen turns for slow howl");
            target.setPos(a.base.add(35,0,0));near.setPos(cast.mouth(1).add(second.scale(15)).add(0,-.9,0));
            a.tick(45,54);
            h.assertTrue(Math.abs(near.getHealth()-start*.80F)<.02 && cast.axis(1).distanceToSqr(second)<1e-8,"second howl independently strikes on its original heading");
            a.begin(move,target,71);a.tick(1,GoddessTerrainCast.PING);
            Vec3 replay=cast.mouth(0),direction=cast.axis(0);
            Vec3 coverPoint=replay.add(direction.scale(17));
            var pillar=BlockPos.containing(coverPoint);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
                a.level.setBlockAndUpdate(pillar.offset(x,y,z),Blocks.DEEPSLATE.defaultBlockState());
            Vec3 protectedPoint=replay.add(direction.scale(22));
            h.assertTrue(!cast.clear(replay,protectedPoint),"a closed pillar occludes the actual line of fire");
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
                a.level.setBlockAndUpdate(pillar.offset(x,y,z),Blocks.AIR.defaultBlockState());
            var beyond=a.player("VoiceReach",replay.add(direction.scale(26)).add(0,-.9,0));
            far.setPos(protectedPoint.add(0,-.9,0));a.tick(16,26);
            h.assertTrue(Math.abs(far.getHealth()-sheltered*.94F)<.02 && beyond.getHealth()==beyond.getMaxHealth(),
                    "mining the stone opens the cast; a target outside the short call radius stays untouched");
            System.out.println("GODDESS_SONIC near="+start+"->"+after+"->"+near.getHealth()+" sealed="+sheltered+" dug="+far.getHealth()+" outside24="+beyond.getHealth()+" frozen=true");
        }
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="goddess_ground",timeoutTicks=90)
    public static void spikesHaveWalkableGapsAndWaveCanBeJumped(GameTestHelper h){
        try(var a=new Arena(h)){
            var target=a.player("SpikeTarget",a.base.add(30,0,0));
            var stand=a.player("SpikeFoot",a.base.add(6,0,0));
            var gap=a.player("SpikeGap",a.base.add(6,0,3.8));
            stand.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);stand.setHealth(stand.getMaxHealth());
            a.begin(GoddessAttacks.Move.SPIKE_FIELD,target,80);a.tick(1,17);
            float full=stand.getHealth(),safe=gap.getHealth(); a.tick(18,24);
            h.assertTrue(Math.abs(stand.getHealth()-full*.90F)<.02 && gap.getHealth()==safe,"announced ground spike hits stone lane and leaves a walking gap");
            var ridge=a.boss.goddess.attacks.terrain.spike(6,3);
            var cell=BlockPos.containing(ridge);
            a.level.setBlockAndUpdate(cell,Blocks.DEEPSLATE.defaultBlockState());
            a.level.setBlockAndUpdate(cell.above(),Blocks.DEEPSLATE.defaultBlockState());
            var crest=a.boss.goddess.attacks.terrain.surface(ridge);
            h.assertTrue(crest!=null && Math.abs(crest.y-a.base.y-2)<.03,"spike root uses the rebuilt ledge top");
            for(int y=2;y>=-18;y--)a.level.setBlockAndUpdate(cell.offset(0,y,0),Blocks.AIR.defaultBlockState());
            h.assertTrue(a.boss.goddess.attacks.terrain.surface(ridge)==null,"excavated 18-block trench has no free-floating spike");
            var aerial=a.player("SpineHeight",a.boss.goddess.attacks.terrain.spike(4,3).add(0,5,0));
            a.tick(25,36);
            h.assertTrue(aerial.getHealth()==aerial.getMaxHealth(),"actor above the real four-block crown clears the eruption");
            var jumped=a.player("WaveJump",a.base.add(17,1.45,0));
            var covered=a.player("WaveCover",a.base.add(0,0,12));
            var wall=BlockPos.containing(a.base.add(0,0,7));
            for(int x=-2;x<=2;x++)for(int y=0;y<4;y++)a.level.setBlockAndUpdate(wall.offset(x,y,0),Blocks.STONE.defaultBlockState());
            stand.setPos(a.base.add(17,0,0));
            var common=ModEntities.COMMON_INFECTED.get().create(a.level);common.moveTo(a.base.x+14,a.base.y,a.base.z+2,0,0);a.level.addFreshEntity(common);
            try{
                a.begin(GoddessAttacks.Move.INFECTION_WAVE,target,81);float before=stand.getHealth();
                a.tick(1,35);h.assertTrue(stand.getHealth()==before,"front has not reached 17 blocks");
                a.tick(36,45);
                h.assertTrue(Math.abs(stand.getHealth()-before+full*.12F)<.02 && jumped.getHealth()==jumped.getMaxHealth() && covered.getHealth()==covered.getMaxHealth(),"jump and current rock both clear the advancing crest");
                h.assertTrue(common.getDeltaMovement().horizontalDistance()>.5,"infected crowd physically reveals the crest");
                System.out.println("GODDESS_GROUND lane="+full+"->"+before+" wave="+stand.getHealth()+" gap="+safe+" jump="+jumped.getHealth()+" covered="+covered.getHealth());
            }finally{common.discard();}
        }
        h.succeed();
    }
}
