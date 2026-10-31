package com.codex.blacklight.military.vehicle;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Objects;

public final class VehicleControlAuthority {
    public static final int PRIMARY_FIRE=1, SECONDARY_FIRE=2, NEXT_AMMUNITION=4, NEXT_SEAT=8, BRAKE=16;
    public record Session(UUID connectedPlayer, String dimension, UUID actualVehicle, boolean alive) {}
    public record Input(UUID vehicle, UUID lease, long sequence, double throttle, double steering,
            double aimYaw, double aimPitch, int actions) {}
    public record State(double throttle, double steering, double aimYaw, double aimPitch,
            boolean primaryFire, boolean secondaryFire, boolean brake, long sequence, int actions) {}
    private final UUID vehicle;
    private final String dimension;
    private final VehicleSeats seats;
    private final Map<UUID,Lease> leases=new HashMap<>();
    private static final class Lease {
        final UUID token=UUID.randomUUID(); final long revision;
        final ArrayDeque<Long> received=new ArrayDeque<>();
        long sequence=-1,tick=-1; State state=stopped(-1);
        Lease(long revision) { this.revision=revision; }
    }
    public VehicleControlAuthority(UUID vehicle, String dimension, VehicleSeats seats) {
        this.vehicle=Objects.requireNonNull(vehicle); this.dimension=Objects.requireNonNull(dimension); this.seats=Objects.requireNonNull(seats);
    }
    /** Session must be assembled from the server connection, never decoded from a client packet. */
    public UUID grant(Session session) {
        if(!valid(session) || !controllingJob(session.connectedPlayer())) { if(session!=null) revoke(session.connectedPlayer()); return null; }
        var lease=new Lease(seats.revision()); leases.put(session.connectedPlayer(),lease); return lease.token;
    }
    public boolean accept(Session session, Input input, long tick) {
        Lease lease=current(session); if(lease==null || input==null || tick<0 || tick<lease.tick) return false;
        if(!vehicle.equals(input.vehicle()) || !lease.token.equals(input.lease()) || input.sequence()<0 || input.sequence()<=lease.sequence
                || !unit(input.throttle()) || !unit(input.steering()) || !angle(input.aimYaw(),180) || !angle(input.aimPitch(),90)
                || (input.actions() & ~(PRIMARY_FIRE|SECONDARY_FIRE|NEXT_AMMUNITION|NEXT_SEAT|BRAKE)) != 0) return false;
        var job=seats.job(session.connectedPlayer());
        if(job!=VehicleDefinition.Job.DRIVER && (input.throttle()!=0 || input.steering()!=0)) return false;
        int actions=input.actions();
        if(job==VehicleDefinition.Job.DRIVER && seats.hasGunner()) actions&=~(PRIMARY_FIRE|SECONDARY_FIRE);
        while(!lease.received.isEmpty() && tick-lease.received.peekFirst()>=20) lease.received.removeFirst();
        if(lease.received.size()>=20) return false;
        lease.received.addLast(tick); lease.tick=tick; lease.sequence=input.sequence();
        boolean brake=(actions&BRAKE)!=0;
        lease.state=new State(brake?0:input.throttle(),brake?0:input.steering(),input.aimYaw(),input.aimPitch(),
                (actions&PRIMARY_FIRE)!=0,(actions&SECONDARY_FIRE)!=0,brake,input.sequence(),actions);
        return true;
    }
    public State state(Session session, long tick) {
        if(tick<0) throw new IllegalArgumentException("Negative server tick");
        Lease lease=current(session);
        if(lease==null) return stopped(-1);
        if(lease.tick<0 || tick<lease.tick || tick-lease.tick>=5)
            return new State(0,0,lease.state.aimYaw(),lease.state.aimPitch(),false,false,true,lease.sequence,0);
        return lease.state;
    }
    public void revoke(UUID player) { leases.remove(player); }
    public void revokeAll() { leases.clear(); }
    private Lease current(Session session) {
        if(session==null) return null;
        Lease lease=leases.get(session.connectedPlayer());
        if(lease!=null && (!valid(session) || lease.revision!=seats.revision() || !controllingJob(session.connectedPlayer()))) {
            revoke(session.connectedPlayer()); return null;
        }
        return lease;
    }
    private boolean valid(Session session) {
        return session!=null && session.connectedPlayer()!=null && session.alive() && dimension.equals(session.dimension())
                && vehicle.equals(session.actualVehicle()) && seats.seatOf(session.connectedPlayer())!=null;
    }
    private boolean controllingJob(UUID player) { var job=seats.job(player); return job==VehicleDefinition.Job.DRIVER || job==VehicleDefinition.Job.GUNNER; }
    private static boolean unit(double value) { return Double.isFinite(value) && value>=-1 && value<=1; }
    private static boolean angle(double value,double max) { return Double.isFinite(value) && value>=-max && value<=max; }
    private static State stopped(long sequence) { return new State(0,0,0,0,false,false,true,sequence,0); }
}
