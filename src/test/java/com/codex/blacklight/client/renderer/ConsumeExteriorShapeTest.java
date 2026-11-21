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

    private static Strip[] folds(Surface body, Surface rightArm, Surface leftArm, Vec3 facing, float age, Vec3 movement) {
        return ConsumeResidueKnot.folds(body,rightArm,leftArm,null,null,facing,1,age,3,movement);
    }
    @Test void theSettledWeaveHasSeparateStrandsOnChestBackAndBothFlanks() {
        var body=body(Vec3.ZERO);
        var full=folds(body,null,null,FRONT,2.3F,Vec3.ZERO);
        assertEquals(14,full.length);
        int chest=0,back=0,left=0,right=0;
        for(var strip:full) {
            for(int i=0;i<strip.points().length;i++) {
                var p=strip.points()[i];
                if(strip.widths()[i]<.012)continue;
                if(p.z>body.depth()+.017)chest++;
                if(-p.z>body.depth()+.017)back++;
                if(p.x>body.width()+.017)left++;
                if(-p.x>body.width()+.017)right++;
                assertTrue(p.y+strip.widths()[i]<body.half()+.035);
            }
        }
        assertTrue(Math.min(Math.min(chest,back),Math.min(left,right))>15);
        Vec3 shift=new Vec3(3,-1,2),v=new Vec3(.23,0,.06);
        var movedBody=new Surface(shift,UP,RIGHT.yRot(.82F),FRONT.yRot(.82F),body.width(),body.depth(),body.half());
        var moved=folds(movedBody,null,null,FRONT.yRot(.82F),2.3F,v.yRot(.82F));
        var original=folds(body,null,null,FRONT,2.3F,v);
        for(int i=0;i<moved.length;i++)
            assertTrue(moved[i].points()[4].distanceTo(shift.add(original[i].points()[4].yRot(.82F)))<.0001);
    }

    @Test void theTipWithdrawsAlongItsOwnCurveWhileTheMiddleStillHasThickness() {
        var skin=body(Vec3.ZERO);
        float peak=com.codex.blacklight.combat.ConsumeGiverEffect.REVERSE_SECONDS;
        var before=folds(skin,null,null,FRONT,peak,Vec3.ZERO)[0];
        var after=folds(skin,null,null,FRONT,peak+.63F,Vec3.ZERO)[0];
        assertTrue(after.points()[0].distanceTo(before.points()[0])>.10);
        assertTrue(after.points()[0].distanceTo(before.points()[6])<after.points()[0].distanceTo(before.points()[0]));
        assertTrue(after.widths()[after.widths().length/2]>.025);
        assertTrue(after.widths()[0]<after.widths()[after.widths().length/2]*.2);
    }

    @Test void posedArmsClearBeforeTheMiddleOfTheChestAndBack() {
        var body=body(Vec3.ZERO);
        var right=Surface.arm(new Vec3(-.32,.28,0),new Vec3(-.55,.01,-.14),RIGHT);
        var left=Surface.arm(new Vec3(.32,.28,0),new Vec3(.47,.04,.21),RIGHT);
        float peak=com.codex.blacklight.combat.ConsumeGiverEffect.REVERSE_SECONDS;
        assertEquals(18,folds(body,right,left,FRONT,peak,Vec3.ZERO).length);
        var full=ConsumeSkinPath.giverWeights(peak);
        var returning=ConsumeSkinPath.giverWeights(peak+1.16F);
        var late=ConsumeSkinPath.giverWeights(peak+1.8F);
        assertTrue(ConsumeSkinPath.armCoverage(full,true,.72)>.8);
        assertEquals(0,ConsumeSkinPath.armCoverage(returning,false,.72),.02);
        assertTrue(ConsumeSkinPath.torsoCoverage(late,.54)>.75);
        assertEquals(0,ConsumeSkinPath.armCoverage(late,true,.46),.02);
        var remaining=folds(body,right,left,FRONT,peak+1.8F,Vec3.ZERO);
        assertTrue(remaining.length>0);
        assertEquals(0,folds(body,right,left,FRONT,6.66F,Vec3.ZERO).length);
    }
}
