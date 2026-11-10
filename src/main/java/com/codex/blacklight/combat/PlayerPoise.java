package com.codex.blacklight.combat;

/** Player-versus-player hit reactions: short, never chained into a permanent lock,
 * and never interrupting a committed attack. Mobs keep their full control windows. */
public final class PlayerPoise {
    public static final int LIGHT_TICKS=6, HEAVY_TICKS=12, IMMUNE_TICKS=14;
    private PlayerPoise(){}
    /** Returns the reaction length to publish, or 0 when the hit must not stagger the player. */
    public static int duration(long previousStart,int previousDuration,long now,int kind,boolean attacking){
        boolean heavy=kind!=HitReaction.STAGGER;
        if(attacking)return 0;
        long recovered=previousStart+Math.max(0,previousDuration);
        boolean immune=previousDuration>0&&now<recovered+IMMUNE_TICKS;
        if(immune&&(!heavy||now<recovered))return 0;
        return heavy?HEAVY_TICKS:LIGHT_TICKS;
    }
    /** Players never enter the mob knockdown chain. */
    public static int kind(int kind){return kind==HitReaction.DOWN||kind==HitReaction.GETUP?HitReaction.HEAVY:kind;}
}