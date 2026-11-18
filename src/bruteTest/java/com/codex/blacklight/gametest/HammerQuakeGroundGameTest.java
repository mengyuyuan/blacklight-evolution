package com.codex.blacklight.gametest;
import com.codex.blacklight.combat.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HammerQuakeGroundGameTest {
    private static void check(GameTestHelper h,WeaponMove move,int terrain){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,terrain==2?Blocks.AIR:Blocks.STONE);for(int y=2;y<10;y++)h.setBlock(x,y,z,Blocks.AIR);
            if(terrain==1)h.setBlock(x,2,z,Blocks.STONE_SLAB);
        }
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(java.util.UUID.randomUUID(),"hammer-ground"));
        p.setPos(h.absoluteVec(new Vec3(7.5,terrain==1?2.5:2,7.5)));p.setYRot(0);p.setOnGround(terrain!=3);
        BlockHitResult hit=null;int when=-1;
        for(int tick=move.start;tick<move.end;tick++){
            var contact=HammerQuakeContact.sweep(p,move,tick,0,p.position(),0,0);
            if(contact!=null){hit=contact;when=tick;break;}
        }
        if(terrain>=2)h.assertTrue(hit==null,"air/unsupported motion cannot emit ground impact");
        else{
            h.assertTrue(hit!=null,"scaled projected fist reaches real surface "+move+" terrain="+terrain);
            h.assertTrue(Math.abs(hit.getLocation().y-p.getY())<.001,"ground result lies on actual top face");
            float seconds=when/20F-LocalWeaponMotion.find(move.id).orElseThrow().transitionSeconds();
            var pose=HammerQuakeContact.projectedPose(p,move,seconds,p.position(),0);
            h.assertTrue(HammerGroundPose.minimum(pose,false,WeaponAttackScales.sample(move.id,seconds,false))>=.011F,"server contact pose cannot sink through floor");
        }
        h.succeed();
    }

    private static void irregularSurface(GameTestHelper h,int terrain){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            for(int y=0;y<10;y++)h.setBlock(x,y,z,Blocks.AIR);
            if(terrain!=2||z<9)h.setBlock(x,1,z,Blocks.STONE);
            if(terrain==0&&z>=9)h.setBlock(x,2,z,Blocks.STONE_SLAB);
            if(terrain==1&&x==6&&z>=8&&z<=10)h.setBlock(x,2,z,Blocks.STONE);
        }
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(java.util.UUID.randomUUID(),"hammer-terrain"));
        p.setPos(h.absoluteVec(new Vec3(7.5,2,7.5)));p.setYRot(0);p.setOnGround(true);
        double worst=0;String detail="";
        for(var move:new WeaponMove[]{WeaponMove.HAMMER_QUAKE,WeaponMove.HAMMER_QUAKE_CHARGED}){
            float duration=com.codex.blacklight.animation.MotionLibrary.duration(move.id);
            for(float seconds=0;seconds<duration;seconds+=1/30F){
                var pose=HammerQuakeContact.projectedPose(p,move,seconds,p.position(),0);
                var world=new org.joml.Matrix4f().rotateY((float)Math.PI).scale(.9375F*p.getScale());
                for(boolean left:new boolean[]{false,true})for(var vertex:HammerGroundPose.fist(pose,WeaponAttackScales.sample(move.id,seconds,left),left)){
                    var v=world.transformPosition(vertex);var at=p.position().add(v.x,v.y,v.z);
                    var pos=net.minecraft.core.BlockPos.containing(at);
                    for(var box:p.level().getBlockState(pos).getCollisionShape(p.level(),pos).toAabbs()){
                        var b=box.move(pos);if(!b.contains(at))continue;
                        double depth=Math.min(Math.min(at.x-b.minX,b.maxX-at.x),Math.min(Math.min(at.y-b.minY,b.maxY-at.y),Math.min(at.z-b.minZ,b.maxZ-at.z)));
                        if(depth>worst){worst=depth;detail=move.id+" t="+seconds+" left="+left+" at="+at;}
                    }
                }
            }
        }
        h.assertTrue(worst<.02,"fist cannot enter terrain="+terrain+" depth="+worst+" "+detail);
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="hammer_ground_front_step",timeoutTicks=40) public static void entireStrokeClearsFrontSlab(GameTestHelper h){irregularSurface(h,0);}
    @GameTest(template="blackwatch_arena",batch="hammer_ground_side_step",timeoutTicks=40) public static void entireStrokeClearsSideBlock(GameTestHelper h){irregularSurface(h,1);}
    @GameTest(template="blackwatch_arena",batch="hammer_ground_ledge",timeoutTicks=40) public static void entireStrokeClearsLedge(GameTestHelper h){irregularSurface(h,2);}
    @GameTest(template="blackwatch_arena",batch="hammer_ground_plain",timeoutTicks=40) public static void ordinaryPunchHitsProjectedMesh(GameTestHelper h){check(h,WeaponMove.HAMMER_QUAKE,0);}
    @GameTest(template="blackwatch_arena",batch="hammer_ground_slab",timeoutTicks=40) public static void chargedPunchUsesSlabTop(GameTestHelper h){check(h,WeaponMove.HAMMER_QUAKE_CHARGED,1);}
    @GameTest(template="blackwatch_arena",batch="hammer_ground_void",timeoutTicks=40) public static void noSolidFaceMeansNoWave(GameTestHelper h){check(h,WeaponMove.HAMMER_QUAKE_CHARGED,2);}
    @GameTest(template="blackwatch_arena",batch="hammer_ground_air",timeoutTicks=40) public static void airbornePoseCannotHitThroughFloor(GameTestHelper h){check(h,WeaponMove.HAMMER_QUAKE_CHARGED,3);}
}
