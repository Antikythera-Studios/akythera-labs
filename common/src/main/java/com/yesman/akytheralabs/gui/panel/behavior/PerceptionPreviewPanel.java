package com.yesman.akytheralabs.gui.panel.behavior;

import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.ColliderViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.collider.SphereCollider;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.behavior.BehaviorTreeBlueprintHolder;
import com.yesman.akytheralabs.akythera.behavior.PerceptionHolder;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.main.EntityTypeSelectModal;
import com.yesman.akytheralabs.gui.widget.viewer.ColliderPreviewComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// The preview of a behavior tree asset's senses: the preview entity, the sight shape at its eyes, facing where the
/// entity faces, and the hearing sphere around its feet. Both are read from the holder every frame. Picking the sight
/// shape raises the gizmo, which drags its pivot. The entity type button on the header picks the preview entity.
public final class PerceptionPreviewPanel extends AdjustablePanelManager.PanelArea {
    private static final Identifier DEFAULT_ENTITY_TYPE = Identifier.withDefaultNamespace("zombie");
    private static final int SIGHT_COLOR = 0xFF33AAFF;
    private static final int HEARING_COLOR = 0xFFFFCC33;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_PAD = 4;

    private final Font font;

    private final BehaviorTreeBlueprintHolder holder;

    private final PanelHeader header;

    private final Model3DViewer viewer;

    /// Runs when the sight shape is picked, and after each gizmo drag of it.
    private final Runnable onSightEdited;

    /// The wireframe of the sight shape, or null while the asset has no sight.
    private @Nullable ColliderPreviewComponent sight;

    private int buttonX;
    private int buttonY;

    public PerceptionPreviewPanel(final Font font, final BehaviorTreeBlueprintHolder holder, final Runnable onSightEdited) {
        this.font = font;
        this.holder = holder;
        this.onSightEdited = onSightEdited;
        this.header = new PanelHeader(font, Component.literal("Preview"));
        this.viewer = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        ColliderViewerComponent hearing = new ColliderViewerComponent(
            () -> {
                PerceptionHolder.HearingHolder sense = holder.getPerception().getHearing();

                return sense == null || sense.getRadius() <= 0.0F
                    ? null
                    : new SphereCollider(Transform.IDENTITY, TransformInheritPolicy.LOC_ROT_SCA, sense.getRadius(), true);
            },
            () -> Transform.IDENTITY
        );

        hearing.setColor(HEARING_COLOR);

        viewer.addComponent(new CameraComponent(true));
        viewer.addComponent(new BackgroundComponent(16, 16, 16, 255));
        viewer.addComponent(new GridFloorComponent(2.0F, 50, 68, 68, 68, 255));
        viewer.addComponent(hearing);
        EntityPreviewModelMapping.displayOrWarn(viewer, font, entityType(), true, true);
        syncSight();

        addWidget(header);
        addWidget(viewer);
    }

    /// Releases the viewer's render target.
    public void destroy() {
        viewer.destroy();
    }

    /// Rebuilds [#sight] when the asset gained, lost or replaced its sight.
    private void syncSight() {
        PerceptionHolder.SightHolder sense = holder.getPerception().getSight();
        ColliderComponentHolder shape = sense == null ? null : sense.getShape();

        if (sight != null && sight.getCollider() == shape) return;

        if (sight != null) {
            if (viewer.getFocused() == sight) viewer.focus(null);

            viewer.removeComponent(sight);
        }

        sight = shape == null ? null : ColliderPreviewComponent.editable(shape, this::eyeTransform, onSightEdited, _ -> onSightEdited.run());

        if (sight != null) {
            sight.setRestingColor(SIGHT_COLOR);
            viewer.addComponent(sight);
        }
    }

    private Identifier entityType() {
        return holder.getPreviewEntityType() == null ? DEFAULT_ENTITY_TYPE : holder.getPreviewEntityType();
    }

    /// Where the sight is placed on an entity facing straight ahead, in the model space the preview draws in: at the
    /// eye height, turned half a circle like [EntityPatch#getModelTransform] turns the model.
    private Transform eyeTransform() {
        EntityType<?> type = EntityPreviewModelMapping.typeOf(entityType());
        double eyeHeight = type == null ? 0.0D : type.getDimensions().eyeHeight();

        return Transform.of(new Vec3(0.0D, eyeHeight, 0.0D), new Quaterniond().rotationY(Math.PI), new Vec3(1.0D, 1.0D, 1.0D));
    }

    private Component buttonLabel() {
        return Component.literal(entityType().toString());
    }

    private boolean overButton(final double mouseX, final double mouseY) {
        int width = font.width(buttonLabel()) + BUTTON_PAD * 2;

        return mouseX >= buttonX && mouseX < buttonX + width && mouseY >= buttonY && mouseY < buttonY + BUTTON_H;
    }

    @Override
    protected void onPanelResized(final ScreenRectangle screenRectangle) {
        header.relocate(screenRectangle);
        viewer.relocate(screenRectangle);
        viewer.onResizeCompleted();

        buttonY = screenRectangle.top() + (PanelHeader.DEFAULT_HEADER_HEIGHT - BUTTON_H) / 2;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        syncSight();
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        Component label = buttonLabel();
        int width = font.width(label) + BUTTON_PAD * 2;

        buttonX = getRight() - width - BUTTON_PAD;
        graphics.fill(buttonX, buttonY, buttonX + width, buttonY + BUTTON_H, overButton(mouseX, mouseY) ? 0xFF3A3A3A : 0xFF2A2A2A);
        graphics.text(font, label, buttonX + BUTTON_PAD, buttonY + (BUTTON_H - font.lineHeight) / 2, 0xFFCCCCCC, false);
    }

    @Override
    public boolean mouseClicked(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
        if (!overButton(event.x(), event.y())) return super.mouseClicked(event, doubleClick);

        ModalManager.getInstance().openModalWidgetAtCenter(new EntityTypeSelectModal(font, entityType(), picked -> {
            holder.setPreviewEntityType(picked);
            EntityPreviewModelMapping.displayOrWarn(viewer, font, picked, true, true);
        }));

        return true;
    }
}
