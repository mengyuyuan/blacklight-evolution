package com.codex.blacklight.entity;

import com.codex.blacklight.combat.EncounterMove;
import com.codex.blacklight.combat.GoliathPresentation;
import static com.codex.blacklight.combat.EncounterMove.*;

/** Minecraft encounter designs. These numbers are not claimed to be decoded Prototype AI values. */
public enum EncounterKind {
    LEADER_HUNTER("leader_hunter","领袖猎手",180,11,.30,1.5F,3.2F,true,false,false),
    BRAWLER("brawler","格斗者",100,9,.32,1.3F,2.5F,false,false,false),
    JUGGERNAUT("juggernaut","主宰",240,14,.22,2.2F,3.6F,false,false,false),
    GOLIATH("goliath","歌利亚",1100,22,.17,GoliathPresentation.WIDTH,GoliathPresentation.HEIGHT,true,false,false),
    FLYER("flyer","飞行感染体",65,7,.30,1.6F,1.2F,false,true,false),
    ELIZABETH_GREENE("elizabeth_greene","伊丽莎白·格林",1400,17,0,com.codex.blacklight.combat.GreenePresentation.WIDTH,com.codex.blacklight.combat.GreenePresentation.HEIGHT,true,false,false),
    ALEX_MERCER("alex_mercer","阿历克斯·墨瑟",800,13,.32,.8F,2.1F,true,false,false),
    ROLAND("roland","罗兰",360,11,.31,.8F,2.1F,true,false,false),
    ARCHER("archer","卡伦·阿彻",400,10,.29,.75F,2F,true,false,false),
    GALLOWAY("galloway","萨布丽娜·加洛韦",440,12,.30,.8F,2.1F,true,false,false),
    KOENIG("koenig","安东·柯尼格",320,10,.28,.75F,2F,true,false,false),
    CAPTAIN_CROSS("captain_cross","罗伯特·克罗斯",320,9,.28,.75F,2F,true,false,true),
    SUPER_SOLDIER("super_soldier","超级士兵",150,10,.30,1F,2.4F,false,false,true);
    public final String id,zh;public final double health,damage,speed;public final float width,height;public final boolean boss,flying,military;
    EncounterKind(String id,String zh,double health,double damage,double speed,float width,float height,boolean boss,boolean flying,boolean military){this.id=id;this.zh=zh;this.health=health;this.damage=damage;this.speed=speed;this.width=width;this.height=height;this.boss=boss;this.flying=flying;this.military=military;}
    public boolean humanoid(){return ordinal()>=ALEX_MERCER.ordinal();}
    /** Retired drafts remain readable in old saves; Brawler resolves to the original hunter. */
    public boolean enabled(){return switch(this){case JUGGERNAUT,GOLIATH,FLYER,ELIZABETH_GREENE,SUPER_SOLDIER->true;default->false;};}
    public boolean armored(){return this==JUGGERNAUT||this==GOLIATH;}
    public int weakParts(){return this==ELIZABETH_GREENE?3:this==GOLIATH?2:0;}
    public EncounterMove choose(double distance,int phase,int sequence){
        return switch(this){
            case LEADER_HUNTER->sequence%5==0?HOWL:distance>4?POUNCE:sequence%2==0?DOUBLE:CLAW;
            case BRAWLER->distance>4?POUNCE:sequence%3==2?SLAM:DOUBLE;
            case JUGGERNAUT->distance>5?CHARGE:sequence%3==0?GUARD:sequence%3==1?HAMMER:SLAM;
            case GOLIATH->distance>8?CHARGE:sequence%3==0?ERUPTION:sequence%3==1?SLAM:HAMMER;
            case FLYER->DIVE;
            case ELIZABETH_GREENE->sequence%4==0?HOWL:sequence%4==1?ORBS:sequence%4==2?VOLLEY:ERUPTION;
            case ALEX_MERCER->phase==0?(distance>5?POUNCE:DOUBLE):phase==1?(distance>6?CHARGE:SLAM):(sequence%4==0?ERUPTION:distance>5?WHIP:sequence%3==0?DODGE:BLADE);
            case ROLAND->distance>5?POUNCE:sequence%3==0?DODGE:BLADE;
            case ARCHER->sequence%4==0?HOWL:distance>7?VOLLEY:WHIP;
            case GALLOWAY->sequence%3==0?ERUPTION:distance>6?CHARGE:HAMMER;
            case KOENIG->sequence%3==0?DODGE:distance>5?WHIP:DOUBLE;
            case CAPTAIN_CROSS->distance>5?GRENADE:sequence%3==0?DODGE:BATON;
            case SUPER_SOLDIER->distance>4?POUNCE:sequence%3==0?GUARD:HAMMER;
        };
    }
    public boolean permits(EncounterMove move){for(int p=0;p<3;p++)for(int s=0;s<12;s++)for(double d:new double[]{2,6,10,20})if(choose(d,p,s)==move)return true;return false;}
}
