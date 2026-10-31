package com.codex.blacklight.firearms;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirearmSupportPoseTest {
    private static Object frame(boolean enabled,boolean mainGun,boolean offhandEmpty,boolean leftMain,double reload) throws Exception {
        Class<?> type;
        try{type=Class.forName("com.codex.blacklight.firearms.FirearmSupportPose");}
        catch(ClassNotFoundException absent){fail("A real support-hand pose must control second-hand rendering");return null;}
        return type.getMethod("evaluate",boolean.class,boolean.class,boolean.class,boolean.class,double.class).invoke(null,enabled,mainGun,offhandEmpty,leftMain,reload);
    }
    private static boolean visible(Object frame) throws Exception{return (boolean)frame.getClass().getMethod("visible").invoke(frame);}
    private static double number(Object frame,String name) throws Exception{return ((Number)frame.getClass().getMethod(name).invoke(frame)).doubleValue();}
    @Test void mainRifleWithAnEmptyOffhandGetsOneVisibleSupportingHand() throws Exception {
        var f=frame(true,true,true,false,0);assertTrue(visible(f));assertTrue((boolean)f.getClass().getMethod("left").invoke(f));assertEquals(1,number(f,"gripWeight"),1e-7);
    }
    @Test void occupiedOffhandAndDualGunsNeverGenerateAThirdHand() throws Exception {
        assertFalse(visible(frame(true,true,false,false,0)));assertFalse(visible(frame(true,false,false,false,0)));
    }
    @Test void aLeftMainHandMirrorsTheSupportPoseToTheRight() throws Exception {
        var rightMain=frame(true,true,true,false,0);var leftMain=frame(true,true,true,true,0);
        assertFalse((boolean)leftMain.getClass().getMethod("left").invoke(leftMain));
        assertEquals(-number(rightMain,"x"),number(leftMain,"x"),1e-7);assertEquals(number(rightMain,"y"),number(leftMain,"y"),1e-7);assertEquals(number(rightMain,"z"),number(leftMain,"z"),1e-7);
    }
    @Test void reloadDetachesThenReattachesTheSupportingHand() throws Exception {
        assertEquals(1,number(frame(true,true,true,false,0),"gripWeight"),1e-7);
        assertTrue(number(frame(true,true,true,false,.5),"gripWeight")<.05);
        assertEquals(1,number(frame(true,true,true,false,1),"gripWeight"),1e-7);
    }
    @Test void menusAbilitiesAndNoMainFirearmHideTheSupportHand() throws Exception {
        assertFalse(visible(frame(false,true,true,false,0)));assertFalse(visible(frame(true,false,true,false,0)));
    }
}
