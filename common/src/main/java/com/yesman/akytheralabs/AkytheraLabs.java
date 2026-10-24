package com.yesman.akytheralabs;

import com.yesman.akythera.api.animation.notify.impl.CancelOnMoveNotifyState;
import com.yesman.akythera.api.animation.notify.impl.ExtendDeathTimeNotifyState;
import com.yesman.akythera.api.animation.notify.impl.LockMovementNotifyState;
import com.yesman.akythera.api.animation.notify.impl.LogNotify;
import com.yesman.akythera.api.animation.notify.impl.LogNotifyState;
import com.yesman.akythera.api.animation.notify.impl.PlaySoundNotify;
import com.yesman.akythera.api.animation.notify.impl.StartRagdollNotify;
import com.yesman.akythera.api.animation.notify.impl.TrailParticleNotifyState;
import com.yesman.akytheralabs.dataholder.DataHolderMapper;
import com.yesman.akytheralabs.akythera.animation.notify.impl.CancelOnMoveNotifyStateHolder;
import com.yesman.akytheralabs.akythera.animation.notify.impl.ExtendDeathTimeNotifyStateHolder;
import com.yesman.akytheralabs.akythera.animation.notify.impl.LockMovementNotifyStateHolder;
import com.yesman.akytheralabs.akythera.animation.notify.impl.LogNotifyHolder;
import com.yesman.akytheralabs.akythera.animation.notify.impl.LogNotifyStateHolder;
import com.yesman.akytheralabs.akythera.animation.notify.impl.PlaySoundNotifyHolder;
import com.yesman.akytheralabs.akythera.animation.notify.impl.StartRagdollNotifyHolder;
import com.yesman.akytheralabs.akythera.animation.notify.impl.TrailParticleNotifyStateHolder;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;

public class AkytheraLabs {
    private AkytheraLabs() {
    }

    public static final String MODID = "akytheralabs";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /// Creates an identifier that points to an Akythera resource.
    public static @NonNull Identifier identifier(@NonNull final String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /// Creates an identifier that points to an Akythera resource.
    public static @NonNull Identifier samplePack(@NonNull final String path) {
        return Identifier.fromNamespaceAndPath("akythera_sample_pack", path);
    }

    /// Registers every `engine notify type → editor holder factory` pair the
    /// editor knows about. Called once from each platform's client init.
    public static void bootstrapDataHolderMappings() {
        DataHolderMapper.registerNotify(LogNotify.CODEC, LogNotifyHolder::new);
        DataHolderMapper.registerNotify(PlaySoundNotify.CODEC, PlaySoundNotifyHolder::new);
        DataHolderMapper.registerNotify(StartRagdollNotify.CODEC, StartRagdollNotifyHolder::new);
        DataHolderMapper.registerNotifyState(LogNotifyState.CODEC, LogNotifyStateHolder::new);
        DataHolderMapper.registerNotifyState(LockMovementNotifyState.CODEC, LockMovementNotifyStateHolder::new);
        DataHolderMapper.registerNotifyState(CancelOnMoveNotifyState.CODEC, CancelOnMoveNotifyStateHolder::new);
        DataHolderMapper.registerNotifyState(ExtendDeathTimeNotifyState.CODEC, ExtendDeathTimeNotifyStateHolder::new);
        DataHolderMapper.registerNotifyState(TrailParticleNotifyState.CODEC, TrailParticleNotifyStateHolder::new);
    }
}
