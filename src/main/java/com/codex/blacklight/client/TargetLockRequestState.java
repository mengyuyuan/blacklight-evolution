package com.codex.blacklight.client;

/** Prediction survives network latency; only the matching acknowledgement can settle a request. */
public final class TargetLockRequestState {
    private int requestId,desiredId=-1,acknowledgedId=-1,desiredRegion=-1,acknowledgedRegion=-1;
    private boolean pending,attachmentObserved;
    private long firstOccludedTick = -1;

    /** A banking aircraft can briefly pass behind a terrain edge. Repeated frame
     * queries during one game tick must not consume the sight-loss allowance. */
    public boolean retainAircraftSight(boolean visible, long tick) {
        if (visible) {
            firstOccludedTick = -1;
            return true;
        }
        if (firstOccludedTick < 0) firstOccludedTick = tick;
        return tick - firstOccludedTick < 10;
    }
    public int requestId(){return requestId;}
    public int desiredId(){return desiredId;}
    public int acknowledgedId(){return acknowledgedId;}
    public int desiredRegion(){return desiredRegion;}
    public int acknowledgedRegion(){return acknowledgedRegion;}
    public boolean pending(){return pending;}
    public int request(int id){return request(id,-1);}
    public int request(int id,int region) {
        if(region< -1||region>2||id<0&&region!=-1)throw new IllegalArgumentException("Invalid target region");
        if(id< -1)throw new IllegalArgumentException("Invalid target ID");
        requestId=requestId==Integer.MAX_VALUE?1:requestId+1;
        firstOccludedTick = -1;
        desiredId=id;desiredRegion=region;pending=true;attachmentObserved=false;return requestId;
    }
    public boolean acknowledge(int sequence,int acceptedId){return acknowledge(sequence,acceptedId,-1);}
    public boolean acknowledge(int sequence,int acceptedId,int acceptedRegion) {
        if(!pending||sequence!=requestId||acceptedId< -1||acceptedRegion< -1||acceptedRegion>2||acceptedId<0&&acceptedRegion!=-1)return false;
        acknowledgedId=desiredId=acceptedId;acknowledgedRegion=desiredRegion=acceptedRegion;pending=false;attachmentObserved=false;return true;
    }
    /** Synced attachments still deliver later server cleanup to the owner and observers. */
    public boolean observeAttachment(int id){return observeAttachment(id,-1);}
    public boolean observeAttachment(int id,int region) {
        if(pending)return false;
        if(id==desiredId&&region==desiredRegion){attachmentObserved=true;return false;}
        if(!attachmentObserved)return false;
        desiredId=acknowledgedId=desiredRegion=acknowledgedRegion=-1;attachmentObserved=false;return true;
    }
    public void resetOwner() {
        firstOccludedTick = -1;
        // Keep sequence monotonic across respawn/world changes, invalidating old replies.
        requestId=requestId==Integer.MAX_VALUE?1:requestId+1;
        desiredId=acknowledgedId=desiredRegion=acknowledgedRegion=-1;pending=false;attachmentObserved=false;
    }
}
