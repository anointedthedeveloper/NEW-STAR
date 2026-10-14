package com.drift.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.drift.config.DriftSettings;
import com.drift.config.SettingsService;
import com.drift.core.DriftPaths;
import com.drift.core.Json;
import com.drift.persistence.Database;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Service;

/**
 * Validated import of icon packs and wallpaper collections (zip with manifest.json). Packs are pure data:
 * only an allow-list of image types is accepted, every entry path is confined to the pack folder, sizes are
 * measured on the decompressed stream, and nothing in a pack is ever executed.
 */
@Service
public class AssetPackService {
    public static final long MAX_ARCHIVE_BYTES = 50L * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    public static final int MAX_ENTRIES = 500;
    static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "bmp", "ico");
    private static final String PREVIOUS_WALLPAPER_KEY = "wallpaper.previous";

    public record Manifest(String name, String version, String type, List<String> files) {}

    public record Inspection(boolean valid, List<String> errors, List<String> warnings, Manifest manifest, String packId, String contentHash, boolean duplicate) {}

    private final Path packsDir;
    private final Database db;
    private final SettingsService settings;
    private final WallpaperSetter wallpaper;

    public AssetPackService(DriftPaths paths, Database db, SettingsService settings, WallpaperSetter wallpaper) {
        this(paths.packs(), db, settings, wallpaper);
    }

    public AssetPackService(Path packsDir, Database db, SettingsService settings, WallpaperSetter wallpaper) {
        this.packsDir = packsDir;
        this.db = db;
        this.settings = settings;
        this.wallpaper = wallpaper;
    }

    /** Validates an archive without extracting anything. */
    public Inspection inspect(Path archive) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            if (!Files.isRegularFile(archive)) return fail("Archive not found");
            if (Files.size(archive) > MAX_ARCHIVE_BYTES) return fail("Archive is larger than " + MAX_ARCHIVE_BYTES / 1024 / 1024 + " MB");
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                if (zip.size() > MAX_ENTRIES) return fail("Archive has more than " + MAX_ENTRIES + " entries");
                ZipEntry manifestEntry = zip.getEntry("manifest.json");
                if (manifestEntry == null) return fail("manifest.json is missing");
                byte[] mbytes = readBounded(zip, manifestEntry, 64 * 1024, null);
                Manifest manifest = parseManifest(mbytes, errors);
                if (manifest == null) return new Inspection(false, errors, warnings, null, null, null, false);

                Set<String> listed = new HashSet<>(manifest.files());
                Set<String> seenHashes = new HashSet<>();
                Map<String, String> hashes = new HashMap<>();
                long total = 0;
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String name = e.getName();
                    if (e.isDirectory()) continue;
                    if (name.equals("manifest.json")) continue;
                    String bad = unsafeName(name);
                    if (bad != null) { errors.add("Entry rejected (" + bad + "): " + name); continue; }
                    String ext = ext(name);
                    if (!IMAGE_EXT.contains(ext)) { errors.add("File type not allowed: " + name); continue; }
                    if (!listed.contains(name)) { errors.add("File is not listed in the manifest: " + name); continue; }
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] data;
                    try { data = readBounded(zip, e, MAX_FILE_BYTES, md); }
                    catch (IOException tooBig) { errors.add(name + ": " + tooBig.getMessage()); continue; }
                    total += data.length;
                    if (total > MAX_TOTAL_BYTES) { errors.add("Pack is larger than " + MAX_TOTAL_BYTES / 1024 / 1024 + " MB when extracted"); break; }
                    if (!magicMatches(ext, data)) { errors.add("Contents do not match the file extension: " + name); continue; }
                    String h = HexFormat.of().formatHex(md.digest());
                    hashes.put(name, h);
                    if (!seenHashes.add(h)) warnings.add("Duplicate image content inside the pack: " + name);
                }
                for (String f : manifest.files()) if (zip.getEntry(f) == null) errors.add("Listed file is missing: " + f);
                String contentHash = combinedHash(hashes);
                String id = packId(manifest);
                boolean duplicate = Files.isDirectory(packsDir.resolve(id)) || isContentInstalled(contentHash);
                if (duplicate) warnings.add("A pack with the same name/version or identical content is already installed");
                return new Inspection(errors.isEmpty(), errors, warnings, manifest, id, contentHash, duplicate);
            }
        } catch (IOException | java.security.NoSuchAlgorithmException | RuntimeException ex) {
            return fail("Cannot read archive: " + ex.getMessage());
        }
    }

    private static Inspection fail(String msg) { return new Inspection(false, List.of(msg), List.of(), null, null, null, false); }

    private static Manifest parseManifest(byte[] bytes, List<String> errors) {
        try {
            JsonNode n = Json.MAPPER.readTree(bytes);
            if (n.path("schema").asInt(0) != 1) errors.add("Unsupported manifest schema (expected 1)");
            String name = n.path("name").asText("");
            String version = n.path("version").asText("");
            String type = n.path("type").asText("");
            if (!name.matches("[\\p{L}\\p{N} _.-]{1,40}")) errors.add("Pack name must be 1-40 letters, digits, spaces or _.-");
            if (!version.matches("[0-9A-Za-z._-]{1,20}")) errors.add("Pack version is invalid");
            if (!type.equals("icons") && !type.equals("wallpapers")) errors.add("Pack type must be 'icons' or 'wallpapers'");
            List<String> files = new ArrayList<>();
            JsonNode arr = n.path("files");
            if (!arr.isArray() || arr.isEmpty()) errors.add("Manifest must list at least one file");
            else for (JsonNode f : arr) files.add(f.asText(""));
            return errors.isEmpty() ? new Manifest(name, version, type, files) : null;
        } catch (IOException e) {
            errors.add("manifest.json is not valid JSON");
            return null;
        }
    }

    /** Returns a reason when a zip entry name is unsafe, else null. */
    static String unsafeName(String name) {
        if (name.isEmpty() || name.length() > 200) return "bad length";
        if (name.startsWith("/") || name.startsWith("\\") || name.contains("\\") || name.matches("^[A-Za-z]:.*")) return "absolute or backslash path";
        if (name.indexOf('\0') >= 0) return "NUL byte";
        for (String seg : name.split("/")) if (seg.equals("..") || seg.equals(".") || seg.isEmpty()) return "path traversal";
        return null;
    }

    private static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static boolean magicMatches(String ext, byte[] d) {
        if (d.length < 4) return false;
        return switch (ext) {
            case "png" -> (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "jpg", "jpeg" -> (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xD8;
            case "bmp" -> d[0] == 'B' && d[1] == 'M';
            case "ico" -> d[0] == 0 && d[1] == 0 && d[2] == 1 && d[3] == 0;
            default -> false;
        };
    }

    private static byte[] readBounded(ZipFile zip, ZipEntry e, long max, MessageDigest md) throws IOException {
        try (InputStream in = zip.getInputStream(e)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) throw new IOException("file exceeds the " + max / 1024 / 1024 + " MB limit when decompressed");
                out.write(buf, 0, n);
                if (md != null) md.update(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String combinedHash(Map<String, String> hashes) throws java.security.NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        hashes.values().stream().sorted().forEach(h -> md.update(h.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return HexFormat.of().formatHex(md.digest());
    }

    static String packId(Manifest m) {
        return (m.name() + "-" + m.version()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private boolean isContentInstalled(String contentHash) {
        return installed().stream().anyMatch(p -> contentHash.equals(readMeta(p).path("contentHash").asText("")));
    }

    private JsonNode readMeta(String id) {
        try { return Json.MAPPER.readTree(Files.readString(packsDir.resolve(id).resolve("pack.json"))); }
        catch (IOException e) { return Json.MAPPER.createObjectNode(); }
    }

    /** Installs a validated, non-duplicate pack. Extraction uses the validated names only. */
    public String install(Path archive) throws IOException {
        Inspection ins = inspect(archive);
        if (!ins.valid()) throw new IOException("Pack rejected: " + String.join("; ", ins.errors()));
        if (ins.duplicate()) throw new IOException("Pack rejected: " + String.join("; ", ins.warnings()));
        Path target = packsDir.resolve(ins.packId()).normalize();
        if (!target.startsWith(packsDir.normalize())) throw new IOException("Pack rejected: unsafe pack id");
        Path tmp = Files.createTempDirectory(packsDir.toFile().exists() ? packsDir : Files.createDirectories(packsDir), "import-");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (String f : ins.manifest().files()) {
                Path dest = tmp.resolve(f).normalize();
                if (!dest.startsWith(tmp)) throw new IOException("Pack rejected: path escape");
                Files.createDirectories(dest.getParent());
                try (InputStream in = zip.getInputStream(zip.getEntry(f)); OutputStream out = Files.newOutputStream(dest)) { in.transferTo(out); }
            }
            Files.writeString(tmp.resolve("pack.json"), Json.write(Map.of("name", ins.manifest().name(), "version", ins.manifest().version(),
                    "type", ins.manifest().type(), "files", ins.manifest().files(), "contentHash", ins.contentHash())));
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            deleteTree(tmp);
            throw e instanceof IOException io ? io : new IOException(e);
        }
        return ins.packId();
    }

    public List<String> installed() {
        if (!Files.isDirectory(packsDir)) return List.of();
        try (Stream<Path> s = Files.list(packsDir)) {
            return s.filter(p -> Files.isRegularFile(p.resolve("pack.json"))).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) { return List.of(); }
    }

    public List<Path> assets(String packId) {
        Path dir = packsDir.resolve(packId).normalize();
        if (!dir.startsWith(packsDir.normalize()) || !Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).filter(p -> IMAGE_EXT.contains(ext(p.getFileName().toString()))).sorted().toList();
        } catch (IOException e) { return List.of(); }
    }

    public void remove(String packId) throws IOException {
        Path dir = packsDir.resolve(packId).normalize();
        if (!dir.startsWith(packsDir.normalize()) || dir.equals(packsDir.normalize())) throw new IOException("Invalid pack id");
        deleteTree(dir);
        if (packId.equals(settings.get().appearance().iconPack())) applyIconPack("");
    }

    public void applyIconPack(String packId) {
        settings.update(s -> new DriftSettings(new DriftSettings.Appearance(s.appearance().themeName(), s.appearance().reducedMotion(), packId),
                s.floating(), s.voice(), s.startup(), s.developer(), s.privacy(), s.monitoring(), s.shortcuts(), s.weather()));
    }

    /** Applies a wallpaper from an installed wallpapers pack, remembering the previous wallpaper for revert. */
    public void applyWallpaper(String packId, Path image) throws IOException {
        if (!wallpaper.supported()) throw new IOException("Changing the wallpaper is only supported on Windows");
        Path dir = packsDir.resolve(packId).normalize();
        Path real = image.toRealPath();
        if (!real.startsWith(dir.toRealPath())) throw new IOException("Image is not part of that pack");
        if (db.queryOne("SELECT 1 FROM kv WHERE key=?", rs -> 1, PREVIOUS_WALLPAPER_KEY).isEmpty()) {
            wallpaper.current().ifPresent(cur -> db.update("INSERT INTO kv(key,value) VALUES(?,?)", PREVIOUS_WALLPAPER_KEY, cur));
        }
        wallpaper.set(real);
    }

    public boolean revertWallpaper() throws IOException {
        Optional<String> prev = db.queryOne("SELECT value FROM kv WHERE key=?", rs -> rs.getString(1), PREVIOUS_WALLPAPER_KEY);
        if (prev.isEmpty() || !Files.isRegularFile(Path.of(prev.get()))) return false;
        wallpaper.set(Path.of(prev.get()));
        db.update("DELETE FROM kv WHERE key=?", PREVIOUS_WALLPAPER_KEY);
        return true;
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) { for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x); }
    }
}
