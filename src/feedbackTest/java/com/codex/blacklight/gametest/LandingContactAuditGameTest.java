package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.InfectionRules;
import com.codex.blacklight.movement.*;
import com.codex.blacklight.power.PowerForm;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class LandingContactAuditGameTest {
    @GameTest(template="wall_test",timeoutTicks=40,batch="audit_land_veto")
    public static void aCancelledLandingHitDoesNotApplyItsKnockback(GameTestHelper h){check(h,0);}
    @GameTest(template="wall_test",timeoutTicks=40,batch="audit_land_wall")
    public static void anOrdinaryLandingCannotHitThroughASolidWall(GameTestHelper h){check(h,1);}
    @GameTest(template="wall_test",timeoutTicks=40,batch="audit_land_invulnerable")
    public static void anInvulnerableActorIsNotPushedByALandingAttack(GameTestHelper h){check(h,2);}
    private static void check(GameTestHelper h,int kind){
        var p=MovingCombatGameTest.player(h,PowerForm.CLAWS);p.setPos(h.absoluteVec(new Vec3(5,180,5)));
        var target=EntityType.COW.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        target.setPos(p.position().add(0,0,kind==1?3:1.5));target.setInvulnerable(kind==2);
        target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);h.getLevel().addFreshEntity(target);
        var wall=p.blockPosition().offset(0,0,1);
        if(kind==1)for(int x=-2;x<=2;x++)for(int y=0;y<=4;y++)h.getLevel().setBlock(wall.offset(x,y,0),Blocks.STONE.defaultBlockState(),2);
        Consumer<LivingHurtEvent> veto=e->{if(kind==0&&e.getEntity()==target)e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(veto);
        try{
            if(kind==1)h.assertTrue(!p.hasLineOfSight(target),"the fixture really occludes the victim");
            PrototypeMovementEffects.onLanding(p,new LandingImpact.Observation(1.5,20));
            h.assertTrue(target.getHealth()==10000&&target.getDeltaMovement().lengthSqr()<1e-10,
                    "protected/occluded landing victim keeps its health and velocity: hp="+target.getHealth()+" v="+target.getDeltaMovement());
            h.succeed();
        }finally{
            MinecraftForge.EVENT_BUS.unregister(veto);target.discard();p.discard();
            if(kind==1)for(int x=-2;x<=2;x++)for(int y=0;y<=4;y++)h.getLevel().setBlock(wall.offset(x,y,0),Blocks.AIR.defaultBlockState(),2);
        }
    }
}
