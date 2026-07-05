package com.yesman.akytheralabs.compat.playeranimlibrary;

import com.yesman.akythera.compat.ModMixinPlugin;
import org.jspecify.annotations.NonNull;

/// Gates `akytheralabs-compat.player_animation_library.mixins.json` on PAL being installed —
/// PAL is an optional dependency, so its compat mixins must not apply (or load their classes)
/// without it. Same pattern as the engine's `PALMixinPlugin`.
public final class PALMixinPlugin extends ModMixinPlugin {
    @Override
    public @NonNull String getModId() {
        return "player_animation_library";
    }
}