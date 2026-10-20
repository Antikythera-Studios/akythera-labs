package com.yesman.akytheralabs.dataholder;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.gui.widget.script.CompileFailure;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/// Shared boilerplate for [DataHolder] implementations: the holder's own [ResourceKey], pushed in
/// by [ProjectObjectRegistry] at register / rekey time so bind callbacks can call back into the
/// project without an extra map lookup — plus the deserialization-failure state ([#markFailed],
/// [#retainRaw]) and the [#guarded] codec wrapper that fills it.
public abstract class AbstractDataHolder<T> implements DataHolder<T> {
    @Nullable
    private ResourceKey<T> ownResourceKey;

    @Nullable
    private Exception deserializationException;

    @Nullable
    private JsonElement retainedRaw;

    @Nullable
    private Exception objectCreationFailure;

    @Nullable
    private CompileFailure compileFailure;

    public @Nullable ResourceKey<T> getOwnResourceKey() {
        return ownResourceKey;
    }

    public void setOwnResourceKey(@NonNull ResourceKey<T> key) {
        this.ownResourceKey = key;
    }

    @Override
    public @Nullable Exception getDeserializationFailureException() {
        return deserializationException;
    }

    @Override
    public void markFailed(Exception exception) {
        deserializationException = exception;
    }

    @Override
    public void retainRaw(JsonElement raw) {
        retainedRaw = raw;
    }

    @Override
    public @Nullable JsonElement retainedRaw() {
        return retainedRaw;
    }

    @Override
    public void markResolved() {
        deserializationException = null;
        retainedRaw = null;
    }

    @Override
    public void markObjectFailed(@Nullable Exception exception) {
        objectCreationFailure = exception;
    }

    @Override
    public @Nullable Exception getObjectCreationFailure() {
        return objectCreationFailure;
    }

    @Override
    public void markCompileFailed(@Nullable CompileFailure failure) {
        compileFailure = failure;
    }

    @Override
    public @Nullable CompileFailure getCompileFailure() {
        return compileFailure;
    }

    /// The first nested decode failure among a group of child holders — a child's own decode failure,
    /// or one it nests in turn — or null when all are healthy. Convenience for an aggregate holder's
    /// [DataHolder#getNestedFailureException] override (patch components, notify rows).
    public static @Nullable Exception firstNestedFailure(Iterable<? extends DataHolder<?>> children) {
        for (DataHolder<?> child : children) {
            Exception own = child.getDeserializationFailureException();
            if (own != null) return own;

            Exception nested = child.getNestedFailureException();
            if (nested != null) return nested;
        }

        return null;
    }

    /// Never-failing wrapper around a holder codec — the per-module exception guard.
    ///
    /// Decode: a failure (or a thrown exception — some inner codecs `Enum.valueOf` inside `xmap`)
    /// yields `defaultFactory`'s instance carrying the failure ([DataHolder#markFailed]) **and the
    /// element's original JSON** ([DataHolder#retainRaw]). Encode: while the failure stands, the
    /// retained JSON is re-emitted verbatim — a save can never wipe data the editor couldn't read.
    public static <H extends DataHolder<?>> Codec<H> guarded(Codec<H> inner, Supplier<H> defaultFactory) {
        return new Codec<>() {
            @Override
            public <D> DataResult<Pair<H, D>> decode(DynamicOps<D> ops, D input) {
                String message;

                try {
                    DataResult<Pair<H, D>> result = inner.decode(ops, input);
                    if (result.result().isPresent()) return result;
                    message = result.error().map(DataResult.Error::message).orElse("Unknown deserialization error");
                } catch (Exception e) {
                    message = e.toString();
                }

                AkytheraLabs.LOGGER.error("Guarded decode failed, keeping raw form: {}", message);
                H fallback = defaultFactory.get();
                fallback.markFailed(new IllegalStateException(message));
                fallback.retainRaw(new Dynamic<>(ops, input).convert(JsonOps.INSTANCE).getValue());

                return DataResult.success(Pair.of(fallback, ops.empty()));
            }

            @Override
            public <D> DataResult<D> encode(H input, DynamicOps<D> ops, D prefix) {
                JsonElement raw = input.retainedRaw();

                if (input.getDeserializationFailureException() != null && raw != null) {
                    return DataResult.success(new Dynamic<>(JsonOps.INSTANCE, raw).convert(ops).getValue());
                }

                return inner.encode(input, ops, prefix);
            }
        };
    }

    /// Component variant of [#guarded]: the fallback depends on WHY the decode failed. A known
    /// `type` whose body is malformed falls back to that type's default holder — opened with
    /// default values, marked failed for the warning tooltip, NO raw retained (the editor state
    /// is authoritative; the next save writes it and clears the mark). An unknown `type` falls
    /// back to `rawPlaceholder` with the raw retained — there are no defaults to show for a type
    /// that doesn't exist, so the row round-trips untouched instead.
    public static <H extends DataHolder<?>> Codec<H> guardedComponent(
        Codec<H> inner,
        Function<Identifier, @Nullable H> defaultsByType,
        Supplier<H> rawPlaceholder
    ) {
        return new Codec<>() {
            @Override
            public <D> DataResult<Pair<H, D>> decode(DynamicOps<D> ops, D input) {
                String message;

                try {
                    DataResult<Pair<H, D>> result = inner.decode(ops, input);
                    if (result.result().isPresent()) return result;
                    message = result.error().map(DataResult.Error::message).orElse("Unknown deserialization error");
                } catch (Exception e) {
                    message = e.toString();
                }

                Identifier typeId = Identifier.tryParse(new Dynamic<>(ops, input).get("type").asString(""));
                H typedDefault = typeId == null ? null : defaultsByType.apply(typeId);

                if (typedDefault != null) {
                    AkytheraLabs.LOGGER.warn("Component {} malformed, opening with defaults: {}", typeId, message);
                    typedDefault.markFailed(new IllegalStateException(message));
                    return DataResult.success(Pair.of(typedDefault, ops.empty()));
                }

                AkytheraLabs.LOGGER.error("Guarded component decode failed, keeping raw form: {}", message);
                H placeholder = rawPlaceholder.get();
                placeholder.markFailed(new IllegalStateException(message));
                placeholder.retainRaw(new Dynamic<>(ops, input).convert(JsonOps.INSTANCE).getValue());

                return DataResult.success(Pair.of(placeholder, ops.empty()));
            }

            @Override
            public <D> DataResult<D> encode(H input, DynamicOps<D> ops, D prefix) {
                JsonElement raw = input.retainedRaw();

                if (input.getDeserializationFailureException() != null && raw != null) {
                    return DataResult.success(new Dynamic<>(JsonOps.INSTANCE, raw).convert(ops).getValue());
                }

                DataResult<D> encoded = inner.encode(input, ops, prefix);

                // A defaults-fallback component whose real state just reached disk is healthy
                // again — the failure tint drops on the next save.
                if (input.getDeserializationFailureException() != null && encoded.result().isPresent()) {
                    input.markResolved();
                }

                return encoded;
            }
        };
    }
}
