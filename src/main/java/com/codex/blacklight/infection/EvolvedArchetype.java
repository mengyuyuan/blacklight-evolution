package com.codex.blacklight.infection;

import java.util.*;

/** Stable runtime dispatch. A saved type, never a fresh roll from a rendering frame. */
public enum EvolvedArchetype {
    BLADE("blade", "blade",1,1,0,1,2),
    STRENGTH("strength","strength",1.15,1.2,0,.85,1.7),
    ARMORED("armored","armor",1.25,.9,6,.8,1.6),
    PREDATOR("predator","predation",.95,1.05,0,1.12,1.65),
    TENDRIL("tendril","tendril",1, .9,0,1,1.8),
    SKIRMISHER("skirmisher","mobility",.9,.95,0,1.18,1.9),
    SENSORY("sensory","senses",.95,.85,0,1.02,1.7),
    REGENERATOR("regenerator","support",1.1,.85,2,.95,1.7);

    private final String key,bias;
    public final double health,attack,armor,speed,reach;
    EvolvedArchetype(String key,String bias,double health,double attack,double armor,double speed,double reach){
        this.key=key;this.bias=bias;this.health=health;this.attack=attack;this.armor=armor;this.speed=speed;this.reach=reach;
    }
    public String key(){return key;}
    public String id(){return "blacklight:"+key+"_evolved";}
    public String bias(){return "blacklight:"+bias;}
    public String translation(){return "entity.blacklight.evolved_"+key;}
    public static EvolvedArchetype resolve(String type){for(var value:values())if(value.id().equals(type))return value;return BLADE;}
    public static Set<String> types(){var ids=new HashSet<String>();for(var value:values())ids.add(value.id());return Set.copyOf(ids);}
}
