package dev.cloudframe.cloudframe.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dev.cloudframe.cloudframe.util.Debug;
import dev.cloudframe.cloudframe.util.DebugFlags;
import dev.cloudframe.cloudframe.util.DebugManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Resource pack handler (Option B):
 * - Extracts `resourcepack/` from the plugin JAR
 * - Zips it to the plugin data folder
 * - Serves it over a local HTTP server
 * - Pushes it to joining players via Player.setResourcePack(url, sha1)
 *
 * Notes:
 * - Ensure the `resourcepack/` directory is present under
 *   `cloudframe/src/main/resources/resourcepack/` before building the plugin.
 * - The HTTP server binds to 0.0.0.0:8080 by default; adjust if necessary.
 */
public class ResourcePackHandler implements Listener {

    private static final Debug debug = DebugManager.get(ResourcePackHandler.class);
    private static final int PORT = 8080;
    private static final String RESOURCE_ROOT = "resourcepack/";
    private static final String ZIP_NAME = "cloudframe-resourcepack.zip";

    // Delay sending the pack so we "win" if other plugins set a pack on join.
    // (Minecraft only allows one active server pack at a time; last call wins.)
    private static final long SEND_DELAY_TICKS = 40L;

    private static final AtomicLong REQUEST_SEQ = new AtomicLong(0);
    private final Map<UUID, PackRequest> lastRequestByPlayer = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private HttpServer server;
    private File zipFile;
    private String sha1;

    private record PackRequest(long id, String url, String sha1, long sentAtMs) {}

    public ResourcePackHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File data = plugin.getDataFolder();
            if (!data.exists()) data.mkdirs();

            File tmpDir = new File(data, "resourcepack-extract");
            if (tmpDir.exists()) deleteRecursively(tmpDir.toPath());
            tmpDir.mkdirs();

            // Extract resourcepack/ from the running JAR into tmpDir
            extractResourcepackFromJar(tmpDir);

