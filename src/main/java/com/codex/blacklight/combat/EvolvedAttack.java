package com.codex.blacklight.combat;

/** MC contact/visual windows fitted to the local P2 source clips, not decoded fight bytecode. */
public final class EvolvedAttack {
    private EvolvedAttack(){}
    public static int contact(String move,float age){
        if(move.startsWith("thrust"))return age>=18&&age<=28?0:-1;
        // The second cut is the return toward the opponent, after the retreat peak.
        if(move.equals("slash"))return age>=6&&age<=12?0:age>=30&&age<=39?1:-1;
        return move.equals("leap")&&age>=16&&age<=29?0:-1;
    }
    public static boolean trail(String move,float age,int side){
        var special=com.codex.blacklight.infection.EvolvedSpecialMove.resolve(move);
        if(special!=null)return age>=special.contact-5&&age<=special.retract&&(special!=com.codex.blacklight.infection.EvolvedSpecialMove.MEND&&special!=com.codex.blacklight.infection.EvolvedSpecialMove.VOLLEY);
        if(move.equals("thrust_left")&&side!=1||move.equals("thrust_right")&&side!=0)return false;
        return contact(move,age+1)>=0||contact(move,age-1)>=0;
    }
    public static float charge(String move,float age){
        if(!move.startsWith("thrust")||age<3||age>=18)return 0;
        return (float)Math.sin((age-3)/15*Math.PI);
    }
}
