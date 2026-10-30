package com.codex.blacklight.city;

/** A command-core observation is evidence, not a percentage of remaining building blocks. */
final class MilitaryBaseHealth {
    enum Status { UNKNOWN, ACTIVE, OBSERVING, INACTIVE }
    private boolean inactive;
    private long missingSince=-1;
    private Status status=Status.UNKNOWN;
    MilitaryBaseHealth(boolean previouslyInactive){inactive=previouslyInactive;}
    boolean observe(long now,boolean allLoaded,boolean anyTerminal){
        boolean previous=inactive;
        if(anyTerminal){inactive=false;missingSince=-1;status=Status.ACTIVE;}
        else if(!allLoaded){missingSince=-1;status=Status.UNKNOWN;}
        else if(inactive)status=Status.INACTIVE;
        else{
            if(missingSince<0||now<missingSince)missingSince=now;
            if(now-missingSince>=200){inactive=true;status=Status.INACTIVE;}else status=Status.OBSERVING;
        }
        return previous!=inactive;
    }
    boolean operational(){return status==Status.ACTIVE;}
    boolean inactive(){return inactive;}
    String label(){return inactive&&status==Status.UNKNOWN?"INACTIVE_UNLOADED":status.name();}
}
