package com.codex.blacklight.client.animation;

import com.codex.blacklight.network.RagdollImpactPayload;
import java.util.ArrayList;
import java.util.List;

/** Original server packets held until a native boss's death becomes observable.
 * Lifecycle/death observation belongs to AdaptiveRagdoll, never a timer here. */
public final class RagdollFatalHandoff {
    private static final int LIMIT=8;
    private final List<RagdollImpactPayload> hits=new ArrayList<>();

    private static boolean startsBody(RagdollImpactPayload hit){
        return !hit.refinement()&&RagdollDrive.fullImpact(hit);
    }
    public void add(RagdollImpactPayload hit){
        if(!hit.fatal())return;
        if(hits.size()<LIMIT)hits.add(hit);
        // A run of refinements must not crowd out the first actual death impulse.
        else if(startsBody(hit)&&hits.stream().noneMatch(RagdollFatalHandoff::startsBody))hits.set(LIMIT-1,hit);
    }
    /** An existing body may receive every original packet, including refinements. */
    public List<RagdollImpactPayload> impacts(){return List.copyOf(hits);}

    /** Refinements/short feedback cannot create a corpse on their own. Put the
     * qualifying first impact first; never rewrite its server time or fatality. */
    public List<RagdollImpactPayload> bodyInputs(){
        int first=-1;
        for(int i=0;i<hits.size();i++)if(startsBody(hits.get(i))){first=i;break;}
        if(first<0)return List.of();
        var ordered=new ArrayList<RagdollImpactPayload>(hits.size());
        ordered.add(hits.get(first));
        for(int i=0;i<hits.size();i++)if(i!=first)ordered.add(hits.get(i));
        return List.copyOf(ordered);
    }
}
