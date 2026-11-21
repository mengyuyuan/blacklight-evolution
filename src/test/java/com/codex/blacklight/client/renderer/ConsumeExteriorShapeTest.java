package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.client.renderer.ConsumeExteriorShape.*;

class ConsumeExteriorShapeTest {
    private static final Vec3 UP=new Vec3(0,1,0),RIGHT=new Vec3(1,0,0),FRONT=new Vec3(0,0,1);
    private static Surface body(Vec3 p){return new Surface(p,UP,RIGHT,FRONT,.235,.145,.36);}
    private static Bodies pair(Vec3 position){
        var surface=body(Vec3.ZERO);
        return new Bodies(surface,body(position),surface,surface,FRONT,new Vec3(.1,0,0));
    }
    @Test void pairedRibbonFollowsOnlyTheChosenVictim(){
        Vec3 shift=new Vec3(.3,.19,-.21);
        var base=strip(PAIR[0],0,pair(FRONT),1,2.5);
        var moved=strip(PAIR[0],0,pair(FRONT.add(shift)),1,2.5);
        assertEquals(0,base.points()[0].distanceTo(moved.points()[0]),1e-9);
        int last=base.points().length-1;
        assertEquals(0,base.points()[last].add(shift).distanceTo(moved.points()[last]),1e-9);
        assertNull(strip(PAIR[6],6,pair(FRONT),.07F,.1));
        assertTrue(strip(PAIR[0],0,pair(FRONT),.07F,.1).points().length<base.points().length);
    }
    @Test void foldMassHasSilhouetteOnBackAndFlankAndClearsHead(){
        var surface=body(Vec3.ZERO);
        var full=ConsumeResidueKnot.folds(surface,FRONT,false,1,2,Vec3.ZERO);
        var mirror=ConsumeResidueKnot.folds(surface,FRONT,true,1,2,Vec3.ZERO);
        double outside=0,back=0,radius=0,top=-1;
        for(int f=0;f<full.length;f++){
            var strip=full[f];
            for(int i=0;i<strip.points().length;i++){
                var p=strip.points()[i];double r=strip.widths()[i];
                outside=Math.max(outside,p.x+r-surface.width());
                back=Math.max(back,-p.z+r-surface.depth());
                radius=Math.max(radius,r);top=Math.max(top,p.y+r);
                var other=mirror[f].points()[i];assertEquals(-p.x,other.x,1e-8);assertEquals(p.z,other.z,1e-8);
            }
            assertTrue(strip.points()[0].x<surface.width());
            assertTrue(strip.widths()[0]<.025&&strip.widths()[strip.widths().length-1]<.025);
        }
        assertTrue(outside>.16&&back>.15,"clump must bulge out from the two covered faces");
        assertTrue(radius>.095);assertTrue(top<surface.half()+.04);
    }
    @Test void foldsContractAndFollowTheRealPoseDuringMovement(){
        var skin=body(Vec3.ZERO);Vec3 velocity=new Vec3(.26,0,.09),shift=new Vec3(3,-1,2);
        var full=ConsumeResidueKnot.folds(skin,FRONT,false,1,3,velocity);
        var rotatedBody=new Surface(shift,UP,RIGHT.yRot(.8F),FRONT.yRot(.8F),skin.width(),skin.depth(),skin.half());
        var rotated=ConsumeResidueKnot.folds(rotatedBody,FRONT.yRot(.8F),false,1,3,velocity.yRot(.8F));
        var fading=ConsumeResidueKnot.folds(skin,FRONT,false,.08F,3,velocity);
        assertEquals(0,ConsumeResidueKnot.folds(skin,FRONT,false,0,3,velocity).length);
        for(int f=0;f<full.length;f++)for(int i=0;i<full[f].points().length;i++){
            assertTrue(full[f].points()[i].yRot(.8F).add(shift).distanceTo(rotated[f].points()[i])<.0001);
            assertTrue(fading[f].widths()[i]<full[f].widths()[i]*.2);
        }
    }
}
