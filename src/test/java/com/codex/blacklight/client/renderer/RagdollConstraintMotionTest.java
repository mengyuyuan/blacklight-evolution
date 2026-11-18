package com.codex.blacklight.client.renderer;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollConstraintMotionTest {
    @Test void aSuspendedHeadCannotStoreTheFallRejectedByItsTorso(){
        var before=new Vec3(.12,-.8,.07);
        var after=RagdollConstraintMotion.linear(before,new Vec3(0,.02,0));
        assertEquals(0,new Vec3(.12,0,.07).distanceTo(after),1e-12);
        assertTrue(after.lengthSqr()<=before.lengthSqr());
        // Joint/terrain repair is not an upward launch, even if it is large.
        assertEquals(Vec3.ZERO,RagdollConstraintMotion.linear(Vec3.ZERO,new Vec3(0,.6,0)));
        var leaving=new Vec3(0,.3,0);
        assertEquals(leaving,RagdollConstraintMotion.linear(leaving,new Vec3(0,.02,0)));
    }
    @Test void aStepOrWallOnlyRemovesMotionIntoItsCorrection(){
        var corrections=List.of(new Vec3(.08,0,0),new Vec3(0,.051,0),new Vec3(-.078,.021,0));
        for(var correction:corrections)for(var velocity:List.of(new Vec3(-.5,-.3,.2),new Vec3(.4,-.2,-.1),Vec3.ZERO)){
            var result=RagdollConstraintMotion.linear(velocity,correction);
            assertTrue(result.lengthSqr()<=velocity.lengthSqr()+1e-12,"a correction cannot add kinetic energy");
            assertTrue(result.dot(correction)>=-1e-12,"no retained velocity points into the rejected contact");
        }
        var slide=new Vec3(0,0,.3);
        assertEquals(slide,RagdollConstraintMotion.linear(slide,new Vec3(0,.021,0)));
    }
    @Test void anAngularLimitCannotKeepDrivingARejectedRotation(){
        var predicted=new Quaternionf().rotationZ(.25F);var corrected=new Quaternionf();
        var blocked=RagdollConstraintMotion.angular(new Vector3f(0,0,.5F),predicted,corrected);
        assertEquals(0,blocked.length(),1e-6);
        var free=new Vector3f(.1F,-.2F,.3F);
        assertEquals(0,RagdollConstraintMotion.angular(free,predicted,predicted).distance(free),1e-6);
        // Equivalent quaternion signs must choose the same correction direction.
        var opposite=new Quaternionf(-corrected.x,-corrected.y,-corrected.z,-corrected.w);
        assertEquals(0,RagdollConstraintMotion.angular(new Vector3f(0,0,.5F),predicted,opposite).length(),1e-6);
    }
    @Test void supportCrossesRealJointsButCannotCrossASeveredGap(){
        var links=List.of(new RagdollConstraintMotion.Link(0,1),new RagdollConstraintMotion.Link(1,2));
        assertArrayEquals(new boolean[]{true,true,true,false},RagdollConstraintMotion.supported(new boolean[]{true,false,false,false},links));
        assertArrayEquals(new boolean[]{true,false,false,false},RagdollConstraintMotion.supported(new boolean[]{true,false,false,false},List.of(new RagdollConstraintMotion.Link(1,2))));
        assertArrayEquals(new boolean[]{false,false,false,false},RagdollConstraintMotion.supported(new boolean[4],links));
    }
    @Test void supportedHeadSettlesAndLosingTheFloorWakesItsWholeComponent(){
        var links=List.of(new RagdollConstraintMotion.Link(0,1));var turn=new Quaternionf();
        var supported=RagdollConstraintMotion.supported(new boolean[]{true,false},links);
        int still=0;Vec3 velocity=Vec3.ZERO;
        for(int step=0;step<RagdollConstraintMotion.SLEEP_STEPS;step++){
            velocity=velocity.scale(.975).add(0,-.04,0);
            // The connected pose returns the suspended head to the same place.
            velocity=RagdollConstraintMotion.linear(velocity,velocity.scale(-.5));
            assertEquals(0,velocity.length(),1e-12,"the rejected downward speed must not accumulate between half-steps");
            still=RagdollConstraintMotion.still(still,supported[1],Vec3.ZERO,turn,turn,velocity,new Vector3f());
        }
        assertEquals(RagdollConstraintMotion.SLEEP_STEPS,still);
        var unsupported=RagdollConstraintMotion.supported(new boolean[2],links);
        for(boolean contact:unsupported)assertEquals(0,RagdollConstraintMotion.still(still,contact,Vec3.ZERO,turn,turn,velocity,new Vector3f()),"removing the sole floor wakes every linked limb");
    }
    @Test void aWholeUnsupportedBodyKeepsFallingInsteadOfSleepingOnItsJoints(){
        var links=List.of(new RagdollConstraintMotion.Link(0,1));var turn=new Quaternionf();
        var support=RagdollConstraintMotion.supported(new boolean[2],links);
        Vec3 root=new Vec3(0,20,0),head=new Vec3(0,21,0),velocity=Vec3.ZERO;int still=0;
        for(int step=0;step<40;step++){
            velocity=velocity.scale(.975).add(0,-.04,0);var motion=velocity.scale(.5);
            assertEquals(velocity,RagdollConstraintMotion.linear(velocity,Vec3.ZERO),"joint closure adds no correction to coherent free fall");
            root=root.add(motion);head=head.add(motion);
            still=RagdollConstraintMotion.still(still,support[0],motion,turn,turn,velocity,new Vector3f());
            assertEquals(0,still);assertEquals(1,head.y-root.y,1e-10);
        }
        assertTrue(root.y<19&&velocity.y<0);
    }
    @Test void aPositionallyMovingOrTurningBodyIsNotQuietJustBecauseVelocityWasClipped(){
        var identity=new Quaternionf();
        assertEquals(0,RagdollConstraintMotion.still(23,true,new Vec3(0,.1,0),identity,identity,Vec3.ZERO,new Vector3f()));
        assertEquals(0,RagdollConstraintMotion.still(23,true,Vec3.ZERO,identity,new Quaternionf().rotationX(.1F),Vec3.ZERO,new Vector3f()));
    }
}
