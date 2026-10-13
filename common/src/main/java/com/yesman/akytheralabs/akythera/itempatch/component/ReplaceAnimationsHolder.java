package com.yesman.akytheralabs.akythera.itempatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.animator.AnimationInterfaceKey;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.world.patch.item.component.ReplaceAnimations;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Stream;

/// Editor holder for [ReplaceAnimations]: the reset and mirror flags as scalar checkbox rows, plus a
/// list of (anim interface, animation) replacement entries. Animations are held as the shared project
/// [Holder]s [AkytheraProject#getEntryHolderOrDangling] hands out.
public final class ReplaceAnimationsHolder extends ComponentDataHolder<ReplaceAnimations>
    implements RootAttachableComponent
{
    public static final MapCodec<ReplaceAnimationsHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EntryHolder.CODEC.listOf().optionalFieldOf("animations_to_replace", List.of())
                .forGetter(h -> h.entries),
            Codec.BOOL.fieldOf("reset_animations").forGetter(h -> h.resetAnimations),
            Codec.BOOL.optionalFieldOf("mirror_animations", false).forGetter(h -> h.mirrorAnimations)
        ).apply(instance, (entries, reset, mirror) -> {
            ReplaceAnimationsHolder h = new ReplaceAnimationsHolder();
            h.entries.addAll(entries);
            h.resetAnimations = reset;
            h.mirrorAnimations = mirror;
            return h;
        })
    );

    @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.reset_animations", order = 0, inheritable = true)
    private boolean resetAnimations;

    @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.mirror_animations", order = 1, inheritable = true)
    private boolean mirrorAnimations;

    /// The live entry list, mutated in place by the Details panel's list row.
    @EditorEditable(
        label = "gui.akytheralabs.item_patch_screen.section.replacements",
        defaultElement = "defaultEntry",
        emptyCandidates = "gui.akytheralabs.item_patch_screen.empty.anim_interfaces",
        order = 2,
        inheritable = true
    )
    private final List<EntryHolder> entries = new ArrayList<>();

    public static ReplaceAnimationsHolder createDefault() {
        return new ReplaceAnimationsHolder();
    }

    /// The `+` button's fresh row, see [EditorEditable#defaultElement]: the first animation interface
    /// the project offers, replacing nothing yet. Null while the project defines none, which the `+`
    /// reports through the field's [EditorEditable#emptyCandidates].
    static @Nullable EntryHolder defaultEntry() {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        SortedSet<Identifier> interfaces = project.getMergedDataBlockIdentifiers(AnimationInterfaceKey.ENUM_MANAGER);

        if (interfaces.isEmpty()) return null;

        Holder<AnimationInterfaceKey> first =
            project.getDataBlockHolderOrDangling(AnimationInterfaceKey.ENUM_MANAGER, interfaces.first());

        return new EntryHolder(
            first.value(),
            ResourceKey.create(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, DataHolder.EMPTY_REFERENCE)
        );
    }

    public boolean getResetAnimations() {
        return resetAnimations;
    }

    public void setResetAnimations(boolean resetAnimations) {
        this.resetAnimations = resetAnimations;
    }

    public boolean getMirrorAnimations() {
        return mirrorAnimations;
    }

    public void setMirrorAnimations(boolean mirrorAnimations) {
        this.mirrorAnimations = mirrorAnimations;
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
        mirrorAnimations = object.mirrorAnimations();
        entries.clear();
        for (ReplaceAnimations.ReplaceEntry replaceEntry : object.animationsToReplace()) {
            entries.add(new EntryHolder(replaceEntry.interfaceKey(), replaceEntry.animResourceKey()));
        }
    }

    @Override
    public ReplaceAnimations makeObject() {
        List<ReplaceAnimations.ReplaceEntry> list = entries.stream()
            .map(d -> new ReplaceAnimations.ReplaceEntry(d.interfaceKey, d.animation.unwrapKey().orElseThrow()))
            .toList();

        return new ReplaceAnimations(list, resetAnimations, mirrorAnimations, isEnabled());
    }

    /// [ReplaceAnimations] is its own blueprint — it builds itself.
    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return ReplaceAnimations.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/replace_animations.ftl";
    }

    /// Tokens for `components/replace_animations.ftl`: one row per replacement, carrying the anim
    /// interface's data-block id, the animation's registry id and the [AkytheraRegistries.DataPack]
    /// constant that registry is named by, plus the reset and mirror flags. A dangling animation emits
    /// its raw empty-reference id.
    @Override
    public Map<String, Object> getDTO() {
        List<Map<String, Object>> rows = entries.stream()
            .<Map<String, Object>>map(d -> {
                Identifier interfaceId = d.interfaceKey.identifier();
                ResourceKey<? extends PlayableAnimation> animKey = d.animation.unwrapKey().orElseThrow();

                return Map.of(
                    "anim_interface", interfaceId == null ? "" : interfaceId.toString(),
                    "animation", animKey.identifier().toString(),
                    "registry", animKey.isFor(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY)
                        ? "BLEND_SPACE_2D_KEY" : "ANIM_SEQUENCE_KEY"
                );
            })
            .toList();

        return Map.of("replaces", rows, "reset_animations", resetAnimations, "mirror_animations", mirrorAnimations);
    }

    /// Key → the shared live holder for it.
    private static Holder<? extends PlayableAnimation> resolveAnimation(ResourceKey<? extends PlayableAnimation> key) {
        return AkytheraProject.getCurrentProject().getEntryHolderOrDangling(key);
    }

    /// One replacement: the target anim interface (an EnumerableDataBlock combo) and the animation to
    /// play there (project holder popup over both animation registries).
    public static final class EntryHolder {
        static final Codec<EntryHolder> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                DataBlockHolder.blockCodec(AnimationInterfaceKey.ENUM_MANAGER)
                    .fieldOf("anim_interface").forGetter(d -> d.interfaceKey),
                PlayableAnimation.KEY_CODEC
                    .fieldOf("animation").forGetter(d -> d.animation.unwrapKey().orElseThrow())
            ).apply(instance, EntryHolder::new)
        );

        @EditorEditable(label = "gui.akytheralabs.item_patch_screen.row.anim_interface", order = 0, inheritable = true)
        private AnimationInterfaceKey interfaceKey;

        @EditorEditable(
            label = "gui.akytheralabs.item_patch_screen.row.animation",
            registries = "animationRegistries",
            order = 1,
            inheritable = true
        )
        private Holder<? extends PlayableAnimation> animation;

        EntryHolder(AnimationInterfaceKey interfaceKey, ResourceKey<? extends PlayableAnimation> animation) {
            this.interfaceKey = interfaceKey;
            this.animation = resolveAnimation(animation);
        }

        /// The registries the animation row's picker lists, see [EditorEditable#registries]: both
        /// branches of [PlayableAnimation#KEY_CODEC].
        static List<ResourceKey<? extends Registry<? extends PlayableAnimation>>> animationRegistries() {
            return List.of(
                AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,
                AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY
            );
        }
    }
}
