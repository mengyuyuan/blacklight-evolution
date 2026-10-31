package com.codex.blacklight.infection;

/** Bounded combinations using complete existing attack/recovery clips. */
public final class EvolvedCombo {
    private EvolvedCombo(){}
    public static String followup(String completed,int count,boolean connected,double gap,boolean visible){
        if(!connected||!visible||count>=3||gap>2||gap<0)return "";
        return completed.equals("thrust_left")?"thrust_right":completed.equals("thrust_right")?"slash":"";
    }
    public static int recovery(int count){return count>=3?18:7;}
}
