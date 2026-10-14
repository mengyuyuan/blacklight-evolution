package com.codex.blacklight.combat;
import com.codex.blacklight.power.CombatSkill;

/** Level and charge affect ballistic launch speed, direct-hit damage and knockback. */
public final class BlockThrowTuning {
    public static final int MAX_CHARGE_TICKS=30;
    private BlockThrowTuning(){}
    public static int level(int mask){
        return (mask&CombatSkill.BLOCK_THROW_3.bit())!=0?3:(mask&CombatSkill.BLOCK_THROW_2.bit())!=0?2:(mask&CombatSkill.BLOCK_THROW_1.bit())!=0?1:0;
    }
    private static double charge(int ticks){return Math.max(0,Math.min(1,ticks/(double)MAX_CHARGE_TICKS));}
    public static double speed(int level,int ticks){return switch(level){case 1->1.2+.6*charge(ticks);case 2->1.55+.95*charge(ticks);case 3->1.9+1.3*charge(ticks);default->0;};}
    public static float damage(int level,int ticks){return (float)switch(level){case 1->12+10*charge(ticks);case 2->18+16*charge(ticks);case 3->26+24*charge(ticks);default->0;};}
    public static double knockback(int level,int ticks){return level<1||level>3?0:.6+.25*level+.65*charge(ticks);}
}
