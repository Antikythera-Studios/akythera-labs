package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationStateMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/// Reaches the driver of the machine's current state. `AnimationStateMachine` stands in front of that
/// driver and forwards to it, so a caller that needs the driver's own type has to step through.
@Mixin(AnimationStateMachine.class)
public interface AnimationStateMachineAccessor {
    @Invoker("getCurrentStateDriver")
    AnimationDriver akytheralabs$getCurrentStateDriver();
}
