package com.codex.blacklight.combat;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.HashMap;
import java.util.WeakHashMap;

/** Amounts are stored as fractions, including when a player's health upgrades mid-attack. */
public final class GoddessDamageBudget {
    private record Hit(long tick,double fraction){}
    private final ArrayDeque<Hit> hits=new ArrayDeque<>();
    private final WeakHashMap<UUID, HashMap<Integer, Double>> castTotals = new WeakHashMap<>();
    public float debit(UUID boss,int sequence,long tick,float maximum,float proposed){
        return debit(boss,sequence,-1,tick,maximum,proposed);
    }
    float debit(UUID boss,int sequence,int otherCast,long tick,float maximum,float proposed){
        if(maximum<=0 || proposed<=0 || !Float.isFinite(maximum))return 0;
        while(!hits.isEmpty() && tick-hits.getFirst().tick>=60)hits.removeFirst();
        var totals = castTotals.computeIfAbsent(boss, key -> new HashMap<>());
        totals.keySet().removeIf(key -> key != sequence && key != otherCast);
        double comboFraction = totals.getOrDefault(sequence, 0D);
        double recent=0;
        for(var hit:hits)recent+=hit.fraction;
        double remaining=Math.max(0,.45-Math.max(comboFraction,recent));
        double fraction=Math.min(remaining, Math.min(.30,proposed/(double)maximum));
        if(fraction<=0)return 0;
        hits.addLast(new Hit(tick,fraction));
        totals.put(sequence, comboFraction + fraction);
        return (float)(fraction*maximum);
    }
}
