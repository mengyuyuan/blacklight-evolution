package com.codex.blacklight.combat;

import java.util.ArrayDeque;
import java.util.UUID;

/** Amounts are stored as fractions, including when a player's health upgrades mid-attack. */
public final class GoddessDamageBudget {
    private record Hit(long tick,double fraction){}
    private final ArrayDeque<Hit> hits=new ArrayDeque<>();
    private UUID owner;
    private int combo;
    private double comboFraction;
    public float debit(UUID boss,int sequence,long tick,float maximum,float proposed){
        if(maximum<=0 || proposed<=0 || !Float.isFinite(maximum))return 0;
        while(!hits.isEmpty() && tick-hits.getFirst().tick>=60)hits.removeFirst();
        if(!boss.equals(owner) || sequence!=combo){
            owner=boss;combo=sequence;comboFraction=0;
        }
        double recent=0;
        for(var hit:hits)recent+=hit.fraction;
        double remaining=Math.max(0,.45-Math.max(comboFraction,recent));
        double fraction=Math.min(remaining, Math.min(.30,proposed/(double)maximum));
        if(fraction<=0)return 0;
        hits.addLast(new Hit(tick,fraction));comboFraction+=fraction;
        return (float)(fraction*maximum);
    }
}
