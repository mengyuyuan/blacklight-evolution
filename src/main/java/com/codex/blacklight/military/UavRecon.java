package com.codex.blacklight.military;

import java.util.UUID;

/** Minecraft scan/transmission timings, not claimed as decoded source-game constants. */
public final class UavRecon {
    public static final int SCAN_TICKS=60,SIGNAL_TICKS=40,REPORT_TICKS=100,RETRY_TICKS=200,SORTIE_TICKS=3600;
    public enum Event { NONE, DETECTED, REQUEST_SUPPORT }
    private UUID target;private int scan,signal,reportWait,supportWait;
    public int scan(){return scan;}public int signal(){return signal;}public int reportWait(){return reportWait;}public int supportWait(){return supportWait;}
    public boolean ready(UUID id){return id!=null&&id.equals(target)&&scan==SCAN_TICKS&&signal==SIGNAL_TICKS&&supportWait==0;}
    private void cooldown(){if(reportWait>0)reportWait--;if(supportWait>0)supportWait--;}
    /** A visible vehicle may carry an already recognized contact. Following it is
     * not a fresh viral scan and cannot complete the ground-support transmission. */
    public boolean trackRecognized(UUID id,boolean visible,boolean enabled){
        cooldown();signal=0;
        if(!enabled||!visible||id==null||!id.equals(target)||scan!=SCAN_TICKS){interrupt();return false;}
        return true;
    }
    public Event tick(UUID id,boolean visible,boolean enabled){
        cooldown();
        if(!enabled||id==null){interrupt();return Event.NONE;}
        if(!id.equals(target)){interrupt();target=id;reportWait=0;}
        if(!visible){scan=Math.max(0,scan-4);signal=0;if(scan==0)target=null;return Event.NONE;}
        scan=Math.min(SCAN_TICKS,scan+1);
        if(scan<SCAN_TICKS)return Event.NONE;
        boolean report=reportWait==0;if(report)reportWait=REPORT_TICKS;
        if(supportWait==0)signal=Math.min(SIGNAL_TICKS,signal+1);
        if(signal==SIGNAL_TICKS)return Event.REQUEST_SUPPORT;
        return report?Event.DETECTED:Event.NONE;
    }
    public void dispatched(boolean success){supportWait=success?SORTIE_TICKS:RETRY_TICKS;signal=0;}
    public void interrupt(){target=null;scan=signal=0;}
    /** A load never resumes stale acquisition, but also never refills the support lease. */
    public void restoreCooldowns(int report,int support){interrupt();reportWait=Math.max(0,Math.min(REPORT_TICKS,report));supportWait=Math.max(0,Math.min(SORTIE_TICKS,support));}
}
