package com.yesman.akytheralabs.akythera.particle;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.gui.widget.PopupBox;
import com.yesman.akytheralabs.gui.widget.main.TextureSelectModal;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

/// The Details row of a [ParticleSprite]: its id, picked through a [TextureSelectModal] in
/// [TextureSelectModal.Mode#PARTICLE]. Every pick is one undo step.
public final class ParticleSpriteProducer implements EditorWidgetFactory.WidgetProducer {
    @Override
    public List<? extends AbstractWidget> produce(final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot) {
        PopupBox<ParticleSprite> box = new PopupBox<>(
            factory.font(),
            EditorWidgetFactory.columnStart(slot, 0, 1), EditorWidgetFactory.columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> (ParticleSprite) slot.get(),
            _ -> {},
            sprite -> sprite == null ? "" : sprite.id().toString(),
            Component.empty(),
            popup -> ModalManager.getInstance().openModalWidgetAtCenter(new TextureSelectModal(factory.font(), TextureSelectModal.Mode.PARTICLE, id -> {
                ModalManager.getInstance().closeModal();

                ParticleSprite before = (ParticleSprite) slot.get();
                ParticleSprite after = new ParticleSprite(id);

                HistoryManager.getInstance().pushHistory(EditorWidgetFactory.historyScope(slot), new DataChange<>(before, after, sprite -> {
                    slot.set(sprite);
                    popup.valueSetter().accept(sprite);
                }));
                slot.set(after);
                popup.valueSetter().accept(after);
            }))
        );

        return List.of(box);
    }
}
