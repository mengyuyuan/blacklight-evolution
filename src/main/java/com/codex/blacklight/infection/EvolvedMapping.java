package com.codex.blacklight.infection;

import com.google.gson.*;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Pure, data-driven lineage choice. Only mature, actually acquired organs vote. */
public final class EvolvedMapping {
    public static final String DEFAULT_FORM="blacklight:blade_evolved";
    public static final String UNCLASSIFIED="blacklight:unclassified";
    private EvolvedMapping(){}
    public record Candidate(String id,String type,Set<String> biases,double weight){
        public Candidate{
            identifier(id);identifier(type);biases=Collections.unmodifiableSet(new TreeSet<>(biases));
            if(biases.isEmpty()||!Double.isFinite(weight)||weight<=0||weight>1_000_000)throw new IllegalArgumentException("Invalid evolved candidate "+id);
        }
    }
    public record Choice(String form,String type,String bias,String table,long seed,boolean fallback,Map<String,Double> scores){
        public Choice{scores=Collections.unmodifiableMap(new TreeMap<>(scores));}
    }
    public static final class Table {
        private final Map<String,String> biases;
        private final Map<String,Map<String,Double>> organs;
        private final Map<String,Candidate> forms;
        private final String fallback,fingerprint;
        public Table(Map<String,String> biases,Map<String,Map<String,Double>> organs,Map<String,Candidate> forms,String fallback){
            if(biases.isEmpty()||biases.size()>64||organs.size()>512||forms.isEmpty()||forms.size()>128)throw new IllegalArgumentException("Evolved table size");
            var labels=new TreeMap<String,String>();biases.forEach((id,label)->{identifier(id);if(label==null||label.isBlank()||label.length()>80)throw new IllegalArgumentException("Bias label "+id);labels.put(id,label);});
            var weights=new TreeMap<String,Map<String,Double>>();
            organs.forEach((id,values)->{
                identifier(id);if(values.isEmpty()||values.size()>16)throw new IllegalArgumentException("Empty/oversized organ weights "+id);
                var clean=new TreeMap<String,Double>();values.forEach((bias,weight)->{
                    if(!labels.containsKey(bias)||weight==null||!Double.isFinite(weight)||weight<=0||weight>100)throw new IllegalArgumentException("Invalid organ bias "+id+" / "+bias);
                    clean.put(bias,weight);
                });weights.put(id,Collections.unmodifiableMap(clean));
            });
            var candidates=new TreeMap<String,Candidate>();forms.forEach((id,candidate)->{
                if(!id.equals(candidate.id())||!labels.keySet().containsAll(candidate.biases()))throw new IllegalArgumentException("Unknown candidate bias "+id);
                candidates.put(id,candidate);
            });
            if(!candidates.containsKey(fallback))throw new IllegalArgumentException("Missing evolved fallback "+fallback);
            this.biases=Collections.unmodifiableMap(labels);this.organs=Collections.unmodifiableMap(weights);this.forms=Collections.unmodifiableMap(candidates);this.fallback=fallback;
            this.fingerprint=hash(labels.toString()+weights+candidates+fallback);
        }
        public Map<String,String> biases(){return biases;}
        public Map<String,Map<String,Double>> organs(){return organs;}
        public Map<String,Candidate> forms(){return forms;}
        public String fallback(){return fallback;}
        public String fingerprint(){return fingerprint;}
        /** Divide by the maximum first: preserve relative strengths without overflowing. */
        public Map<String,Double> score(TissueInheritance genes){
            double largest=genes.organs().entrySet().stream().filter(e->e.getValue().mature()&&e.getValue().pending()==0&&organs.containsKey(e.getKey().key()))
                    .mapToDouble(e->e.getValue().integrated()).max().orElse(0);
            var scores=new TreeMap<String,Double>();if(largest<=0)return scores;
            genes.organs().forEach((core,organ)->{
                var weights=organs.get(core.key());if(weights==null||!organ.mature()||organ.pending()>0||organ.integrated()<=0)return;
                double strength=organ.integrated()/largest;weights.forEach((bias,weight)->scores.merge(bias,strength*weight,Double::sum));
            });return Collections.unmodifiableMap(scores);
        }
        public Choice choose(TissueInheritance genes,long seed,Set<String> availableTypes){
            var scores=score(genes);double maximum=scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            var leaders=new TreeSet<String>();scores.forEach((id,score)->{if(maximum>0&&Math.abs(score-maximum)<=Math.max(1,maximum)*1e-9)leaders.add(id);});
            var eligible=forms.values().stream().filter(c->availableTypes.contains(c.type())&&!Collections.disjoint(c.biases(),leaders)).toList();
            boolean usedFallback=eligible.isEmpty();
            if(usedFallback){
                var candidate=forms.get(fallback);
                if(!availableTypes.contains(candidate.type()))throw new IllegalStateException("Evolved fallback type is not installed: "+candidate.type());
                eligible=List.of(candidate);
            }
            var random=new SplittableRandom(seed);double total=eligible.stream().mapToDouble(Candidate::weight).sum(),roll=random.nextDouble(total);
            Candidate selected=eligible.get(eligible.size()-1);for(var c:eligible){roll-=c.weight();if(roll<0){selected=c;break;}}
            var matching=new ArrayList<String>();for(var leader:leaders)if(selected.biases().contains(leader))matching.add(leader);
            if(matching.isEmpty())matching.addAll(leaders);
            String bias=matching.isEmpty()?UNCLASSIFIED:matching.get(random.nextInt(matching.size()));
            return new Choice(selected.id(),selected.type(),bias,fingerprint,seed,usedFallback,scores);
        }
    }
    public static Table read(Reader reader){
        var root=JsonParser.parseReader(reader).getAsJsonObject();if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Evolved mapping schema");
        var biases=new TreeMap<String,String>();root.getAsJsonObject("biases").entrySet().forEach(e->biases.put(e.getKey(),e.getValue().getAsString()));
        var organs=new TreeMap<String,Map<String,Double>>();root.getAsJsonObject("organs").entrySet().forEach(e->{
            var weights=new TreeMap<String,Double>();e.getValue().getAsJsonObject().entrySet().forEach(w->weights.put(w.getKey(),w.getValue().getAsDouble()));organs.put(e.getKey(),weights);
        });
        var forms=new TreeMap<String,Candidate>();for(var element:root.getAsJsonArray("forms")){
            var value=element.getAsJsonObject();String id=value.get("id").getAsString();var axes=new TreeSet<String>();value.getAsJsonArray("biases").forEach(b->axes.add(b.getAsString()));
            if(forms.put(id,new Candidate(id,value.get("type").getAsString(),axes,value.get("weight").getAsDouble()))!=null)throw new IllegalArgumentException("Duplicate evolved form "+id);
        }
        return new Table(biases,organs,forms,root.get("fallback").getAsString());
    }
    private static void identifier(String id){if(!TissueInheritance.validSpecies(id))throw new IllegalArgumentException("Invalid evolved mapping ID: "+id);}
    private static String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