            // Zip extracted directory
            zipFile = new File(data, ZIP_NAME);
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
                Path base = tmpDir.toPath();
                Files.walk(base).filter(p -> !Files.isDirectory(p)).forEach(p -> {
                    String relative = base.relativize(p).toString().replace('\\', '/');
                    if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                        debug.log("zip", "Adding to zip: " + relative);
                    }
                    try {
                        byte[] bytes = Files.readAllBytes(p);

                        // Add primary entry
                        ZipEntry ze = new ZipEntry(relative);
                        zos.putNextEntry(ze);
                        zos.write(bytes);
                        zos.closeEntry();

                        // If textures live under "textures/items/", also add an alternate
                        // path under "textures/item/" so clients expecting the singular
                        // folder name will find the image.
                        if (relative.startsWith("assets/minecraft/textures/items/") && !relative.contains(".mcmeta")) {
                            String alt = relative.replaceFirst("textures/items/", "textures/item/");
                            if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                                debug.log("zip", "Also adding alternate texture path: " + alt);
                            }
                            ZipEntry ze2 = new ZipEntry(alt);
                            zos.putNextEntry(ze2);
                            zos.write(bytes);
                            zos.closeEntry();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            // Calculate SHA-1
            sha1 = calculateSHA1(zipFile);

            // Start HTTP server
            startServer();

            // Log zip contents
            if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
                    var entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        var e = entries.nextElement();
                        debug.log("zipContents", "Zip contains: " + e.getName());
                    }
                } catch (IOException ex) {
                    debug.log("zipContents", "Failed to list zip contents: " + ex.getMessage());
                }
            }

            // Sanity-check critical pack files. If these are missing, textures will never apply.
            sanityCheckZip(zipFile);

            // Register listener to push pack on join
            Bukkit.getPluginManager().registerEvents(this, plugin);

            // Also push the pack to currently-online players (useful for /reload or PlugMan reloads).
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p == null || !p.isOnline()) continue;
                    sendPack(p, "enable-reload");
                }
            }, SEND_DELAY_TICKS);

            // cleanup extracted files (keep zip only)
            deleteRecursively(tmpDir.toPath());

            debug.log("init", "Resource pack prepared: " + zipFile.getAbsolutePath() + " sha1=" + sha1);
        } catch (Exception ex) {
            debug.log("init", "Failed to initialize resource pack: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void shutdown() {
        if (server != null) {
            server.stop(0);
            debug.log("shutdown", "Resource pack HTTP server stopped");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();

        // Do not send immediately; some servers/proxies/plugins send packs on join.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            sendPack(p, "join-delay");
        }, SEND_DELAY_TICKS);
    }

    private void sendPack(Player p, String reason) {
        try {
            String host = determineHostForUrl();
            String hostForUrl = host;
            // If IPv6 literal, enclose in brackets for URL
            if (hostForUrl.contains(":")) hostForUrl = "[" + hostForUrl + "]";

            // Cache-busting query param so clients don't reuse a stale URL cache.
            String url = "http://" + hostForUrl + ":" + PORT + "/" + ZIP_NAME;
            if (sha1 != null) {
                url = url + "?sha1=" + sha1;
            }

            long reqId = REQUEST_SEQ.incrementAndGet();
            lastRequestByPlayer.put(p.getUniqueId(), new PackRequest(reqId, url, sha1, System.currentTimeMillis()));

                if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                debug.log("sendPack", "id=" + reqId + " reason=" + reason + " player=" + p.getName() + " url=" + url +
                    " sha1=" + (sha1 == null ? "<null>" : sha1) +
                    " zip=" + (zipFile == null ? "<null>" : (zipFile.getAbsolutePath() + " size=" + zipFile.length())));
                }

            if (sha1 != null) {
                p.setResourcePack(url, sha1);
            } else {
                p.setResourcePack(url);
            }
        } catch (Exception ex) {
            debug.log("sendPack", "Failed: player=" + p.getName() + " msg=" + ex.getMessage());
        }
    }

    // NOTE: Previously we generated assets/minecraft/blockstates/note_block.json for custom
    // NOTE_BLOCK-based blocks. Controllers/tubes are now entity-only, so we intentionally
    // do not override any vanilla blockstates.

    /**
     * Determine a host address likely reachable by clients.
     * Preference order:
     * 1. Server socket address if not wildcard
     * 2. InetAddress.getLocalHost()
     * 3. First non-loopback IPv4 from network interfaces
     * 4. Fallback to 127.0.0.1
     */
    private String determineHostForUrl() {
        try {
            if (server != null && server.getAddress() != null) {
                try {
                    java.net.InetSocketAddress sa = server.getAddress();
                    if (sa != null) {
                        java.net.InetAddress ia = sa.getAddress();
                        if (ia != null && !ia.isAnyLocalAddress() && !ia.isLoopbackAddress()) {
                            return ia.getHostAddress();
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Try local host
            try {
                String local = java.net.InetAddress.getLocalHost().getHostAddress();
                if (local != null && !local.startsWith("127.")) return local;
            } catch (Exception ignored) {}

            // Enumerate network interfaces for a non-loopback IPv4
            try {
                var ifaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces());
                for (var nif : ifaces) {
                    if (nif.isLoopback() || !nif.isUp()) continue;
                    var addrs = java.util.Collections.list(nif.getInetAddresses());
                    for (var ia : addrs) {
                        if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) {
                            return ia.getHostAddress();
                        }
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            debug.log("determineHost", "Error determining host: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent ev) {
        try {
            Player p = ev.getPlayer();
            PackRequest req = lastRequestByPlayer.get(p.getUniqueId());
            if (req == null) {
                if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                    debug.log("rpStatus", "player=" + p.getName() + " status=" + ev.getStatus() + " (no tracked request)");
                }
                return;
            }
            long ageMs = Math.max(0, System.currentTimeMillis() - req.sentAtMs());
            if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                debug.log("rpStatus", "player=" + p.getName() + " status=" + ev.getStatus() +
                        " reqId=" + req.id() +
                        " ageMs=" + ageMs +
                        " sha1=" + (req.sha1() == null ? "<null>" : req.sha1()) +
                        " url=" + req.url());
            }
        } catch (Exception ex) {
            // keep listener safe
        }
    }

    // ----------------------
    // Helpers
    // ----------------------

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/" + ZIP_NAME, new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                    debug.log("serve", "HTTP request: " + exchange.getRequestURI());
                }

                try {
                    String ua = exchange.getRequestHeaders().getFirst("User-Agent");
                    String range = exchange.getRequestHeaders().getFirst("Range");
                    String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
                    if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                        debug.log("serve", "Headers: ua=" + (ua == null ? "<null>" : ua) +
                                " range=" + (range == null ? "<null>" : range) +
                                " if-none-match=" + (ifNoneMatch == null ? "<null>" : ifNoneMatch));
                    }
                } catch (Exception ignored) {}

                if (zipFile == null || !zipFile.exists()) {
                    debug.log("serve", "ZIP missing: " + (zipFile == null ? "null" : zipFile.getAbsolutePath()));
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                // Try to prevent aggressive caching on the client side.
                exchange.getResponseHeaders().add("Cache-Control", "no-store, no-cache, must-revalidate");
                exchange.getResponseHeaders().add("Pragma", "no-cache");
                exchange.getResponseHeaders().add("Expires", "0");
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + ZIP_NAME + "\"");
                if (sha1 != null) {
                    exchange.getResponseHeaders().add("X-CloudFrame-SHA1", sha1);
                }
                exchange.sendResponseHeaders(200, zipFile.length());
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipFile))) {
                    bis.transferTo(exchange.getResponseBody());
                }
                if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                    debug.log("serve", "Served ZIP to " + exchange.getRemoteAddress());
                }
                exchange.close();
            }
        });
        server.start();
        debug.log("startServer", "Started HTTP server on port " + PORT);
    }

    private void sanityCheckZip(File zip) {
        if (!DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) return;
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip)) {
            sanityCheckEntry(zf, "pack.mcmeta", true);

            // These are the key override files for our custom items.
            sanityCheckEntry(zf, "assets/minecraft/models/item/iron_hoe.json", false);
            sanityCheckEntry(zf, "assets/minecraft/models/item/blaze_rod.json", false);
            sanityCheckEntry(zf, "assets/minecraft/models/item/copper_block.json", false);

            // At least one texture we expect to exist.
            sanityCheckEntry(zf, "assets/minecraft/textures/items/cloud_wrench.png", false);
        } catch (Exception ex) {
            debug.log("zipCheck", "Failed zip sanity check: " + ex.getMessage());
        }
    }

    private void sanityCheckEntry(java.util.zip.ZipFile zf, String path, boolean logContent) {
        try {
            var entry = zf.getEntry(path);
            if (entry == null) {
                debug.log("zipCheck", "MISSING: " + path);
                return;
            }

            long size = entry.getSize();
            debug.log("zipCheck", "OK: " + path + " size=" + size);

            if (logContent) {
                try (InputStream is = zf.getInputStream(entry)) {
                    byte[] bytes = is.readAllBytes();
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    // Keep this short in logs.
                    String singleLine = text.replace("\r", "").replace("\n", " ").trim();
                    if (singleLine.length() > 200) singleLine = singleLine.substring(0, 200) + "...";
                    debug.log("zipCheck", path + " content=" + singleLine);
                }
            }
        } catch (Exception ex) {
            debug.log("zipCheck", "Error checking " + path + ": " + ex.getMessage());
        }
    }

    private void extractResourcepackFromJar(File targetDir) throws IOException {
        try {
            java.net.URI codeUri = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            File codeFile = Path.of(codeUri).toFile();

            if (codeFile.isFile()) {
                // Running from a packaged JAR
                try (JarFile jf = new JarFile(codeFile)) {
                    Enumeration<JarEntry> entries = jf.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry je = entries.nextElement();
                        String name = je.getName();
                        if (!name.startsWith(RESOURCE_ROOT)) continue;

                        String rel = name.substring(RESOURCE_ROOT.length());
                        File out = new File(targetDir, rel);
                        if (je.isDirectory()) { out.mkdirs(); continue; }

                        File parent = out.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();

                        try (InputStream is = jf.getInputStream(je); FileOutputStream fos = new FileOutputStream(out)) {
                            is.transferTo(fos);
                            if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                                debug.log("extract", "Extracted resource: " + rel);
                            }
                        }
                    }
                }
            } else {
                // Running from classes directory (IDE/dev) or codeLocation is a folder
                var cl = plugin.getClass().getClassLoader();
                Enumeration<java.net.URL> resources = cl.getResources(RESOURCE_ROOT);
                while (resources.hasMoreElements()) {
                    java.net.URL url = resources.nextElement();
                    String protocol = url.getProtocol();
                    if ("file".equals(protocol)) {
                        File dir = Path.of(url.toURI()).toFile();
                        Files.walk(dir.toPath()).forEach(p -> {
                            try {
                                Path rel = dir.toPath().relativize(p);
                                File out = new File(targetDir, rel.toString().replace('\\', '/'));
                                if (Files.isDirectory(p)) { out.mkdirs(); }
                                else {
                                    if (out.getParentFile() != null) out.getParentFile().mkdirs();
                                    try (InputStream is = Files.newInputStream(p); FileOutputStream fos = new FileOutputStream(out)) {
                                        is.transferTo(fos);
                                            if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                                                debug.log("extract", "Extracted resource: " + rel.toString().replace('\\', '/'));
                                            }
                                    }
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        });
                    } else if ("jar".equals(protocol)) {
                        java.net.JarURLConnection conn = (java.net.JarURLConnection) url.openConnection();
                        try (JarFile jf = conn.getJarFile()) {
                            Enumeration<JarEntry> entries = jf.entries();
                            while (entries.hasMoreElements()) {
                                JarEntry je = entries.nextElement();
                                String name = je.getName();
                                if (!name.startsWith(RESOURCE_ROOT)) continue;

                                String rel = name.substring(RESOURCE_ROOT.length());
                                File out = new File(targetDir, rel);
                                if (je.isDirectory()) { out.mkdirs(); continue; }
                                if (out.getParentFile() != null) out.getParentFile().mkdirs();
                                try (InputStream is = jf.getInputStream(je); FileOutputStream fos = new FileOutputStream(out)) {
                                    is.transferTo(fos);
                                    if (DebugFlags.RESOURCE_PACK_VERBOSE_LOGGING) {
                                        debug.log("extract", "Extracted resource: " + rel);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract resourcepack from code source", e);
        }
    }

    private String calculateSHA1(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream is = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) md.update(buf, 0, r);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        Files.walk(p).sorted((a, b) -> b.compareTo(a)).forEach(pp -> {
            try { Files.deleteIfExists(pp); } catch (IOException ignored) {}
        });
    }
}
