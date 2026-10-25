package io.github.teamcreating.mtrnav.mixin;

import org.mtr.core.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = org.mtr.MTR.class, remap = false)
public interface MtrAccessor {
    @Accessor("main")
    static Main navigationMain() { throw new AssertionError(); }
}
