package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoddessCrownGameTest {
    @GameTest(template="blackwatch_arena",batch="goddess_crown",timeoutTicks=90)
    public static void invertedCheekIsReachableWithCuttableSpitters(GameTestHelper h){
        try(var a=new GoddessTerrainGameTest.Arena(h)){
            var p=a.player("AcidFighter",a.base.add(23,0,0));
            var boss=a.boss;
            var cast=boss.goddess.attacks.crown;
            var move=GoddessAttacks.Move.INVERTED_ACID;
            for(int mask:new int[]{0,1,3}){
                var body=boss.goddess.save();body.putInt("Removed",mask);boss.goddess.load(body);
                a.begin(move,p,400+mask);a.tick(1,32);
                var rows=GoddessRig.regions(boss,boss.skinOrigin(0),0);
                var inner=rows.get(GoddessCrownCast.FIRST_ROW);
                h.assertTrue(inner.boxes().size()==39 && rows.get(GoddessCrownCast.FIRST_ROW+1).boxes().size()==7,"46 real hair actors split into independent receivers");
                Vec3 point=inner.boxes().get(0).center;
                var hit=new HydraHurtContact.Contact(inner.bone(),point,new Vec3(0,1,0),InfectedAttackStyle.TEAR);
                h.assertTrue(boss.hurtAt(p.damageSources().playerAttack(p),3,hit),"ordinary player hit interrupts charged row");
                h.assertTrue(!cast.interrupt(GoddessCrownCast.FIRST_ROW),"a row cannot break twice");
                boss.goddess.attacks.load(boss.goddess.attacks.save());
                a.tick(33,GoddessCrownCast.SPIT_A);
                h.assertTrue(cast.globs().isEmpty(),"cut row remains stopped across save/load");
                a.tick(GoddessCrownCast.SPIT_A+1,GoddessCrownCast.SPIT_B);
                h.assertTrue(cast.globs().size()==5 && cast.globs().stream().allMatch(g->g.row==1),"uncut row still fires five physical arcs");
                for(var glob:cast.globs()){
                    var middle=glob.point(glob.start+GoddessCrownCast.FLIGHT/2);
                    h.assertTrue(middle.y>glob.from.lerp(glob.to,.5).y+.45,"spit has a raised center of flight");
                }
                a.tick(GoddessCrownCast.SPIT_B+1,GoddessCrownCast.OPEN);
                float beginning=boss.goddess.attacks.age(0);
                for(int age=GoddessCrownCast.OPEN;age<GoddessCrownCast.RISE;age++){
                    var skull=GoddessRig.regions(boss,boss.skinOrigin(0),0).get(0);
                    double reach=skull.boxes().stream().map(box->box.nearest(p.getEyePosition())).mapToDouble(p.getEyePosition()::distanceTo).min().orElse(999);
                    h.assertTrue(reach<3.1 && cast.globs().stream().noneMatch(g->g.visible(boss.goddess.attacks.age(0))),
                            "ordinary grounded reach and clear ground opening, reach="+reach+" age="+age+" mask="+mask);
                    a.tick(age+1,age+1);
                }
                h.assertTrue(boss.goddess.attacks.age(0)-beginning==60,"three full seconds after last acid");
                a.tick(GoddessCrownCast.RISE+1,GoddessCrownCast.ACID_END);
                h.assertTrue(boss.goddess.attacks.move()!=move && !cast.acting(),"returns to combat at authored deadline");
            }
            System.out.println("GODDESS_CROWN ground reach, two lockable rows, reload, 60 safe ticks, masks 0/1/3");
        }
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="goddess_crown_burst",timeoutTicks=90)
    public static void eruptionLeavesGapsAndCannotPassAWall(GameTestHelper h){
        // Place both receivers inside the structure's central entity chunk, above previous test floors.
        var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(8,0,8)));
        var site=new BlockPos(chunk.getMinBlockX()+8-30,h.getLevel().getMaxBuildHeight()-48,chunk.getMinBlockZ()+4);
        try(var a=new GoddessTerrainGameTest.Arena(h,site)){
            var target=a.player("BurstTarget",a.base.add(30,0,0));
            var cast=a.boss.goddess.attacks.crown;
            a.begin(GoddessAttacks.Move.TENDRIL_ERUPTION,target,700);a.tick(1,26);
            Vec3 start=cast.base(0);
            var lane=cast.strand(start,0,41,false);
            var neighbor=cast.strand(start,1,41,false);
            Vec3 point=lane[8],gap=point.lerp(neighbor[8],.5);
            var inLane=a.player("BurstHit",point.add(0,-.8,0));
            var inGap=a.player("BurstGap",gap.add(0,-.8,0));
            for(var witness:java.util.List.of(inLane,inGap)){
                h.assertTrue(witness.chunkPosition().equals(chunk)
                                && a.level.getEntity(witness.getUUID())==witness
                                && a.level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,witness.getBoundingBox()).contains(witness),
                        "collision witness indexed in structure chunk "+chunk+" at "+witness.position());
            }
            h.assertTrue(cast.strand(start,0,41,true).length==lane.length,"clear line from root to witnessed strand");
            float health=inLane.getHealth(),spared=inGap.getHealth();
            a.tick(27,46);
            h.assertTrue(inLane.getHealth()<health,"physical strand hits indexed receiver, health="+inLane.getHealth()+" at "+point);
            h.assertTrue(inGap.getHealth()==spared,"adjacent gap spares indexed receiver, health="+inGap.getHealth()+" at "+gap);
            float once=inLane.getHealth();
            a.boss.goddess.attacks.load(a.boss.goddess.attacks.save());a.tick(47,52);
            h.assertTrue(inLane.getHealth()==once,"one eruption hit per player including reload");
            var wall=BlockPos.containing(lane[6]);var old=a.level.getBlockState(wall);
            try{
                a.level.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
                h.assertTrue(cast.strand(start,0,48,true).length<lane.length,"placed block truncates a physical strand");
            }finally{a.level.setBlockAndUpdate(wall,old);}
            a.tick(53,73);
            h.assertTrue(cast.strand(start,0,73,false)[10].distanceTo(start)<5,"all bundles retract to body");
            System.out.println("GODDESS_CROWN eruption="+health+"->"+once+" gap="+spared+" persisted=true");
        }
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="goddess_acid_cover",timeoutTicks=90)
    public static void acidDebitsOnePercentagePerRowAndHitsTerrain(GameTestHelper h){
        var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(8,0,8)));
        var site=new BlockPos(chunk.getMinBlockX()+8-28,h.getLevel().getMaxBuildHeight()-100,chunk.getMinBlockZ()+8);
        try(var a=new GoddessTerrainGameTest.Arena(h,site)){
            var target=a.player("AcidAim",a.base.add(22,0,0));
            var cast=a.boss.goddess.attacks.crown;
            a.begin(GoddessAttacks.Move.INVERTED_ACID,target,580);
            a.tick(1,GoddessCrownCast.SPIT_A-6);
            target.setPos(a.base.add(32,0,0));a.tick(GoddessCrownCast.SPIT_A-5,GoddessCrownCast.SPIT_A);
            var shots=cast.globs();
            var intercepted=shots.get(0);var missile=shots.get(4);
            var obstruction=BlockPos.containing(intercepted.point(GoddessCrownCast.SPIT_A+6));
            var old=a.level.getBlockState(obstruction);
            try{
                a.level.setBlockAndUpdate(obstruction,Blocks.DEEPSLATE.defaultBlockState());
                a.tick(1,7);
                int age=GoddessCrownCast.SPIT_A+8;
                var witness=a.player("SpitReceiver",missile.point(age).add(0,-.65,0));
                var health=witness.getHealth();
                h.assertTrue(a.level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,witness.getBoundingBox()).contains(witness),
                        "first acid receiver indexed at "+witness.position());
                h.assertTrue(missile.visible(age-1),"first projectile is still in flight");
                a.tick(8,8);
                h.assertTrue(!intercepted.visible(age),"placed rock splashes first acid arc");
                h.assertTrue(!missile.visible(age) && Math.abs(witness.getHealth()-health*.92)<.01,
                        "first real body collision costs 8%, actual="+witness.getHealth());
                a.boss.goddess.attacks.load(a.boss.goddess.attacks.save());
                var second=cast.globs().get(2);
                h.assertTrue(second.visible(age),"second projectile survived until reload");
                witness.setPos(second.point(age+1).add(0,-.65,0));
                h.assertTrue(a.level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,witness.getBoundingBox()).contains(witness),
                        "second acid receiver indexed at "+witness.position());
                a.tick(9,9);
                h.assertTrue(!second.visible(age+1) && Math.abs(witness.getHealth()-health*.92)<.01,
                        "second physical collision after reload cannot debit same row again, actual="+witness.getHealth());
                System.out.println("GODDESS_CROWN acid="+health+"->"+witness.getHealth()+" wall=true secondContact=true");
            }finally{a.level.setBlockAndUpdate(obstruction,old);}
        }
        h.succeed();
    }

    private GoddessCrownGameTest(){}
}
