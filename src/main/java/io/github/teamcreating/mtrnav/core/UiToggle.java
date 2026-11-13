package io.github.teamcreating.mtrnav.core;

/** Ore binary switch's 250 ms stepped travel, including its stretch and one-pixel return. */
public final class UiToggle {
    public record Frame(int offset,int width) {}
    private boolean initialized,on;
    private long changed;
    public void update(boolean value,long now) {
        if(!initialized){initialized=true;on=value;changed=now-250_000_000L;}
        else if(on!=value){on=value;changed=now;}
    }
    public Frame frame(long now) {
        long elapsed=Math.max(0,now-changed);
        if(elapsed>=250_000_000L)return new Frame(on?14:0,16);
        int step=(int)(elapsed/62_500_000L);
        int travel=switch(step){case 1->3;case 2->14;case 3->13;default->0;};
        int width=step==1?20:16;
        return new Frame(on?travel:30-width-travel,width);
    }
}
