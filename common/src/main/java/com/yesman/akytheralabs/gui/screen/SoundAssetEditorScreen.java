package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.BackgroundComponent;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.GridFloorComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.ProjectSounds;
import com.yesman.akytheralabs.dataholder.sound.SoundEventHolder;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ListInsert;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ConsoleModal;
import com.yesman.akytheralabs.gui.widget.main.EditorPreferencesModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.LocalizationManagerModal;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import com.yesman.akytheralabs.gui.widget.sound.SoundPlayTrack;
import com.yesman.akytheralabs.gui.widget.viewer.AttenuationSphereComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Edits a project sound event, see [SoundEventHolder]. Ogg Vorbis files dropped on it are copied
/// beside the event and added as variations. The viewer shows the
/// distance the selected sound is heard from as a sphere around the origin, and the track plays it from
/// there, attenuated by the viewer camera's distance to the origin.
public final class SoundAssetEditorScreen extends Screen implements ModalAccessibleScreen, SafeCloseScreen {
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 20;

    /// Camera distances [#frameCamera] keeps within, in blocks. The far one is as far as a scroll zooms out.
    private static final float MIN_FRAME_DISTANCE = 3.0F;
    private static final float MAX_FRAME_DISTANCE = 20.0F;

    /// The `attenuation_distance` a sound entry takes by default, in blocks.
    private static final float DEFAULT_ATTENUATION_DISTANCE = 16.0F;

    private final Path eventFile;
    private final SoundEventHolder holder;

    private final AdjustablePanelManager panelManager;
    private final TopMenuBar topMenuBar;
    private final Model3DViewer model3DViewer;
    private final SoundPlayTrack playTrack;
    private final PanelHeader detailsHeader;
    private final WidgetTable detailsTable;

    /// Last rect the Details panel was laid out in, so a refill lands in place. Null until the first layout.
    private @Nullable ScreenRectangle detailsRect;

    private @Nullable WorkHistory savePoint;

    /// Sound files the event played as saved, or dropped on this screen. On close, those no saved sound
    /// event plays are deleted.
    private final Set<Path> releasableSoundFiles = new HashSet<>();

    public SoundAssetEditorScreen(Path eventFile) {
        super(Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_TITLE));

        Identifier eventId = Objects.requireNonNull(AkytheraProject.getCurrentProject().getSoundId(eventFile));

        this.eventFile = eventFile;
        this.holder = SoundEventHolder.readFrom(eventFile);
        this.releasableSoundFiles.addAll(AkytheraProject.getCurrentProject().soundFilesOf(eventFile));

        topMenuBar = new TopMenuBar(
            minecraft.font,
            Component.literal(eventId.toString()),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(TOP_BAR_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> handleQuitRequest(),
            TopMenuBar.actions()
                .on(TopMenuOption.LOCALIZATION, () -> LocalizationManagerModal.open(minecraft.font))
                .on(TopMenuOption.SAVE, this::save)
                .on(TopMenuOption.EDITOR_PREFERENCES, () -> EditorPreferencesModalGenerator.open(font))
                .on(TopMenuOption.FRAME_CAMERA, this::frameCamera)
                .on(TopMenuOption.CONSOLE, () -> ConsoleModal.open(font))
                .on(TopMenuOption.DOCS, () -> {})
        );

        model3DViewer = new Model3DViewer(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            AkytheraEditorLayoutManager.getInstance()::getLayoutGuiScale
        );

        playTrack = new SoundPlayTrack(minecraft.font, holder, eventId, () -> {
            Vector3f origin = model3DViewer.toViewSpace(new Vector3f());
            return origin == null ? Vec3.ZERO : new Vec3(origin);
        }, this::fillDetails);

        model3DViewer.addComponent(new CameraComponent(true));
        model3DViewer.addComponent(new BackgroundComponent(16, 16, 16, 255));
        model3DViewer.addComponent(new GridFloorComponent(1.0F, 16, 68, 68, 68, 255));
        model3DViewer.addComponent(new AttenuationSphereComponent(minecraft.font, this::attenuationRadius));

        detailsHeader = new PanelHeader(minecraft.font, Component.translatable(LangKeys.GUI_WIDGET_DETAILS));

        detailsTable = new WidgetTable(
            this,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        fillDetails();

        panelManager = new AdjustablePanelManager(
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TOP_BAR_H), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM
        );

        List<Integer> layoutRatios = AkytheraProject.getCurrentProject().getLayoutRatioOrDefault(getClass(), 70, 70);

        var leftAndRight = panelManager.getRoot()
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.VERTICAL, layoutRatios.get(0));
        var viewerAndTrack = leftAndRight.get(0)
            .subdivide(1, AdjustablePanelManager.PanelDivider.Orientation.HORIZONTAL, layoutRatios.get(1));

