package com.yesman.akytheralabs.gui.widget.script.node;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/// A value typed into the data input `pin` of a row, in the JSON form of `type`'s codec. A wire into the
/// same pin outranks it.
///
/// A [ScriptType#keyOf] type is written as its entry type, flagged `registry_key`.
public record PinLiteral(int pin, ScriptType type, JsonElement value) {
    public static final Codec<PinLiteral> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.INT.fieldOf("pin").forGetter(PinLiteral::pin),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("type")
                    .forGetter(literal -> literal.isKey() ? literal.type().element() : literal.type()),
                Codec.BOOL.optionalFieldOf("registry_key", false).forGetter(PinLiteral::isKey),
                ExtraCodecs.JSON.fieldOf("value").forGetter(PinLiteral::value)
            ).apply(instance, (pin, type, key, value) ->
                new PinLiteral(pin, key ? ParseUtils.nvl(ScriptType.keyOf(type), type) : type, value))
    );

    /// Whether this is the [ResourceKey] of a registry entry.
    public boolean isKey() {
        return type.type() == ResourceKey.class && type.element() != null;
    }
}
