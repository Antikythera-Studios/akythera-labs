package com.yesman.akytheralabs.akythera.itempatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.item.component.ReplaceAnimations;
import com.yesman.akythera.core.animation.playable.AnimationSequence;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Editor holder for [ReplaceAnimations]: the reset flag as a scalar checkbox row, plus a list of
/// (anim interface, animation) replacement entries. Animations are held as the shared project
/// [Holder]s [AkytheraProject#getEntryHolderOrDangling] hands out.
public final class ReplaceAnimationsHolder extends ComponentDataHolder<ReplaceAnimations>
    implements RootAttachableComponent
{
    public static final MapCodec<ReplaceAnimationsHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EntryHolder.CODEC.listOf().optionalFieldOf("animations_to_replace", List.of())
                .forGetter(h -> h.entries),
            Codec.BOOL.fieldOf("reset_animations").forGetter(h -> h.resetAnimations)
        ).apply(instance, (entries, reset) -> {
            ReplaceAnimationsHolder h = new ReplaceAnimationsHolder();
            h.entries.addAll(entries);
            h.resetAnimations = reset;
            return h;
        })
    );

    @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.reset_animations", order = 0)
    private boolean resetAnimations;

    /// The live entry list, mutated in place by the Details panel's list row.
    @EditorEditable(
        label = "gui.akytheralabs.item_patch_screen.section.replacements",
        defaultElement = "defaultEntry",
        order = 1
    )
    private final List<EntryHolder> entries = new ArrayList<>();

    public static ReplaceAnimationsHolder createDefault() {
        return new ReplaceAnimationsHolder();
    }

    /// The `+` button's fresh row, see [EditorEditable#defaultElement].
    ///
    /// ⚠ Requires at least one anim interface value to exist (data-driven; the editor always
    /// runs with a world loaded, so the universal set is bound).
    static EntryHolder defaultEntry() {
        AnimationInterfaceKey first = AnimationInterfaceKey.ENUM_MANAGER.universalValues().iterator().next();

        return new EntryHolder(
            first,
            resolveAnimation(ResourceKey.create(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, DataHolder.EMPTY_REFERENCE))
        );
    }

    public boolean getResetAnimations() {
        return resetAnimations;
    }

    public void setResetAnimations(boolean resetAnimations) {
        this.resetAnimations = resetAnimations;
    }

    /// The shared animation holders each entry references.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return entries.stream()
            .map(d -> d.animation)
            .filter(h -> h instanceof EditorReference)
            .map(h -> (EditorReference) h);
    }

    @Override
    public void readFrom(ReplaceAnimations object) {
        resetAnimations = object.resetAnimations();
        entries.clear();
        for (ReplaceAnimations.Replace replace : object.animationsToReplace()) {
            entries.add(new EntryHolder(replace.interfaceKey(), resolveAnimation(replace.animResourceKey())));
        }
    }

    @Override
    public ReplaceAnimations makeObject() {
        List<ReplaceAnimations.Replace> list = entries.stream()
            .map(d -> new ReplaceAnimations.Replace(d.interfaceKey, d.animation.unwrapKey().orElseThrow()))
            .toList();

        return new ReplaceAnimations(list, resetAnimations);
    }

    /// [ReplaceAnimations] is its own blueprint — it builds itself.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return ReplaceAnimations.class;
    }

    /// ***************************************************************
    /// Source export (File > Export > As Source Code)
    /// ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/replace_animations.ftl";
    }

    /// Tokens for `components/replace_animations.ftl`: one row per replacement, carrying the anim
    /// interface's data-block id and the sequence's registry id, plus the reset flag. A dangling
    /// animation emits its raw empty-reference id.
    @Override
    public Map<String, Object> getDTO() {
        List<Map<String, Object>> rows = entries.stream()
            .<Map<String, Object>>map(d -> {
                Identifier interfaceId = AnimationInterfaceKey.ENUM_MANAGER.derefIdentifier(d.interfaceKey);

                return Map.of(
                    "anim_interface", interfaceId == null ? "" : interfaceId.toString(),
                    "animation", d.animation.unwrapKey().map(k -> k.identifier().toString()).orElse("")
                );
            })
            .toList();

        return Map.of("replaces", rows, "reset_animations", resetAnimations);
    }

    /// Key → the shared live holder for it.
    private static Holder<AnimationSequence> resolveAnimation(ResourceKey<AnimationSequence> key) {
        return AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key);
    }

    /// One replacement: the target anim interface (an EnumerableDataBlock combo) and the sequence
    /// to play there (project holder popup).
    public static final class EntryHolder {
        static final Codec<EntryHolder> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                AnimationInterfaceKey.ENUM_MANAGER.getNameCodec()
                    .fieldOf("anim_interface").forGetter(d -> d.interfaceKey),
                ResourceKey.codec(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY)
                    .fieldOf("animation").forGetter(d -> d.animation.unwrapKey().orElseThrow())
            ).apply(instance, (iface, animKey) -> new EntryHolder(iface, resolveAnimation(animKey)))
        );

        @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.anim_interface", order = 0)
        private AnimationInterfaceKey interfaceKey;

        @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.animation", order = 1)
        private Holder<AnimationSequence> animation;

        EntryHolder(AnimationInterfaceKey interfaceKey, Holder<AnimationSequence> animation) {
            this.interfaceKey = interfaceKey;
            this.animation = animation;
        }
    }
}
