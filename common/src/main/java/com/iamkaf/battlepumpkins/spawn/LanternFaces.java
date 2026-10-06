package com.iamkaf.battlepumpkins.spawn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iamkaf.battlepumpkins.Constants;
import com.iamkaf.battlepumpkins.pumpkin.Face;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Preset faces worn by Lantern Night pumpkins, read from {@code data/<namespace>/battle_pumpkin_faces/*.json} so
 * datapacks can add their own. Each file holds {@code "rows"}: 16 strings of {@code #} (carved) and {@code .} (skin).
 * <p>
 * Faces load on first use and again whenever {@code /reload} replaces the server's resource manager, so no loader
 * reload hook is needed. Only the server thread calls this.
 */
public final class LanternFaces {
    private static final String DIRECTORY = "battle_pumpkin_faces";

    private static @Nullable ResourceManager loadedFrom;
    private static List<Face> faces = List.of();

    private LanternFaces() {
    }

    public static List<Face> get(ResourceManager resources) {
        if (resources != loadedFrom) {
            faces = load(resources);
            loadedFrom = resources;
        }
        return faces;
    }

    private static List<Face> load(ResourceManager resources) {
        List<Face> loaded = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> entry : resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                List<String> rows = new ArrayList<>();
                JsonArray array = GsonHelper.getAsJsonArray(json, "rows");
                for (JsonElement row : array) {
                    rows.add(GsonHelper.convertToString(row, "row"));
                }
                loaded.add(Face.parse(rows));
            } catch (IOException | RuntimeException e) {
                Constants.LOG.warn("Skipping Lantern Night face {}: {}", entry.getKey(), e.getMessage());
            }
        }
        return List.copyOf(loaded);
    }
}
