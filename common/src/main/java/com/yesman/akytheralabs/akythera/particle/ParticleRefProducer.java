package com.yesman.akytheralabs.akythera.particle;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.particle.AkytheraSpriteSet;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.PopupBox;
import com.yesman.akytheralabs.gui.widget.main.CategorizedSelectModal;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The Details row of a [ParticleRef]: a search box over the open project's particle pieces, the loaded data packs'
/// pieces, and the vanilla particle types that have sprites, each under its own heading. Picking one for an emitter
/// also removes the emitter's module rows that do not apply to it, as the same undo step.
public final class ParticleRefProducer implements EditorWidgetFactory.WidgetProducer {
    /// The label color of a choice the open project holds. ARGB.
    public static final int PROJECT_COLOR = 0xFF8FC7FF;

    @Override
    public List<? extends AbstractWidget> produce(final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot) {
        if (!(slot.owner() instanceof ParticleEmitterHolder emitter)) return List.of();

        Runnable rebuild = slot instanceof EditorWidgetFactory.RebuildingSlot rebuilding ? rebuilding.rebuild() : () -> {};
        Consumer<ParticleRef> pick = picked -> {
            ParticleEmitterHolder.ParticleChoice before = emitter.getParticleChoice();

            emitter.setParticle(picked);
            HistoryManager.getInstance().pushHistory(new DataChange<>(before, emitter.getParticleChoice(), choice -> {
                emitter.setParticleChoice(choice);
                rebuild.run();
            }));
            rebuild.run();
        };
        PopupBox<Object> box = new PopupBox<>(
            factory.font(),
            EditorWidgetFactory.columnStart(slot, 0, 1), EditorWidgetFactory.columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            value -> value instanceof ParticleRef ref ? ref.displayLabel() : Objects.toString(value),
            Component.empty(),
            opened -> {
                Set<ResourceKey<ParticleBlueprint>> projectPieces = AkytheraProject.getCurrentProject()
                    .getModifiableEntries(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY).stream()
                    .map(entry -> (ResourceKey<ParticleBlueprint>)entry.getKey())
                    .collect(Collectors.toSet());

                List<CategorizedSelectModal.Choice<ParticleRef>> project = projectPieces.stream()
                    .map(key -> new CategorizedSelectModal.Choice<ParticleRef>(key.identifier().toString(), new ParticleRef.Piece(key), PROJECT_COLOR, null))
                    .toList();
                List<CategorizedSelectModal.Choice<ParticleRef>> dataPack = StaticRegistryAccess.getRegistryAccess()
                    .lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY).registryKeySet().stream()
                    .filter(key -> !projectPieces.contains(key))
                    .map(key -> new CategorizedSelectModal.Choice<ParticleRef>(key.identifier().toString(), new ParticleRef.Piece(key)))
                    .toList();
                List<CategorizedSelectModal.Choice<ParticleRef>> vanilla = AkytheraSpriteSet.vanillaIds().stream()
                    .map(id -> new CategorizedSelectModal.Choice<ParticleRef>(id.toString(), new ParticleRef.Vanilla(id)))
                    .toList();

                ModalManager.getInstance().openModalWidgetUnder(opened, new CategorizedSelectModal<>(
                    factory.font(),
                    opened.getWidth(),
                    slot.labelComponent(),
                    List.of(
                        new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_CATEGORY_PROJECT), project),
                        new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_CATEGORY_DATAPACK), dataPack),
                        new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_CATEGORY_MINECRAFT), vanilla)
                    ),
                    pick
                ));
            }
        );

        box.setOnCleared(_ -> pick.accept(ParticleRef.NONE));

        return List.of(box);
    }
}
