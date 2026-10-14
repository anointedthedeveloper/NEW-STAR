package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drift.assets.AssetPackService;
import com.drift.assets.WallpaperSetter;
import com.drift.config.DriftSettings;
import com.drift.config.SettingsService;
import com.drift.core.DriftEventBus;
import com.drift.monitoring.MonitorService;
import com.drift.monitoring.OshiSystemMonitor;
import com.drift.monitoring.SystemMonitor;
import com.drift.monitoring.SystemSnapshot;
import com.drift.persistence.Database;
import com.drift.widgets.LayoutService;
import com.drift.widgets.WidgetConfig;
import com.drift.widgets.WidgetLayout;
import com.drift.widgets.WidgetType;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PacksWidgetsTest {
    @TempDir Path dir;
    Database db;
    SettingsService settings;
    final List<Path> wallpapers = new ArrayList<>();
    String currentWallpaper;
    AssetPackService packs;

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3};
    static final byte[] PNG2 = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 9, 9, 9};

    @BeforeEach void setUp() throws IOException {
        db = new Database(dir.resolve("p.db"));
        settings = new SettingsService(db, new DriftEventBus());
        WallpaperSetter ws = new WallpaperSetter() {
            public boolean supported() { return true; }
            public Optional<String> current() { return Optional.ofNullable(currentWallpaper); }
            public void set(Path p) { wallpapers.add(p); currentWallpaper = p.toString(); }
        };
        packs = new AssetPackService(Files.createDirectories(dir.resolve("packs")), db, settings, ws);
    }

    Path zip(String name, Map<String, byte[]> files) throws IOException {
        Path z = dir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(z))) {
            for (var e : files.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); out.closeEntry(); }
        }
        return z;
    }

    static byte[] manifest(String type, String... files) {
        String list = String.join(",", java.util.Arrays.stream(files).map(f -> "\"" + f + "\"").toList());
        return ("{\"schema\":1,\"name\":\"Sky\",\"version\":\"1.0\",\"type\":\"" + type + "\",\"files\":[" + list + "]}").getBytes();
    }

    @Test void validPackInstallsAndListsAssets() throws Exception {
        Path z = zip("ok.zip", Map.of("manifest.json", manifest("wallpapers", "a.png", "b.png"), "a.png", PNG, "b.png", PNG2));
        var ins = packs.inspect(z);
        assertThat(ins.valid()).as(ins.errors().toString()).isTrue();
        String id = packs.install(z);
        assertThat(packs.installed()).containsExactly(id);
        assertThat(packs.assets(id)).hasSize(2);
        // installing the same pack again is flagged as duplicate
        assertThat(packs.inspect(z).duplicate()).isTrue();
        assertThatThrownBy(() -> packs.install(z)).hasMessageContaining("already installed");
    }

    @Test void maliciousArchivesAreRejected() throws Exception {
        Path traversal = zip("t.zip", Map.of("manifest.json", manifest("icons", "../evil.png"), "../evil.png", PNG));
        assertThat(packs.inspect(traversal).valid()).isFalse();
        Path exe = zip("e.zip", Map.of("manifest.json", manifest("icons", "run.exe"), "run.exe", new byte[]{'M', 'Z', 1, 2}));
        assertThat(packs.inspect(exe).errors()).anyMatch(s -> s.contains("not allowed"));
        Path fake = zip("f.zip", Map.of("manifest.json", manifest("icons", "x.png"), "x.png", "not an image".getBytes()));
        assertThat(packs.inspect(fake).errors()).anyMatch(s -> s.contains("do not match"));
        Path unlisted = zip("u.zip", Map.of("manifest.json", manifest("icons", "a.png"), "a.png", PNG, "extra.png", PNG2));
        assertThat(packs.inspect(unlisted).errors()).anyMatch(s -> s.contains("not listed"));
        Path noManifest = zip("n.zip", Map.of("a.png", PNG));
        assertThat(packs.inspect(noManifest).errors()).anyMatch(s -> s.contains("manifest.json is missing"));
        Path badType = zip("b.zip", Map.of("manifest.json", "{\"schema\":1,\"name\":\"x\",\"version\":\"1\",\"type\":\"plugin\",\"files\":[\"a.png\"]}".getBytes(), "a.png", PNG));
        assertThat(packs.inspect(badType).valid()).isFalse();
        assertThatThrownBy(() -> packs.install(traversal)).hasMessageContaining("rejected");
        assertThat(Files.exists(dir.resolve("evil.png"))).isFalse();
        assertThat(packs.installed()).isEmpty();
    }

    @Test void oversizedFileIsRejectedByDecompressedSize() throws Exception {
        byte[] big = new byte[(int) AssetPackService.MAX_FILE_BYTES + 10];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        Path z = zip("big.zip", Map.of("manifest.json", manifest("icons", "big.png"), "big.png", big)); // compresses tiny
        assertThat(packs.inspect(z).errors()).anyMatch(s -> s.contains("limit"));
    }

    @Test void wallpaperApplyAndRevert() throws Exception {
        Path prev = Files.writeString(dir.resolve("old.jpg"), "x");
        currentWallpaper = prev.toString();
        Path z = zip("w.zip", Map.of("manifest.json", manifest("wallpapers", "a.png"), "a.png", PNG));
        String id = packs.install(z);
        packs.applyWallpaper(id, packs.assets(id).get(0));
        assertThat(wallpapers).hasSize(1);
        assertThat(packs.revertWallpaper()).isTrue();
        assertThat(wallpapers.get(1)).isEqualTo(prev);
        assertThat(packs.revertWallpaper()).isFalse();
        assertThatThrownBy(() -> packs.applyWallpaper(id, prev)).hasMessageContaining("not part");
    }

    @Test void iconPackApplyAndRemoveResetsSetting() throws Exception {
        Path z = zip("i.zip", Map.of("manifest.json", manifest("icons", "a.png"), "a.png", PNG));
        String id = packs.install(z);
        packs.applyIconPack(id);
        assertThat(settings.get().appearance().iconPack()).isEqualTo(id);
        packs.remove(id);
        assertThat(settings.get().appearance().iconPack()).isEmpty();
        assertThat(packs.installed()).isEmpty();
    }

    // ---- widgets ----
    @Test void widgetValidation() {
        WidgetConfig ok = WidgetConfig.create(WidgetType.DIGITAL_CLOCK, 10, 10);
        assertThat(ok.validate()).isEmpty();
        assertThat(ok.withOpacity(5).validate()).isNotEmpty();
        assertThat(ok.withSize(1, 1).validate()).hasSize(2);
        assertThat(ok.withLayout("huge").validate()).isNotEmpty();
        assertThat(ok.withRefresh(0).validate()).isNotEmpty();
        assertThat(ok.withPosition(Double.NaN, 0).validate()).isNotEmpty();
        assertThat(ok.withSetting("bad key!", "v").validate()).isNotEmpty();
        assertThat(new WidgetConfig("x", "nuclear-reactor", 0, 0, 100, 100, 1, "compact", true, 1, Map.of()).validate()).isNotEmpty();
        assertThat(WidgetType.fromSpoken("the clock")).contains(WidgetType.DIGITAL_CLOCK);
        assertThat(WidgetType.fromSpoken("music player")).contains(WidgetType.MEDIA_PLAYER);
        assertThat(WidgetType.fromSpoken("toaster")).isEmpty();
    }

    @Test void layoutsPersistRestorePreviousAndPresets() {
        LayoutService ls = new LayoutService(db);
        WidgetConfig clock = WidgetConfig.create(WidgetType.DIGITAL_CLOCK, 100, 100);
        WidgetConfig notes = WidgetConfig.create(WidgetType.NOTES, 300, 100).withSetting("text", "hello");
        ls.save(new WidgetLayout("Default", List.of(clock)));
        ls.save(new WidgetLayout("Default", List.of(clock, notes)));
        ls.save(new WidgetLayout("Focus", List.of(notes)));
        ls.setActive("Focus");

        LayoutService reopened = new LayoutService(new Database(dir.resolve("p.db")));
        assertThat(reopened.names()).containsExactly("Default", "Focus");
        assertThat(reopened.activeName()).isEqualTo("Focus");
        assertThat(reopened.active().widgets()).containsExactly(notes);
        assertThat(reopened.load("Default").orElseThrow().widgets()).hasSize(2);
        assertThat(reopened.restorePrevious("Default").orElseThrow().widgets()).containsExactly(clock);
        assertThat(reopened.load("Default").orElseThrow().widgets()).containsExactly(clock);
        assertThatThrownBy(() -> ls.save(new WidgetLayout("Bad", List.of(clock.withOpacity(9))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ls.save(new WidgetLayout("Dup", List.of(clock, clock)))).hasMessageContaining("Duplicate");
        ls.delete("Focus");
        assertThat(ls.names()).containsExactly("Default");
    }

    // ---- monitoring ----
    @Test void monitoringIsOptInAndNeverSamplesWhenDisabled() throws Exception {
        int[] samples = {0};
        SystemMonitor fake = () -> { samples[0]++; return new SystemSnapshot(Optional.of(12.0), 1, 2, Optional.empty(), Optional.of(true), List.of(), 0); };
        DriftEventBus bus = new DriftEventBus();
        MonitorService svc = new MonitorService(fake, settings, bus);
        svc.apply();
        Thread.sleep(200);
        assertThat(svc.scheduled()).isFalse();
        assertThat(svc.latest()).isEmpty();
        assertThat(samples[0]).isZero();
        settings.update(s -> new DriftSettings(s.appearance(), s.floating(), s.voice(), s.startup(), s.developer(), s.privacy(), new DriftSettings.Monitoring(true, 1), s.shortcuts(), s.weather()));
        svc.apply();
        Thread.sleep(300);
        assertThat(svc.latest()).isPresent();
        settings.update(s -> new DriftSettings(s.appearance(), s.floating(), s.voice(), s.startup(), s.developer(), s.privacy(), new DriftSettings.Monitoring(false, 1), s.shortcuts(), s.weather()));
        svc.apply();
        assertThat(svc.latest()).isEmpty();
        svc.shutdown();
    }

    @Test void oshiMonitorReturnsRealMemoryAndNoFakeBattery() {
        SystemSnapshot s = new OshiSystemMonitor().sample();
        assertThat(s.memoryTotalBytes()).isGreaterThan(0);
        assertThat(s.memoryUsedBytes()).isBetween(0L, s.memoryTotalBytes());
        s.cpuPercent().ifPresent(c -> assertThat(c).isBetween(0.0, 100.0));
        s.battery().ifPresent(b -> assertThat(b.percent()).isBetween(0.0, 100.0));
    }

    // ---- learning export / import ----
    @Test void learnedDataCanBeExportedImportedAndReset() {
        TestEnv a = new TestEnv(dir.resolve("a"));
        a.aliases.put(com.drift.learning.AliasRepository.AliasKind.APP, "coding editor", "id-code");
        a.learning.recordSuccess("open the thing", "app.open", Map.of("appId", "id-code"));
        a.learning.recordSuccess("sneaky", "dev.run", Map.of("task", "maven-clean"));
        String json = a.learning.exportJson();
        assertThat(json).contains("coding editor").contains("open the thing").doesNotContain("sneaky");
        TestEnv b = new TestEnv(dir.resolve("b"));
        assertThat(b.learning.importJson(json)).isEqualTo(2);
        assertThat(b.aliases.find(com.drift.learning.AliasRepository.AliasKind.APP, "coding editor")).isPresent();
        assertThatThrownBy(() -> b.learning.importJson("{\"version\":99}")).isInstanceOf(IllegalArgumentException.class);
        b.learning.reset();
        assertThat(b.aliases.listAll()).isEmpty();
        assertThat(b.learning.lookup("open the thing")).isEmpty();
    }
}
