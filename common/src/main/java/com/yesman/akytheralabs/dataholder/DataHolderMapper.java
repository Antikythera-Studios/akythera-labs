package com.yesman.akytheralabs.dataholder;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/// Central index of `engine type → editor holder factory` for types that are
/// identified by registry name *inside* another asset (notify / notify-state
/// inside an AnimationSequence's notify track). Project-resource-keyed types
/// (AnimationSequence, BlendSpace2D, AnimationMontage) belong to
/// [com.yesman.akytheralabs.AkytheraProject#getDataHolder], not here.
///
/// `LinkedHashMap` preserves registration order so the "Add Notify Here"
/// picker shows entries deterministically.
public final class DataHolderMapper {
    private static final Map<MapCodec<? extends AnimationNotify<?>>, Supplier<AnimationNotifyHolder<?>>> NOTIFY_HOLDERS = new LinkedHashMap<>();
    private static final Map<MapCodec<? extends AnimationNotifyState<?>>, Supplier<AnimationNotifyStateHolder<?>>> NOTIFY_STATE_HOLDERS = new LinkedHashMap<>();

    private DataHolderMapper() {}

    public static void registerNotify(MapCodec<? extends AnimationNotify<?>> codec, Supplier<AnimationNotifyHolder<?>> factory) {
        NOTIFY_HOLDERS.put(codec, factory);
    }

    public static void registerNotifyState(MapCodec<? extends AnimationNotifyState<?>> codec, Supplier<AnimationNotifyStateHolder<?>> factory) {
        NOTIFY_STATE_HOLDERS.put(codec, factory);
    }

    public static @Nullable Supplier<AnimationNotifyHolder<?>> notifyHolderFor(MapCodec<? extends AnimationNotify<?>> codec) {
        return NOTIFY_HOLDERS.get(codec);
    }

    public static @Nullable Supplier<AnimationNotifyStateHolder<?>> notifyStateHolderFor(MapCodec<? extends AnimationNotifyState<?>> codec) {
        return NOTIFY_STATE_HOLDERS.get(codec);
    }

    public static Map<MapCodec<? extends AnimationNotify<?>>, Supplier<AnimationNotifyHolder<?>>> allNotifyHolders() {
        return NOTIFY_HOLDERS;
    }

    public static Map<MapCodec<? extends AnimationNotifyState<?>>, Supplier<AnimationNotifyStateHolder<?>>> allNotifyStateHolders() {
        return NOTIFY_STATE_HOLDERS;
    }
}