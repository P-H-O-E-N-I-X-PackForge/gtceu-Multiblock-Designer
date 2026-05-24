package com.bgame.multiblockdesigner.export;

import com.bgame.multiblockdesigner.definition.MultiblockDefinition;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@link MultiblockDefinition} exports to the server's game directory.
 *
 * <p>Output location: {@code <world>/multiblock_designer/exports/}
 *
 * <p>Files generated per export:
 * <ul>
 *   <li>{@code <name>_<id>.js} — KubeJS startup script for GTCEu</li>
 * </ul>
 */
public class DefinitionExporter {

    public static class ExportResult {
        public final boolean success;
        public final String jsPath;
        public final String error;

        private ExportResult(boolean success, String jsPath, String error) {
            this.success = success;
            this.jsPath  = jsPath;
            this.error   = error;
        }

        public ExportResult(String s) {
            this(false, null, s);
        }

        public static ExportResult ok(String jsPath) {
            return new ExportResult(true, jsPath, null);
        }

        public static ExportResult fail(String error) {
            return new ExportResult(false, null, error);
        }
    }

    public static ExportResult export(MinecraftServer server, MultiblockDefinition def, boolean exportJava) {
        try {
            Path exportDir = server.getServerDirectory().toPath()
                    .resolve("multiblock_designer")
                    .resolve("exports");
            Files.createDirectories(exportDir);

            String safeName = def.displayName.toLowerCase()
                    .replaceAll("[^a-z0-9_]", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_|_$", "");

            String extension = exportJava ? ".java" : ".js";
            Path outFile = exportDir.resolve(safeName + "_" + def.id + extension);
            String content = exportJava ? JavaExporter.export(def) : KubeJSExporter.export(def);

            Files.writeString(outFile, content);

            return ExportResult.ok(outFile.toAbsolutePath().toString());

        } catch (IOException e) {
            return new ExportResult("Export error: " + e.getMessage());
        } catch (Exception e) {
            return ExportResult.fail("Export error: " + e.getMessage());
        }
    }
}