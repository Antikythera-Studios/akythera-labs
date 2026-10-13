package com.yesman.akytheralabs.akythera.widget;

import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.ColorPickerWidget;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.gui.widget.PopupBox;
import com.yesman.akytheralabs.gui.widget.main.TextureSelectModal;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Consumer;

/// The Details row of a [WidgetImage]: a [ColorPickerWidget] swatch for the color, then the texture, picked through
/// a [TextureSelectModal] in the mode [EditorEditable#sprite] chooses and cleared back to a plain color fill.
///
/// The selector writes the color through while it is open, and closing it is one undo step, as every texture pick
/// and clear is.
public final class WidgetImageProducer implements EditorWidgetFactory.WidgetProducer {
    @Override
    public List<? extends AbstractWidget> produce(final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot) {
        AnchoredWidget.Unit start = EditorWidgetFactory.columnStart(slot, 0, 1);
        AnchoredWidget.Unit size = EditorWidgetFactory.columnSize(slot, 0, 1);
        TextureSelectModal.Mode mode = slot.annotation().sprite() ? TextureSelectModal.Mode.SPRITE : TextureSelectModal.Mode.TEXTURE;

        ColorPickerWidget swatch = new ColorPickerWidget(
            factory.font(),
            start, AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> ((WidgetImage) slot.get()).color(),
            color -> slot.set(new WidgetImage(((WidgetImage) slot.get()).texture(), color))
        );

        PopupBox<Identifier> texture = new PopupBox<>(
            factory.font(),
            (rect, xy) -> start.pixels(rect, xy) + EditorWidgetFactory.ROW_H + EditorWidgetFactory.COLUMN_GAP,
            (rect, xy) -> size.pixels(rect, xy) - EditorWidgetFactory.ROW_H - EditorWidgetFactory.COLUMN_GAP,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> ((WidgetImage) slot.get()).texture(),
            _ -> {},
            id -> id == null ? "" : id.toString(),
            Component.empty(),
            box -> ModalManager.getInstance().openModalWidgetAtCenter(new TextureSelectModal(factory.font(), mode, id -> {
                ModalManager.getInstance().closeModal();
                change(slot, new WidgetImage(id, ((WidgetImage) slot.get()).color()), swatch, box);
            }))
        );

        texture.setOnCleared(box -> change(slot, WidgetImage.of(((WidgetImage) slot.get()).color()), swatch, box));
        swatch.setValue(((WidgetImage) slot.get()).color());
        swatch.setOnCommitted((from, to) -> {
            Identifier current = ((WidgetImage) slot.get()).texture();

            HistoryManager.getInstance().pushHistory(
                EditorWidgetFactory.historyScope(slot),
                new DataChange<>(new WidgetImage(current, from), new WidgetImage(current, to), writer(slot, swatch, texture))
            );
        });

        return List.of(swatch, texture);
    }

    /// Writes `after` through `slot` as one undo step.
    private static void change(
        final EditorWidgetFactory.ValueSlot slot, final WidgetImage after, final ColorPickerWidget swatch, final PopupBox<Identifier> texture
    ) {
        Consumer<WidgetImage> writer = writer(slot, swatch, texture);

        HistoryManager.getInstance().pushHistory(EditorWidgetFactory.historyScope(slot), new DataChange<>((WidgetImage) slot.get(), after, writer));
        writer.accept(after);
    }

    /// Writes an image through `slot` and shows it on both widgets.
    private static Consumer<WidgetImage> writer(final EditorWidgetFactory.ValueSlot slot, final ColorPickerWidget swatch, final PopupBox<Identifier> texture) {
        return image -> {
            slot.set(image);
            swatch.setValue(image.color());
            texture.valueSetter().accept(image.texture());
        };
    }
}