        viewerAndTrack.get(0).resizeCallback(model3DViewer::relocate);
        viewerAndTrack.get(0).resizeFinishCallback(_ -> model3DViewer.onResizeCompleted());
        viewerAndTrack.get(1).area(playTrack);
        leftAndRight.get(1).resizeCallback(rect -> {
            detailsRect = rect;
            detailsHeader.relocate(rect);
            detailsTable.relocate(rect);
        });

        topMenuBar.setDirtyChecker(this::hasUnsavedChanges);
        savePoint = HistoryManager.getInstance().currentSavePoint();
    }

    @Override
    protected void init() {
        super.init();

        var rect = getRectangle();
        topMenuBar.relocate(rect);
        panelManager.relocate(rect);

        addRenderableWidget(topMenuBar);
        addRenderableWidget(panelManager);
        addRenderableWidget(model3DViewer);
        addRenderableWidget(detailsHeader);
        addRenderableWidget(detailsTable);

        frameCamera();
    }

    /// Looks at the origin from halfway inside the selected sound's sphere, where it plays at half its
    /// gain, or from halfway inside a default sphere when it has none.
    private void frameCamera() {
        CameraComponent camera = model3DViewer.getComponent(CameraComponent.class);
        if (camera == null) return;

        Float radius = attenuationRadius();
        float distance = Mth.clamp(ParseUtils.nvl(radius, DEFAULT_ATTENUATION_DISTANCE) * 0.5F, MIN_FRAME_DISTANCE, MAX_FRAME_DISTANCE);

        camera.moveTo(-distance, 20.0F, 135.0F, new Vector3f());
    }

    /// Radius of the selected sound's sphere in blocks, or null for none: an event row's distance is
    /// the one of the sound it picks, unknown until it plays.
    private @Nullable Float attenuationRadius() {
        SoundEventHolder.SoundHolder sound = playTrack.selectedSound();

        if (sound == null || sound.getType() != Sound.Type.FILE) return null;

        return Math.max(sound.getVolume(), 1.0F) * sound.getAttenuationDistance(); // the scale SoundEngine plays it at
    }

    /// Fills the Details table from [#holder], and the track's rows with it. A write that swaps which
    /// rows exist refills both through here.
    private void fillDetails() {
        new EditorWidgetFactory(font).fillWidgetTable(detailsTable, holder, this::fillDetails);
        playTrack.rebuildRows();

        if (detailsRect != null) detailsTable.relocate(detailsRect);
    }

    /// Writes the event file. An event the game would refuse is not written, and the reason is shown.
    private boolean save() {
        String failure = holder.save(eventFile);

        if (failure != null) {
            RefusalPopup.open(
                Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_MESSAGE_SAVE_FAILED),
                Component.literal(failure)
            );

            return false;
        }

        releasableSoundFiles.addAll(AkytheraProject.getCurrentProject().soundFilesOf(eventFile));
        savePoint = HistoryManager.getInstance().currentSavePoint();

        return true;
    }

    /// Adds each dropped Ogg Vorbis file as a variation, see [#importDropped].
    @Override
    public void onFilesDrop(@NonNull List<Path> paths) {
        importDropped(
            paths.stream().filter(path -> path.getFileName().toString().toLowerCase().endsWith(AkytheraProject.SOUND_EXTENSION)).toList(),
            0
        );
    }

    /// Copies `sources` from `index` on beside the event and adds each as a variation, one at a time. A
    /// file whose name is already taken there asks first, naming the sound events that play it:
    /// overwrite it, keep both under a free name, or skip the file.
    private void importDropped(List<Path> sources, int index) {
        if (index >= sources.size()) return;

        Path source = sources.get(index);
        Path directory = eventFile.getParent();
        String fileName = Util.sanitizeName(source.getFileName().toString(), Identifier::validPathChar);
        Path destination = directory.resolve(fileName);
        Runnable next = () -> importDropped(sources, index + 1);

        if (!Files.exists(destination)) {
            addDroppedSound(source, destination);
            next.run();
            return;
        }

        String users = AkytheraProject.getCurrentProject().soundEventsUsing(destination).stream()
            .map(Identifier::toString)
            .collect(Collectors.joining(", "));
        Component body = users.isEmpty()
            ? Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_POPUP_DUPLICATE_BODY_UNUSED, fileName)
            : Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_POPUP_DUPLICATE_BODY_USED, fileName, users);
        String freeName = FileSystemHelper.findNextAvailableName(directory, FileSystemHelper.stem(fileName), AkytheraProject.SOUND_EXTENSION);
        Path freeDestination = directory.resolve(freeName + AkytheraProject.SOUND_EXTENSION);

        int popupW = 340;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 3);

        // Footer buttons render right-to-left in insertion order
        WidgetContainerModal popup = WidgetContainerModal.builder(font, Component.translatable(LangKeys.GUI_SOUND_ASSET_SCREEN_POPUP_DUPLICATE_TITLE), popupW)
            .textLines(body)
            .footerButton(closingButton(LangKeys.GUI_CANCEL, next), btnW)
            .footerButton(closingButton(LangKeys.GUI_SOUND_ASSET_SCREEN_POPUP_DUPLICATE_KEEP_BOTH, () -> {
                addDroppedSound(source, freeDestination);
                next.run();
            }), btnW)
            .footerButton(closingButton(LangKeys.GUI_MESSAGE_BUTTON_OVERWRITE, () -> {
                addDroppedSound(source, destination);
                next.run();
            }), btnW)
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// Copies `source` onto `destination` and adds it to the event as a variation.
    private void addDroppedSound(Path source, Path destination) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        AkytheraProject.AssetLoadResult<Path> result = project.loadSoundToCurrentProject(source, destination);
        Identifier location = project.getSoundId(destination);

        if (result.failed() || location == null) {
            RefusalPopup.open(
                Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_LOAD_ERROR_TITLE),
                Component.literal(String.valueOf(result.error()))
            );
            return;
        }

        ProjectSounds.evictCachedBuffer(Sound.SOUND_LISTER.idToFile(location));
        releasableSoundFiles.add(destination);

        List<SoundEventHolder.SoundHolder> sounds = holder.getSounds();
        SoundEventHolder.SoundHolder sound = new SoundEventHolder.SoundHolder(location.toString());

        sounds.add(sound);
        HistoryManager.getInstance().pushHistory(new ListInsert<>(sounds, sounds.size() - 1, sound, this::fillDetails));
        fillDetails();
    }

    /// A modal footer button that closes its modal, then runs `onPress`.
    private AkytheraButton closingButton(String langKey, Runnable onPress) {
        return AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(langKey), () -> {
                ModalManager.getInstance().closeModal();
                onPress.run();
            })
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
            .build();
    }

    private boolean hasUnsavedChanges() {
        return HistoryManager.getInstance().currentSavePoint() != savePoint;
    }

    @Override
    public void handleQuitRequest() {
        SafeCloseScreen.openQuiteScreen(
            font,
            Component.translatable(LangKeys.GUI_MESSAGE_ASK_TO_SAVE, topMenuBar.getMessage()),
            hasUnsavedChanges(),
            () -> {
                ModalManager.getInstance().closeModal();
                if (save()) onClose();
            },
            this::onClose
        );
    }

    @Override
    public void onClose() {
        playTrack.stop();
        AkytheraProject.getCurrentProject().deleteUnusedSoundFiles(releasableSoundFiles);
        model3DViewer.destroy();
        AkytheraProject.getCurrentProject().saveLayoutOfScreen(this, panelManager.serializePanelDividerPositions());
        AkytheraEditorLayoutManager.getInstance().removeLastScreen();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Draw nothing
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        AkytheraEditorLayoutManager.blurFocusChainLeaf(this, event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && shouldCloseOnEsc()) {
            handleQuitRequest();
            return true;
        }

        if (EditorKeyMappings.SAVE.matches(event)) {
            save();
            return true;
        }

        if (EditorKeyMappings.REDO.matches(event)) {
            HistoryManager.getInstance().redo();
            return true;
        }

        if (EditorKeyMappings.UNDO.matches(event)) {
            HistoryManager.getInstance().undo();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public @NonNull Optional<GuiEventListener> getChildAt(double x, double y) {
        AbstractWidget modal = getHoveringModal(x, y);
        if (modal != null) return Optional.of(modal);

        return super.getChildAt(x, y);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener g) {
        if (isModal(g)) {
            if (g != null) g.setFocused(true);
        } else {
            super.setFocused(g);
        }
    }

    /// The modal owning focus, through [ModalAccessibleScreen#getFocusOwningModal], so focus is
    /// released when it closes.
    @Nullable
    public GuiEventListener getFocused() {
        AbstractWidget modal = getFocusOwningModal();
        if (modal != null) return modal;

        return super.getFocused();
    }
}
