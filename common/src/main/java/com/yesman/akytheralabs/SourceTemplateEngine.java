package com.yesman.akytheralabs;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/// Shared FreeMarker entry point for source-code generation. Owns the one
/// [Configuration] (template loader rooted at [#TEMPLATE_ROOT]) used by both the
/// top-level [FreemarkerExporter] and by [com.yesman.akytheralabs.dataholder.DTOProvider]
/// implementations that render per-type fragment templates (`notify/log.ftl`,
/// `notify/play_sound.ftl`, …) into Java source strings.
@NullMarked
public final class SourceTemplateEngine {
    private SourceTemplateEngine() {}

    /// Classpath location (relative to the classloader root) of the `.ftl` templates.
    private static final String TEMPLATE_ROOT = "/assets/akytheralabs/source_templates";

    private static final Configuration CONFIGURATION;

    static {
        CONFIGURATION = new Configuration(Configuration.VERSION_2_3_34);
        CONFIGURATION.setClassForTemplateLoading(SourceTemplateEngine.class, TEMPLATE_ROOT);
        CONFIGURATION.setDefaultEncoding(StandardCharsets.UTF_8.name());
        CONFIGURATION.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        CONFIGURATION.setLogTemplateExceptions(false);
        CONFIGURATION.setWrapUncheckedExceptions(true);
        CONFIGURATION.setFallbackOnNullLoopVariable(false);
    }

    /// Load a template by its name relative to [#TEMPLATE_ROOT] (e.g. `animation_sequence_template.ftl`).
    public static Template getTemplate(String templateName) throws IOException {
        return CONFIGURATION.getTemplate(templateName);
    }

    /// Render `templateName` against `model` and return the produced source as a string. Used by holders
    /// to turn a single fragment (one notify / notify-state type) into its Java constructor expression.
    public static String render(String templateName, Map<String, Object> model) {
        try (StringWriter writer = new StringWriter()) {
            getTemplate(templateName).process(model, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("Failed to render source template " + templateName, e);
        }
    }

    /// Format a `float` as a round-trip-exact Java `float` literal, e.g. `0.15F`. DTO providers use this
    /// so numeric values land in generated source as compilable literals rather than bare decimals.
    public static String floatLiteral(float value) {
        return Float.toString(value) + "F";
    }
}