package io.github.teamcreating.mtrnav.core;

/** Pointer/keyboard press ownership with release activation and short face travel. */
public final class UiPress {
    private boolean down;
    private long changed;
    private double from;
    public boolean down() { return down; }
    public boolean press(long now) {
        if(down)return false;
        from=depth(now);down=true;changed=now;return true;
    }
    public boolean release(boolean inside,long now) {
        if(!down)return false;
        from=depth(now);down=false;changed=now;return inside;
    }
    public void cancel(long now) { release(false,now); }
    public double depth(long now) {
        double t=Math.clamp((now-changed)/(down?60_000_000.0:100_000_000.0),0,1);
        double eased=1-Math.pow(1-t,3);
        return from+((down?1:0)-from)*eased;
    }
}
