package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeGiverEffectTest {
    @Test void bodyStartsAtItsOwnSourceCallback(){
        assertEquals(55/30F,ConsumeGiverEffect.cue(PowerForm.NONE,ConsumeVariant.DEFAULT),.00001);
        assertEquals(130/30F,ConsumeGiverEffect.cue(PowerForm.CLAWS,ConsumeVariant.CLASSIC),.00001);
        assertEquals(88/30F,ConsumeGiverEffect.cue(PowerForm.NONE,ConsumeVariant.BEATDOWN),.00001);
        assertTrue(Float.isNaN(ConsumeGiverEffect.cue(PowerForm.NONE,ConsumeVariant.STEALTH_NECK)));
    }
    @Test void theActorMaterialReversesIndependentlyOfTheReceiver(){
        assertEquals(0,ConsumeGiverEffect.reach(-.01F));
        assertEquals(.35F,ConsumeGiverEffect.reach(1),1e-5F);
        assertEquals(2.3F*.35F,ConsumeGiverEffect.reach(2.3F),1e-5F);
        assertEquals(ConsumeGiverEffect.reach(2.0F),ConsumeGiverEffect.reach(2.6F),1e-5F);
        assertEquals(0,ConsumeGiverEffect.reach(ConsumeGiverEffect.END_SECONDS));
    }

    @Test void exteriorEnclosesTheLiveReceiverAndFollowsTheSameClockAcrossHandoff() {
        for(var form:new PowerForm[]{PowerForm.NONE, PowerForm.CLAWS, PowerForm.BLADE, PowerForm.MUSCLE_MASS,
                PowerForm.WHIPFIST, PowerForm.HAMMERFISTS}) for(int variant:ConsumeVariant.variants(form)) {
            var scene=new ConsumeVisual(17,80,ConsumeSequence.duration(form,false,variant),form.ordinal(),
                    ConsumeVisual.CONSUME,0,net.minecraft.world.phys.Vec3.ZERO,variant);
            float start=ConsumeGiverEffect.exteriorStart(scene);
            float finish=ConsumeSequence.completionTick(form,false,variant)/20F;
            assertTrue(Float.isFinite(start) && start+.28F<finish, form+" "+variant);
            assertEquals(0,ConsumeGiverEffect.exterior(scene,start));
            assertEquals(1,ConsumeGiverEffect.exterior(scene,finish-.05F),.0001F);
            assertEquals(ConsumeGiverEffect.exterior(scene,start+.13F),ConsumeGiverEffect.exterior(scene.mobileTail(),start+.13F));
            assertTrue(ConsumeGiverEffect.exterior(scene,finish+.25F)>0);
            assertEquals(0,ConsumeGiverEffect.exterior(scene,finish+.76F));
            assertEquals(0,ConsumeGiverEffect.residue(scene, start));
            assertTrue(ConsumeGiverEffect.residue(scene,finish)>.85F);
            assertTrue(ConsumeGiverEffect.residue(scene,finish+1F)>.7F);
            assertTrue(ConsumeGiverEffect.residue(scene,finish+2F)>.75F);
            assertEquals(0,ConsumeGiverEffect.residue(scene,ConsumeGiverEffect.residueEnd(scene)+.01F));
            assertTrue(ConsumeGiverEffect.bodyEnd(scene)>=ConsumeGiverEffect.residueEnd(scene));
        }
    }
    @Test void powersUsingTheOrdinaryReceiverClipAlsoGetItsExterior() {
        for (var form : PowerForm.values()) {
            if (!ConsumeSequence.key(form).equals("normal") || form == PowerForm.BACKSPINES) continue;
            var scene = new ConsumeVisual(4, 100, 80, form.ordinal());
            assertTrue(ConsumeGiverEffect.exterior(scene, 2.0F) > .9F, form.toString());
        }
    }
    @Test void holdMountedAndHunterStagesNeverRequestTheSmallPreyWrap() {
        for(int stage:new int[]{ConsumeVisual.HOLD,ConsumeVisual.WHIP_PULL,ConsumeVisual.MOUNT_CONSUME,ConsumeVisual.HUNTER_CONSUME}){
            var scene=new ConsumeVisual(17,80,60,PowerForm.WHIPFIST.ordinal(),stage);
            assertFalse(Float.isFinite(ConsumeGiverEffect.exteriorStart(scene)));
            assertEquals(0,ConsumeGiverEffect.exterior(scene,2));
        }
    }
}
