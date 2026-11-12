package com.codex.blacklight.client.renderer;

import com.codex.blacklight.network.RagdollImpactPayload;
import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
import java.util.ArrayList;
import java.util.List;

/** A physical blade can cut a descendant again; completed geometry must not be
 * replayed onto it. Retain motion recorded while the previous job was running. */
final class BladeCutSequence {
    private final List<BladeSpan> samples=new ArrayList<>();
    private int generation;
    int generation(){return generation;}
    String key(String edge){return edge+" #"+generation;}
    List<BladeSpan> snapshot(){return List.copyOf(samples);}
    void record(BladeSpan sample){
        if(!sample.timed())throw new IllegalArgumentException("Consume sample requires its render time");
        if(!samples.isEmpty()&&(sample.time()<samples.getLast().time()||samples.getLast().near(sample)))return;
        samples.add(sample);
        while(samples.size()>RagdollImpactPayload.MAX_BLADE_SPANS)
            for(int i=samples.size()-2;i>0;i-=2)samples.remove(i);
    }
    void advance(BladeSpan completed){
        if(!completed.timed())throw new IllegalArgumentException("Completed cut requires its sample time");
        samples.removeIf(s->s.time()<=completed.time());samples.addFirst(completed);generation++;
    }
    BladeCutSequence copy(){var copy=new BladeCutSequence();copy.samples.addAll(samples);copy.generation=generation;return copy;}
}
