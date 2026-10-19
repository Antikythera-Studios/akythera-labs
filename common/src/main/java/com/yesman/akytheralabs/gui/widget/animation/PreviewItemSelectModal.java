package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akytheralabs.akythera.animation.PreviewItem;
import com.yesman.akytheralabs.akythera.blueprint.ComponentPaths;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.EquipmentSlotBinderHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformSocketHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.main.ItemSelectModal;
import com.yesman.akytheralabs.gui.widget.viewer.PreviewPatchComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// `View > Select Preview Item` of an animation editor: an [ItemSelectModal] with a socket row, picking the
/// [PreviewItem] held under a transform socket of the preview entity type's patch. Picking an item for a socket replaces
/// its item; picking [Items#AIR] takes it away.
public final class PreviewItemSelectModal {
    private static final int SOCKET_BOX_W = 130;
    private static final int SOCKET_BOX_H = 16;

    private PreviewItemSelectModal() {}

    /// Opens the modal for `viewer`'s preview, or a popup saying why it can't: no preview model, no patch, or a patch
    /// with no transform socket. `onChanged` receives the new preview items.
    public static void open(final Font font, final Model3DViewer viewer, final List<PreviewItem> current, final Consumer<List<PreviewItem>> onChanged) {
        PreviewPatchComponent preview = viewer.getComponent(PreviewPatchComponent.class);
        EntityPatchBlueprintHolder patch = preview == null ? null : preview.patch();
        Component title = Component.translatable(LangKeys.GUI_MENU_BAR_VIEW_SELECT_PREVIEW_ITEM);

        if (viewer.getComponent(EntityModelComponent.class) == null) {
            RefusalPopup.open(title, Component.translatable(LangKeys.GUI_PREVIEW_ITEM_MODAL_NO_MODEL));
            return;
        }

        if (patch == null) {
            RefusalPopup.open(title, Component.translatable(LangKeys.GUI_PREVIEW_ITEM_MODAL_NO_PATCH));
            return;
        }

        List<String> sockets = ComponentPaths.known(patch).keySet().stream()
            .filter(path -> ComponentPaths.at(patch, path) instanceof TransformSocketHolder)
            .toList();

        if (sockets.isEmpty()) {
            RefusalPopup.open(title, Component.translatable(LangKeys.GUI_PREVIEW_ITEM_MODAL_NO_SOCKET));
            return;
        }

        // The socket an item is held under already, else the first one binding a slot, else the first one
        MutableObject<String> socket = new MutableObject<>(
            current.stream().map(PreviewItem::socket).filter(sockets::contains).findFirst()
                .orElseGet(() -> sockets.stream()
                    .filter(path -> ComponentPaths.at(patch, path) instanceof TransformSocketHolder holder
                        && holder.getComponents().values().stream().anyMatch(EquipmentSlotBinderHolder.class::isInstance))
                    .findFirst()
                    .orElse(sockets.getFirst()))
        );
        EditorComboBox<String> socketBox = new EditorComboBox<>(
            font,
            AnchoredWidget.pixel(4), AnchoredWidget.pixel(SOCKET_BOX_W),
            AnchoredWidget.pixel(ItemSelectModal.HEADER_Y + (ItemSelectModal.HEADER_H - SOCKET_BOX_H) / 2), AnchoredWidget.pixel(SOCKET_BOX_H),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> socket.getValue(),
            picked -> socket.setValue(picked),
            Math.min(sockets.size(), 8),
            Component.empty(),
            sockets,
            path -> path
        );

        socketBox.setClearable(false);
        socketBox.setValueQuiet(socket.getValue());

        ModalManager.getInstance().openModalWidgetAtCenter(new ItemSelectModal(
            font,
            _ -> true,
            (Item item) -> {
                ModalManager.getInstance().closeModal();

                List<PreviewItem> items = new ArrayList<>(current.stream().filter(held -> !held.socket().equals(socket.getValue())).toList());

                if (item != Items.AIR) items.add(new PreviewItem(socket.getValue(), item));

                onChanged.accept(items);
            },
            Component.translatable(LangKeys.GUI_PREVIEW_ITEM_MODAL_SOCKET),
            socketBox
        ));
    }
}
