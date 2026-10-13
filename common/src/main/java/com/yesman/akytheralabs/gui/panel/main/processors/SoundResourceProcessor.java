package com.yesman.akytheralabs.gui.panel.main.processors;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.ProjectSounds;
import com.yesman.akytheralabs.dataholder.sound.SoundEventHolder;
import com.yesman.akytheralabs.editor.historytrack.FileCreation;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/// The project's sound events under [AkytheraProject#SOUNDS_DIRECTORY], one `sounds.json` entry per
/// `.akmeta` file. The Ogg Vorbis files they play sit beside them, out of the grid: a dropped batch of
/// them becomes sound events, see [#askEventLayout].
public final class SoundResourceProcessor implements ResourceProcessor<Identifier> {
    public static final SoundResourceProcessor INSTANCE = new SoundResourceProcessor();

    private static final String NEW_EVENT_NAME = "new_sound_event";

    private SoundResourceProcessor() {}

    @Override
    public boolean canCreateSubDirectories() {
        return true;
    }

    /// Writes an empty sound event into the selected directory.
    @Override
    public void onPressAddButton(ContentBrowserPanel browser, ExplorerPanel.TreeNode selected) {
        ExplorerPanel.TreeNode regRoot = ResourceProcessor.regRootOf(selected);
        Path targetDir = ResourceProcessor.resolveTargetDir(regRoot, selected);
        if (targetDir == null) return;

        AkytheraProject project = AkytheraProject.getCurrentProject();
        String eventName = FileSystemHelper.findNextAvailableName(targetDir, NEW_EVENT_NAME, AkytheraProject.SOUND_EVENT_EXTENSION);
        Path eventFile = targetDir.resolve(eventName + AkytheraProject.SOUND_EVENT_EXTENSION);
        SoundEventHolder event = new SoundEventHolder();

        if (event.save(eventFile) != null) return;

        HistoryManager.getInstance().pushHistory(new FileCreation(
            () -> {
                event.save(eventFile);
                refreshAndSelect(eventFile);
            },
            () -> {
                project.deletePathRecursive(eventFile);
                refreshAndSelect(eventFile);
            }
        ));

        refreshAndSelect(eventFile);
    }

    @Override
    public boolean canImport(Path file, List<Path> batch) {
        return file.getFileName().toString().endsWith(AkytheraProject.SOUND_EXTENSION);
    }

    /// Imports the file, then drops whatever the game decoded of the file it overwrote.
    @Override
    public AkytheraProject.AssetLoadResult<?> importFile(Path source, Path destination) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        AkytheraProject.AssetLoadResult<Path> result = project.loadSoundToCurrentProject(source, destination);
        Identifier location = project.getSoundId(destination);

        if (!result.failed() && location != null) {
            ProjectSounds.evictCachedBuffer(Sound.SOUND_LISTER.idToFile(location));
        }

        return result;
    }

    @Override
    public @Nullable ContentWidget<?> createContentWidget(Font font, Path path) {
        if (!path.getFileName().toString().endsWith(AkytheraProject.SOUND_EVENT_EXTENSION)) return null;

        return new ContentWidget.SoundEventContent(font, path, ResourceProcessor.fileNameNoExt(path));
    }

    /// Asks how the just imported `soundFiles` become sound events: one event playing all of them as
    /// variations, or one event per file. A single file becomes its own event without asking.
    public static void askEventLayout(Font font, List<Path> soundFiles) {
        if (soundFiles.size() == 1) {
            createEvents(List.of(soundFiles));
            return;
        }

        int popupW = 320;
        int btnW = WidgetContainerModal.evenButtonWidth(popupW, 2);

        WidgetContainerModal popup = WidgetContainerModal.builder(
                font, Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_SOUND_EVENTS_TITLE), popupW)
            .textLines(Component.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_SOUND_EVENTS_BODY, soundFiles.size()))
            // Footer buttons render right-to-left in insertion order
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_SOUND_EVENTS_PER_FILE), () -> {
                        ModalManager.getInstance().closeModal();
                        createEvents(soundFiles.stream().map(List::of).toList());
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .footerButton(
                AkytheraButton.buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_MAIN_SCREEN_CONTENT_BROWSER_POPUP_SOUND_EVENTS_VARIATIONS), () -> {
                        ModalManager.getInstance().closeModal();
                        createEvents(List.of(soundFiles));
                    })
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                    .build(),
                btnW
            )
            .build();

        ModalManager.getInstance().openModalWidgetAtCenter(popup);
    }

    /// One sound event per group of sound files, playing the group as its variations. Each is named
    /// after the group's first file and placed beside it.
    private static void createEvents(List<List<Path>> groups) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        Path lastCreated = null;

        for (List<Path> group : groups) {
            Path first = group.getFirst();
            String eventName = FileSystemHelper.findNextAvailableName(
                first.getParent(), FileSystemHelper.stem(first.getFileName().toString()), AkytheraProject.SOUND_EVENT_EXTENSION);
            Path eventFile = first.resolveSibling(eventName + AkytheraProject.SOUND_EVENT_EXTENSION);
            SoundEventHolder event = new SoundEventHolder();

            for (Path soundFile : group) {
                Identifier location = project.getSoundId(soundFile);
                if (location != null) event.getSounds().add(new SoundEventHolder.SoundHolder(location.toString()));
            }

            if (event.save(eventFile) == null) lastCreated = eventFile;
        }

        if (lastCreated != null) refreshAndSelect(lastCreated);
    }

    private static void refreshAndSelect(Path file) {
        AkytheraEditorLayoutManager.getInstance().<MainEditorScreen>ifLastScreen(mainEditorScreen -> {
            mainEditorScreen.explorerPanel.rebuildTree();
            mainEditorScreen.explorerPanel.focusNodeByPath(file.getParent());
            mainEditorScreen.contentBrowserPanel.pickSelections(true, file);
        });
    }
}
