package com.yesman.akytheralabs.editor;

import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/// The content browser's copy / cut clipboard. Not the OS clipboard, which carries text.
///
/// Entries are held as paths rather than widgets: the grid rebuilds between the copy and the paste.
/// A path whose file is gone by then is dropped at paste time.
public final class ContentClipboard {
    private static final ContentClipboard INSTANCE = new ContentClipboard();

    public static ContentClipboard getInstance() {
        return INSTANCE;
    }

    private List<Path> paths = List.of();
    private boolean cut;

    /// The asset type the paths came from. A paste only lands in a category that matches.
    @Nullable
    private ResourceProcessor<?> processor;

    public void put(List<Path> paths, boolean cut, ResourceProcessor<?> processor) {
        this.paths = List.copyOf(paths);
        this.cut = cut;
        this.processor = processor;
    }

    public void clear() {
        this.paths = List.of();
        this.cut = false;
        this.processor = null;
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public List<Path> paths() {
        return paths;
    }

    public boolean isCut() {
        return cut;
    }

    public @Nullable ResourceProcessor<?> processor() {
        return processor;
    }

    /// Whether `path` waits for a cut's paste — what dims its tile.
    public boolean isCut(Path path) {
        return cut && paths.contains(path);
    }
}
