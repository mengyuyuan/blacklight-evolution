package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;

/** Authoritative contact windows (ticks), shared with animation export and previews. */
public enum WeaponMove {
    BLADE_CUT(PowerForm.BLADE,"blade_cut",16,5,8,9,2.3,95),
    BLADE_RETURN(PowerForm.BLADE,"blade_return",18,6,9,11,2.3,105),
    BLADE_CLEAVE(PowerForm.BLADE,"blade_cleave",44,10,33,9,2.8,110),
    BLADE_RUSH(PowerForm.BLADE,"blade_rush",24,8,12,14,2.8,80),
    BLADE_DIVE(PowerForm.BLADE,"blade_dive",24,8,13,16,2.5,90),
    HAMMER_JAB(PowerForm.HAMMERFISTS,"hammer_jab",26,7,15,11,2.1,70),
    HAMMER_HOOK(PowerForm.HAMMERFISTS,"hammer_hook",28,9,16,14,2.2,110),
    HAMMER_QUAKE(PowerForm.HAMMERFISTS,"hammer_quake",36,18,21,20,4.2,360),
    HAMMER_RAM(PowerForm.HAMMERFISTS,"hammer_ram",26,9,15,16,2.2,80),
    HAMMER_DROP(PowerForm.HAMMERFISTS,"hammer_drop",30,12,17,22,4.0,360),
    WHIP_LASH(PowerForm.WHIPFIST,"whip_lash",22,9,12,8,8.0,12),
    WHIP_RETURN(PowerForm.WHIPFIST,"whip_return",24,10,13,9,7.0,22),
    WHIP_SWEEP(PowerForm.WHIPFIST,"whip_sweep",40,12,25,12,5.5,360),
    WHIP_LUNGE(PowerForm.WHIPFIST,"whip_lunge",26,10,15,11,7.0,25),
    WHIP_AIR(PowerForm.WHIPFIST,"whip_air",24,9,14,10,7.0,35),
    MUSCLE_JAB(PowerForm.MUSCLE_MASS,"muscle_jab",14,4,7,10,2.1,60),
    MUSCLE_CROSS(PowerForm.MUSCLE_MASS,"muscle_cross",17,6,9,13,2.2,80),
    MUSCLE_UPPERCUT(PowerForm.MUSCLE_MASS,"muscle_uppercut",26,11,14,18,2.3,65),
    MUSCLE_RUSH(PowerForm.MUSCLE_MASS,"muscle_rush",23,8,13,14,2.3,70),
    MUSCLE_AIR(PowerForm.MUSCLE_MASS,"muscle_air",22,8,12,14,2.3,70),
    BLADE_COMBO_C(PowerForm.BLADE,"blade_combo_c",16,5,8,9,2.3,95),
    BLADE_COMBO_C2(PowerForm.BLADE,"blade_combo_c2",18,6,9,11,2.3,105),
    BLADE_COMBO_D(PowerForm.BLADE,"blade_combo_d",16,5,8,9,2.3,95),
    HAMMER_COMBO_C(PowerForm.HAMMERFISTS,"hammer_combo_c",26,7,15,11,2.1,70),
    HAMMER_COMBO_D(PowerForm.HAMMERFISTS,"hammer_combo_d",28,9,16,14,2.2,110),
    HAMMER_COMBO_SPECIAL(PowerForm.HAMMERFISTS,"hammer_combo_special",36,18,21,20,4.2,360),
    WHIP_COMBO_C(PowerForm.WHIPFIST,"whip_combo_c",22,9,12,8,8.0,12),
    WHIP_LONGSHOT(PowerForm.WHIPFIST,"whip_longshot",26,10,15,11,7.0,25),
    HAMMER_JAB_CHARGED(PowerForm.HAMMERFISTS,"hammer_jab_charged",26,7,15,11,2.1,70),
    HAMMER_HOOK_CHARGED(PowerForm.HAMMERFISTS,"hammer_hook_charged",28,9,16,14,2.2,110),
    HAMMER_COMBO_C_CHARGED(PowerForm.HAMMERFISTS,"hammer_combo_c_charged",26,7,15,11,2.1,70),
    HAMMER_COMBO_D_CHARGED(PowerForm.HAMMERFISTS,"hammer_combo_d_charged",28,9,16,14,2.2,110),
    HAMMER_QUAKE_CHARGED(PowerForm.HAMMERFISTS,"hammer_quake_charged",36,18,21,20,4.2,360),
    HAMMER_COMBO_SPECIAL_CHARGED(PowerForm.HAMMERFISTS,"hammer_combo_special_charged",36,18,21,20,4.2,360),
    WHIP_LASH_CHARGED(PowerForm.WHIPFIST,"whip_lash_charged",22,9,12,8,8.0,12),
    WHIP_RETURN_CHARGED(PowerForm.WHIPFIST,"whip_return_charged",24,10,13,9,7.0,22),
    WHIP_COMBO_C_CHARGED(PowerForm.WHIPFIST,"whip_combo_c_charged",22,9,12,8,8.0,12),
    WHIP_SWEEP_CHARGED(PowerForm.WHIPFIST,"whip_sweep_charged",40,12,25,12,5.5,360),
    WHIP_LONGSHOT_CHARGED(PowerForm.WHIPFIST,"whip_longshot_charged",26,10,15,11,7.0,25),
    CLAW_AUTO1(PowerForm.CLAWS,"claw_auto1",14,5,8,8,2.8,100),
    CLAW_AUTO2(PowerForm.CLAWS,"claw_auto2",16,5,8,9,2.8,110),
    CLAW_AUTO3(PowerForm.CLAWS,"claw_auto3",22,6,10,12,3,120),
    CLAW_AUTO1_CHARGED(PowerForm.CLAWS,"claw_auto1_charged",20,5,8,11,2.8,100),
    CLAW_AUTO2_CHARGED(PowerForm.CLAWS,"claw_auto2_charged",20,5,8,12,2.8,110),
    CLAW_AUTO3_CHARGED(PowerForm.CLAWS,"claw_auto3_charged",24,6,10,16,3,120),
    CLAW_SPRINT(PowerForm.CLAWS,"claw_sprint",24,6,10,12,3,100),
    CLAW_GROUNDSPIKE(PowerForm.CLAWS,"claw_groundspike",44,14,16,14,5,360),
    MUSCLE_COMBO_C(PowerForm.MUSCLE_MASS,"muscle_combo_c",24,6,10,16,2.8,90),
    MUSCLE_SUPERMAN(PowerForm.MUSCLE_MASS,"muscle_superman",28,7,11,18,3,85),
    AIR_AUTO1(PowerForm.NONE,"air_auto1",24,4,7,8,2.8,85),
    AIR_AUTO2(PowerForm.NONE,"air_auto2",24,4,7,8,2.8,85),
    AIR_AUTO3(PowerForm.NONE,"air_auto3",24,4,7,10,2.8,85),
    AIR_AUTO4(PowerForm.NONE,"air_auto4",26,8,11,14,3,100),
    AIR_KICK(PowerForm.NONE,"air_kick",26,7,11,12,3,85),
    COMBO_KICK1(PowerForm.NONE,"combo_kick1",24,6,10,11,2.8,100),
    COMBO_KICK2(PowerForm.NONE,"combo_kick2",26,7,11,13,2.8,110),
    COMBO_KNEE(PowerForm.NONE,"combo_knee",24,6,10,10,2.4,90),
    COMBO_SPIN(PowerForm.NONE,"combo_spin",24,6,10,12,3,150),
    COMBO_AXE(PowerForm.NONE,"combo_axe",26,7,11,14,2.8,90),
    COMBO_SPIN_LEFT(PowerForm.NONE,"combo_spin_left",26,7,11,14,3,150),
    COMBO_AXE_HANDLE(PowerForm.NONE,"combo_axe_handle",26,7,11,14,2.8,90),
    COMBO_BACKHAND(PowerForm.NONE,"combo_backhand",26,7,11,13,2.8,140),
    COMBO_MACH(PowerForm.NONE,"combo_mach",26,7,11,17,3,90),
    COMBO_FLOAT(PowerForm.NONE,"combo_float",32,10,13,15,3,90),
    COMBO_GROUND_POUND(PowerForm.NONE,"combo_ground_pound",32,12,15,20,4.5,360),
    COMBO_CLAP(PowerForm.NONE,"combo_clap",28,10,13,18,4,150),
    COMBO_PAIN(PowerForm.NONE,"combo_pain",30,11,14,20,7,65),
    AIR_GROUND_POUND(PowerForm.NONE,"air_ground_pound",36,16,19,22,4.5,360),
    AIR_CLAP(PowerForm.NONE,"air_clap",30,12,15,18,4,150),
    AIR_PAIN(PowerForm.NONE,"air_pain",30,12,15,20,4,90),
    WHIP_AIR_CHARGED(PowerForm.WHIPFIST,"whip_air_charged",24,9,14,14,7,35),
    AIR_LAUNCH_FOLLOW(PowerForm.NONE,"air_launch_follow",26,6,10,14,3,90),
    CLAW_POUNCE(PowerForm.CLAWS,"claw_pounce",26,11,15,15,2.8,100),
    DEV_GROUNDSPIKE(PowerForm.NONE,"dev_groundspike",120,40,85,20,10,360),
    DEV_AIR_GROUNDSPIKE(PowerForm.NONE,"dev_air_groundspike",120,40,85,20,10,360),
    DEV_TENDRIL(PowerForm.NONE,"dev_tendril",120,40,85,16,7.5,360),
    DEV_AIR_TENDRIL(PowerForm.NONE,"dev_air_tendril",120,40,85,16,7.5,360),
    DEV_CRITICAL_PAIN(PowerForm.NONE,"dev_critical_pain",120,40,85,50,50,30),
    DEV_AIR_CRITICAL_PAIN(PowerForm.NONE,"dev_air_critical_pain",120,40,85,50,50,30),
    BULLETDIVE_DROP(PowerForm.NONE,"bulletdive_drop",90,32,40,22,6,360);
    public final PowerForm form;
    public final String id;
    public final int duration,start,end;
    public final float damage;
    public final double reach,arc;
    private final LocalWeaponMotion.Definition localMotion;
    WeaponMove(PowerForm form,String id,int duration,int start,int end,float damage,double reach,double arc){
        this.form=form;this.id=id;
        var local=LocalWeaponMotion.find(id);
        this.localMotion=local.orElse(null);
        this.duration=local.map(LocalWeaponMotion.Definition::durationTicks).orElse(duration);
        this.start=local.map(LocalWeaponMotion.Definition::contactStartTick).orElse(start);
        this.end=local.map(LocalWeaponMotion.Definition::contactEndTick).orElse(end);
        this.damage=damage;this.reach=reach;this.arc=arc;
    }
    public static WeaponMove byId(int id){return id<0||id>=values().length?null:values()[id];}
    public static boolean supports(PowerForm form){return form==PowerForm.NONE||form==PowerForm.CLAWS||form==PowerForm.BLADE||form==PowerForm.HAMMERFISTS||form==PowerForm.WHIPFIST||form==PowerForm.MUSCLE_MASS;}
    public static boolean controls(PowerForm form,boolean emptyHands){return supports(form)&&(form!=PowerForm.NONE||emptyHands);}
    public float damageFor(PowerForm active){return damage*(active==PowerForm.NONE?.65F:active==PowerForm.MUSCLE_MASS&&shared()&&!id.startsWith("dev_")&&this!=BULLETDIVE_DROP?2F:1F);}
    public enum Input { ATTACK, SPECIAL, GROUND_POUND, CLAP, PAIN;
        public boolean chord(){return this!=ATTACK&&this!=SPECIAL;}
    }
    public boolean shared(){return form==PowerForm.NONE;}
    public boolean accepts(PowerForm active){return form==active||shared()&&supports(active)||active==PowerForm.NONE&&form==PowerForm.MUSCLE_MASS;}
    public boolean aerial(){return name().startsWith("AIR_")||name().startsWith("DEV_AIR_")||this==BULLETDIVE_DROP||this==CLAW_POUNCE||this==BLADE_DIVE||this==HAMMER_DROP||uncharged()==WHIP_AIR||this==MUSCLE_AIR||this==HAMMER_RAM||uncharged()==HAMMER_COMBO_SPECIAL;}
    public boolean airCombo(){return this==AIR_AUTO1||this==AIR_AUTO2||this==AIR_AUTO3||this==AIR_AUTO4;}
    public boolean landingAttack(){return this==BLADE_DIVE||this==HAMMER_DROP||this==HAMMER_RAM||this==AIR_GROUND_POUND||uncharged()==HAMMER_COMBO_SPECIAL;}
    public boolean closeSweeper(){return uncharged()==WHIP_SWEEP;}
    public int sweeperPulse(int tick){return !closeSweeper()||tick<start||tick>=end?-1:Math.min(8,(tick-start)*9/Math.max(1,end-start));}
    public boolean groundSpike(){return this==CLAW_GROUNDSPIKE;}
    /** Compatibility for existing animation/QA callers: true means Special, not a universal heavy hit. */
    public static WeaponMove choose(PowerForm form,boolean special,boolean air,boolean sprint,boolean second){
        return choose(form,special?Input.SPECIAL:Input.ATTACK,air,sprint,second);
    }
    public static WeaponMove choose(PowerForm form,Input input,boolean air,boolean sprint,boolean second){
        if(!supports(form))return null;
        if(input.chord())return switch(input){
            case GROUND_POUND -> air?AIR_GROUND_POUND:COMBO_GROUND_POUND;
            case CLAP -> air?AIR_CLAP:COMBO_CLAP;
            case PAIN -> air?AIR_PAIN:COMBO_PAIN;
            default -> throw new IllegalStateException();
        };
        if(air&&input==Input.ATTACK)return AIR_AUTO1;
        WeaponMove first=switch(form){
            case CLAWS -> air?CLAW_POUNCE:input==Input.SPECIAL?CLAW_GROUNDSPIKE:sprint?CLAW_SPRINT:CLAW_AUTO1;
            case BLADE -> air?BLADE_DIVE:input==Input.SPECIAL?BLADE_RUSH:BLADE_CUT;
            case HAMMERFISTS -> air?HAMMER_DROP:input==Input.SPECIAL?(sprint?HAMMER_RAM:HAMMER_QUAKE):HAMMER_JAB;
            case WHIPFIST -> air?WHIP_AIR:input==Input.SPECIAL?WHIP_LONGSHOT:WHIP_LASH;
            case NONE,MUSCLE_MASS -> air?AIR_KICK:input==Input.SPECIAL?COMBO_KNEE:sprint?MUSCLE_RUSH:MUSCLE_JAB;
            default -> null;
        };
        return second&&input==Input.ATTACK&&first!=null&&first.nextAttack()!=null?first.nextAttack():first;
    }
    /** Explicit native owner references; enum ordinal is only a stable wire identifier. */
    public WeaponMove nextAttack(){
        return switch(uncharged()){
            case CLAW_AUTO1 -> CLAW_AUTO2;
            case CLAW_AUTO2 -> CLAW_AUTO3;
            case AIR_AUTO1 -> AIR_AUTO2;
            case AIR_AUTO2 -> AIR_AUTO3;
            case AIR_AUTO3 -> AIR_AUTO4;
            case MUSCLE_UPPERCUT,AIR_LAUNCH_FOLLOW -> AIR_AUTO1;
            case BLADE_CUT -> BLADE_RETURN;
            case BLADE_RETURN -> BLADE_COMBO_C;
            case BLADE_COMBO_C -> BLADE_COMBO_C2;
            case BLADE_COMBO_C2 -> BLADE_COMBO_D;
            case HAMMER_JAB -> HAMMER_HOOK;
            case HAMMER_HOOK -> HAMMER_COMBO_C;
            case HAMMER_COMBO_C -> HAMMER_COMBO_D;
            case WHIP_LASH -> WHIP_RETURN;
            case WHIP_RETURN -> WHIP_COMBO_C;
            case MUSCLE_JAB -> MUSCLE_CROSS;
            case MUSCLE_CROSS -> MUSCLE_COMBO_C;
            case COMBO_KICK1 -> MUSCLE_COMBO_C;
            case COMBO_KNEE -> COMBO_AXE_HANDLE;
            case COMBO_SPIN -> COMBO_BACKHAND;
            case COMBO_AXE -> COMBO_MACH;
            case COMBO_AXE_HANDLE -> COMBO_BACKHAND;
            case COMBO_BACKHAND -> COMBO_MACH;
            default -> null;
        };
    }
    public WeaponMove nextSpecial(){
        return switch(uncharged()){
            case CLAW_AUTO1,CLAW_AUTO2 -> COMBO_SPIN;
            case AIR_AUTO1,AIR_AUTO2,AIR_AUTO3 -> AIR_KICK;
            case MUSCLE_JAB -> COMBO_KICK1;
            case MUSCLE_CROSS -> COMBO_KICK1;
            case COMBO_KICK1 -> COMBO_KICK2;
            case COMBO_KNEE -> COMBO_SPIN;
            case COMBO_SPIN -> COMBO_AXE;
            case COMBO_AXE -> COMBO_SPIN_LEFT;
            case COMBO_AXE_HANDLE -> COMBO_AXE;
            case COMBO_BACKHAND -> COMBO_SPIN_LEFT;
            case BLADE_CUT,BLADE_RETURN,BLADE_COMBO_C,BLADE_COMBO_C2 -> BLADE_RUSH;
            case HAMMER_JAB,HAMMER_HOOK,HAMMER_COMBO_C -> HAMMER_COMBO_SPECIAL;
            case WHIP_LASH,WHIP_RETURN,WHIP_COMBO_C -> WHIP_SWEEP;
            default -> null;
        };
    }
    public WeaponMove followUp(Input input){return input==Input.ATTACK?nextAttack():input==Input.SPECIAL?nextSpecial():null;}
    /** Re-evaluate the physical medium at execution, including after a queued jump. */
    public WeaponMove followUp(Input input,PowerForm active,boolean air,boolean sprint){
        if(this==MUSCLE_UPPERCUT&&air&&input==Input.ATTACK)return AIR_LAUNCH_FOLLOW;
        if(input.chord()||air!=aerial())return choose(active,input,air,sprint,false);
        if(air&&input==Input.SPECIAL)return choose(active,input,true,sprint,false);
        if(!air&&active==PowerForm.CLAWS&&input==Input.ATTACK){
            if(this==COMBO_KNEE)return CLAW_AUTO2;
            if(this==COMBO_SPIN||this==COMBO_AXE)return CLAW_AUTO3;
        }
        return followUp(input);
    }
    public WeaponMove uncharged(){
        if(!name().endsWith("_CHARGED"))return this;
        return valueOf(name().substring(0,name().length()-8));
    }
    /** Public fallback aliases never imply that proprietary source clips ship in the mod. */
    public WeaponMove fallback(){
        return switch(uncharged()){
            case CLAW_AUTO1,CLAW_AUTO3,CLAW_SPRINT -> MUSCLE_JAB;
            case CLAW_AUTO2 -> MUSCLE_CROSS;
            case CLAW_GROUNDSPIKE -> HAMMER_QUAKE;
            case MUSCLE_COMBO_C,MUSCLE_SUPERMAN,COMBO_MACH -> MUSCLE_UPPERCUT;
            case AIR_AUTO1,AIR_AUTO2,AIR_AUTO3,AIR_AUTO4,AIR_KICK,AIR_CLAP,AIR_PAIN,AIR_LAUNCH_FOLLOW -> MUSCLE_AIR;
            case COMBO_KICK1,COMBO_KNEE,COMBO_SPIN,COMBO_AXE_HANDLE,COMBO_BACKHAND -> MUSCLE_CROSS;
            case COMBO_KICK2,COMBO_AXE,COMBO_SPIN_LEFT,COMBO_FLOAT -> MUSCLE_UPPERCUT;
            case COMBO_GROUND_POUND,COMBO_CLAP,COMBO_PAIN -> HAMMER_QUAKE;
            case AIR_GROUND_POUND -> HAMMER_DROP;
            case BLADE_COMBO_C,BLADE_COMBO_D -> BLADE_CUT;
            case BLADE_COMBO_C2 -> BLADE_RETURN;
            case HAMMER_COMBO_C -> HAMMER_JAB;
            case HAMMER_COMBO_D -> HAMMER_HOOK;
            case HAMMER_COMBO_SPECIAL -> HAMMER_QUAKE;
            case WHIP_COMBO_C -> WHIP_LASH;
            case WHIP_LONGSHOT -> WHIP_LUNGE;
            default -> uncharged();
        };
    }
    public boolean rush(){return this==BLADE_RUSH||this==HAMMER_RAM||this==WHIP_LUNGE||this==MUSCLE_RUSH;}
    public boolean quake(){return uncharged()==HAMMER_QUAKE||uncharged()==HAMMER_COMBO_SPECIAL||this==HAMMER_DROP||this==HAMMER_RAM||this==COMBO_GROUND_POUND||this==AIR_GROUND_POUND;}
    public boolean frenzy(){return this==BLADE_CLEAVE;}
    public LocalWeaponMotion.ContactBounds contactBounds(int tick){
        return localMotion==null?null:localMotion.contactBounds().get(tick);
    }
    public boolean firstCombo(){return this==BLADE_CUT||this==HAMMER_JAB||this==WHIP_LASH||this==MUSCLE_JAB;}
    public boolean basicCombo(){return firstCombo()||nextAttack()!=null||nextSpecial()!=null||this==BLADE_COMBO_D||uncharged()==HAMMER_COMBO_D||uncharged()==WHIP_COMBO_C||this==MUSCLE_CROSS;}
    public int comboBufferTick(){return form==PowerForm.HAMMERFISTS||form==PowerForm.MUSCLE_MASS||shared()?1:Math.max(1,start-2);}
    /** Source contact bounds mark the low point of a downpunch, independent of a victim. */
    public int groundImpactTick(){
        if(this==BLADE_DIVE)return start;
        if(quake())return start;
        if(form!=PowerForm.HAMMERFISTS)return -1;
        for(int t=start;t<end;t++){var b=contactBounds(t);if(b!=null&&b.minUp()<=.18)return t;}
        return end-1;
    }
    public int comboLinkTick(){
        WeaponMove next=nextAttack()!=null?nextAttack():nextSpecial();
        if(next==null)return duration;
        int sourceTick=SourceComboTiming.linkTick(id,localMotion);
        if(sourceTick>=0){
            // Trim only recovery between grounded ordinary combo hits. Source poses,
            // damage windows, charge holds, dives and receiver scenes keep their clock.
            if(!aerial()&&!landingAttack()&&!rush()&&!frenzy()&&!groundSpike()){
                int safe=Math.max(end+1,end-1+10-next.start);
                int recovery=Math.max(0,sourceTick-safe);
                sourceTick-=Math.min(recovery,(int)Math.ceil(recovery*.15));
            }
            // A reviewed source hook can still precede Minecraft's hurt-resistance
            // boundary. Extend that hook without removing any source contact.
        return Math.min(duration,sourceTick);
        }
        // Until the source event sentinel is fully resolved, retain every active
        // contact and a short follow-through. Do not cancel into the next attack.
        boolean quick=!aerial()&&!landingAttack()&&!rush()&&!frenzy()&&!groundSpike();
        int followThrough=end+com.codex.blacklight.util.MathCompat.clamp((duration-end)/3,quick?1:2,quick?3:4);
        int hurtResistance=end-1+10-next.start;
        return Math.min(duration,Math.max(followThrough,hurtResistance));
    }
    /** Three distinct cuts, separated by ten ticks so normal hurt resistance remains intact. */
    public int contactIndex(int tick){
        if(tick<start||tick>=end)return -1;
        // Separate blade sweeps share one target ledger for this local action.
        // Native cross-window damage deduplication has not been established.
        if(localMotion!=null&&!localMotion.contactWindows().isEmpty())
            return localMotion.contactWindows().stream().anyMatch(window->window.contains(tick))?0:-1;
        return frenzy()?((tick-start)%10<3?(tick-start)/10:-1):0;
    }
    public double contactProgress(double tick){
        if(localMotion!=null&&!localMotion.contactWindows().isEmpty())
            return localMotion.contactWindows().stream().filter(window->window.contains(tick))
                    .findFirst().map(window->window.progress(tick)).orElse(0D);
        double local=frenzy()?(tick-start)%10:tick-start;
        return com.codex.blacklight.util.MathCompat.clamp(local/(frenzy()?2:Math.max(1,end-start-1)),0,1);
    }
}
