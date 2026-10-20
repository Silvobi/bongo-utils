package pl.bongo.bongoutils.social;

/** Monotonic wall time, independent of server TPS. Camera rotation is deliberately ignored. */
public final class Activity {
    public static final long IDLE_NANOS = java.time.Duration.ofMinutes(5).toNanos();
    private Object world;
    private double x,y,z;
    private long lastMoved;
    private boolean afk;
    public Activity(Object world,double x,double y,double z,long now) { reset(world,x,y,z,now); }
    private void reset(Object world,double x,double y,double z,long now) {
        this.world=world;this.x=x;this.y=y;this.z=z;lastMoved=now;
    }
    public boolean afk() { return afk; }
    public boolean sample(Object world,double x,double y,double z,long now) {
        boolean before=afk;
        // Anchor is updated only after movement, so tiny steps accumulate rather than evade detection.
        if(!this.world.equals(world) || Math.abs(this.x-x)>0.001 || Math.abs(this.y-y)>0.001 || Math.abs(this.z-z)>0.001) {
            reset(world,x,y,z,now);afk=false;
        } else if(now-lastMoved>=IDLE_NANOS) afk=true;
        return before!=afk;
    }
    public void toggle(Object world,double x,double y,double z,long now) { reset(world,x,y,z,now);afk=!afk; }
}
