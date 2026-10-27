package com.codex.blacklight.infection;

import java.util.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** One anatomical assignment per functional socket. Acquired powers remain in the server inventory. */
public final class TissueOrganLayout {
    private final Map<TissueCore,Set<String>> mounts;
    private final String body;
    private TissueOrganLayout(Map<TissueCore,Set<String>> mounts,String body){this.mounts=Map.copyOf(mounts);this.body=body;}
    public boolean mounted(TissueCore core,Bone bone){return mounts.getOrDefault(core,Set.of()).contains(bone.path());}
    public boolean fallback(TissueCore core,Bone bone){return mounts.getOrDefault(core,Set.of()).isEmpty()&&bone.path().equals(body);}
    public Set<String> sockets(TissueCore core){return mounts.getOrDefault(core,Set.of());}
    public static boolean oldDistalKit(String name){return name.startsWith("claw_")||name.startsWith("blade_")||name.equals("ulnar_blade");}
    public static int bladeSide(long seed){return TissueGrowth.sample(seed,1931)<.5F?-1:1;}
    public static TissueOrganLayout fit(List<Bone> source,long seed){
        var bones=source.stream().filter(Objects::nonNull).filter(b->b.box().usable()).sorted(Comparator.comparing(Bone::path)).toList();
        Bone trunk=bones.stream().max(Comparator.comparingDouble(b->b.box().volume()*(b.role()==0?10:1))).orElse(null);
        if(trunk==null)return new TissueOrganLayout(Map.of(),"");
        float centre=trunk.modelCentre().x();int side=bladeSide(seed);
        var fore=bones.stream().filter(b->b.role()==2||b.role()==3).toList();
        var hind=bones.stream().filter(b->b.role()==4||b.role()==5).toList();
        var tails=bones.stream().filter(b->b.role()==6&&tailBone(b.path())).toList();
        Bone head=bones.stream().filter(b->b.role()==1).max(Comparator.comparingDouble(b->b.box().volume())).orElse(null);
        var assignments=new EnumMap<TissueCore,Set<String>>(TissueCore.class);
        put(assignments,TissueCore.BLADE,pick(fore,side,centre,false));
        put(assignments,TissueCore.CHELA,pick(fore,-side,centre,false));
        put(assignments,TissueCore.HYDROSTAT,tails.isEmpty()?pick(fore,-side,centre,false):tails.stream().max(Comparator.comparingDouble(b->b.box().volume())).orElse(null));
        var springs=new HashSet<String>();var support=hind.isEmpty()?fore:hind;
        for(int s:new int[]{-1,1}){var bone=pick(support,s,centre,true);if(bone!=null)springs.add(bone.path());}
        assignments.put(TissueCore.TENDON,Set.copyOf(springs));
        var hydraulic=new HashSet<String>();for(var bone:bones)if(bone.role()>=2&&bone.role()<=5)hydraulic.add(bone.path());
        assignments.put(TissueCore.HYDRAULIC,Set.copyOf(hydraulic));
        put(assignments,TissueCore.HORN,head);put(assignments,TissueCore.PHARYNX,head);
        put(assignments,TissueCore.SADDLE,pick(fore,-side,centre,false));
        put(assignments,TissueCore.CLUB,pick(fore,side,centre,false));
        put(assignments,TissueCore.TONGUE,head);
        put(assignments,TissueCore.CNIDOCYTE,pick(fore,-side,centre,false));
        for(var core:new TissueCore[]{TissueCore.GIZZARD,TissueCore.FERMENT,TissueCore.ABSORPTIVE,TissueCore.PANCREAS,TissueCore.FAT})put(assignments,core,trunk.role()==0?trunk:null);
        put(assignments,TissueCore.CROP,head);
        put(assignments,TissueCore.EXTRAORAL,head);put(assignments,TissueCore.GILL_RAKER,head);
        for(var core:TissueCore.values())if(core.vital())put(assignments,core,core==TissueCore.GILL_LAMELLA?head:trunk.role()==0?trunk:null);
        put(assignments,TissueCore.VASCULAR,trunk.role()==0?trunk:null);
        return new TissueOrganLayout(assignments,trunk.path());
    }
    private static void put(Map<TissueCore,Set<String>> map,TissueCore core,Bone bone){map.put(core,bone==null?Set.of():Set.of(bone.path()));}
    private static Bone pick(List<Bone> bones,int side,float centre,boolean rear){
        var own=bones.stream().filter(b->side*(b.modelCentre().x()-centre)>.01F).toList();var available=own.isEmpty()?bones:own;
        return available.stream().min(Comparator.comparingDouble((Bone b)->(rear?-1:1)*b.modelCentre().z())
                .thenComparing(Comparator.comparingDouble((Bone b)->b.box().volume()).reversed()).thenComparing(Bone::path)).orElse(null);
    }
}
