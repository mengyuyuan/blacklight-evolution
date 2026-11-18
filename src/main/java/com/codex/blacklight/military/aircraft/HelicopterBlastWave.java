package com.codex.blacklight.military.aircraft;

import java.util.*;
import net.minecraft.nbt.*;

/** Distinct P1 helicopter missile Hit timelines, sampled at Minecraft's 20 Hz. */
public final class HelicopterBlastWave {
    public static final int DURATION=60,DAMAGE_END=6;
    public static final int RAPID_START=9,RAPID_OUTER=10,RAPID_END=12,RAPID_DURATION=20;
    public static final double MAX_RADIUS=9;
    public record Stage(double radius,float sourceDamage,float sourceDirectDamage) {
        private Stage(double radius,float sourceDamage){this(radius,sourceDamage,500);}
        /** Retains the established MC direct-hit balance, preserving source ratios. */
        public float damage(float directDamage){return directDamage*sourceDamage/sourceDirectDamage;}
    }
    private static final Stage[] STAGES={new Stage(1,500),new Stage(4,100),new Stage(7,35),new Stage(9,15)};
    private static final Stage RAPID_INNER_STAGE=new Stage(2,100,60),RAPID_OUTER_STAGE=new Stage(6,50,60);
    private final boolean rapid;
    private int age;
    private final Set<UUID> contacted=new HashSet<>();
    public HelicopterBlastWave(){this(false);}
    public HelicopterBlastWave(boolean rapid){this.rapid=rapid;}
    public boolean rapid(){return rapid;}
    public int duration(){return rapid?RAPID_DURATION:DURATION;}
    public String weaponId(){return rapid?"helicopter_70mm":"helicopter_hellfire";}
    public int age(){return age;}
    public boolean expired(){return age>=duration();}
    public Stage stage(){return rapid?rapidStage(age):stage(age);}
    public static Stage stage(int tick){return tick<0||tick>=DAMAGE_END?null:STAGES[tick==0?0:tick==1?1:tick<4?2:3];}
    public static Stage rapidStage(int tick){return tick<RAPID_START||tick>=RAPID_END?null:tick<RAPID_OUTER?RAPID_INNER_STAGE:RAPID_OUTER_STAGE;}
    public void advance(int ticks){if(ticks<0)throw new IllegalArgumentException("Negative blast time");age=(int)Math.min(duration(),(long)age+ticks);}
    public boolean contacted(UUID id){return contacted.contains(id);}
    // Original CollisionActionBehaviour records the contact before applying damage,
    // so invulnerability/event cancellation cannot turn a later ring into a retry.
    public boolean contact(UUID id){return !expired()&&stage()!=null&&contacted.add(Objects.requireNonNull(id));}
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",2);tag.putString("Weapon",weaponId());tag.putInt("Age",age);var list=new ListTag();
        for(var id:contacted.stream().sorted().toList()){var row=new CompoundTag();row.putUUID("Entity",id);list.add(row);}tag.put("Contacts",list);return tag;
    }
    public static HelicopterBlastWave load(CompoundTag tag){
        int version=tag.getInt("Version");boolean rapid=false;
        if(version==2){if(!tag.contains("Weapon",Tag.TAG_STRING))return null;String weapon=tag.getString("Weapon");if(!weapon.equals("helicopter_70mm")&&!weapon.equals("helicopter_hellfire"))return null;rapid=weapon.equals("helicopter_70mm");}
        else if(version!=1||tag.contains("Weapon"))return null;
        if(!tag.contains("Age",Tag.TAG_INT)||tag.getInt("Age")<0||tag.getInt("Age")>(rapid?RAPID_DURATION:DURATION)||!tag.contains("Contacts",Tag.TAG_LIST))return null;
        var raw=tag.get("Contacts");if(!(raw instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND||list.size()>4096)return null;
        var state=new HelicopterBlastWave(rapid);state.age=tag.getInt("Age");
        for(int i=0;i<list.size();i++){var row=list.getCompound(i);if(!row.hasUUID("Entity")||!state.contacted.add(row.getUUID("Entity")))return null;}
        return state;
    }
}
