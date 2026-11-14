package com.fairy.agypc

import android.content.Context
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Yahin se URLs edit kar sakte ho agar kabhi link badal jaye. */
object Cfg {
    const val PROOT_URL = "https://github.com/green-green-avk/build-proot-android/raw/master/packages/proot-android-aarch64.tar.gz"
    const val ROOTFS_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-arm64.tar.gz"
    const val AG_PAGE = "https://antigravity.google/download"
    // Unverified guess; auto-detect fail ho to app me URL box use karo
    const val AG_FALLBACK = "https://storage.googleapis.com/antigravity-public/antigravity-hub/2.5.0-5471848641724416/linux-arm/Antigravity.tar.gz"
    const val PORT = 6080
    const val VNC_PASS = "agy12345"
}

class Env(private val ctx: Context) {
    private val base: File = ctx.filesDir
    val rootfs = File(base, "rootfs")
    private val pkg = File(base, "proot-pkg")
    private val tmp = File(base, "tmp")
    private val dl = File(base, "dl")
    private val marker = File(base, "installed.ok")

    val installed: Boolean get() = marker.exists()

    fun reset() {
        marker.delete()
        rootfs.deleteRecursively(); pkg.deleteRecursively(); tmp.deleteRecursively(); dl.deleteRecursively()
    }

    private fun findFile(root: File, name: String): File? =
        root.walkTopDown().firstOrNull { it.isFile && it.name == name }

    /** proot ke andar command chalane ka ProcessBuilder. */
    fun prootCmd(vararg inner: String): ProcessBuilder {
        val proot = findFile(pkg, "proot") ?: error("proot binary nahi mili")
        val loader = findFile(pkg, "loader")
        tmp.mkdirs()
        val cmd = mutableListOf(
            proot.path, "--link2symlink", "-0", "--kill-on-exit", "-r", rootfs.path,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${tmp.path}:/tmp", "-b", "${tmp.path}:/dev/shm",
            "-w", "/root",
            "/usr/bin/env", "-i", "HOME=/root", "USER=root", "TERM=xterm-256color", "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "DEBIAN_FRONTEND=noninteractive"
        )
        cmd += inner
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment()["PROOT_TMP_DIR"] = tmp.path
        if (loader != null) pb.environment()["PROOT_LOADER"] = loader.path
        return pb
    }

    fun setup(agUrl: String, log: (String) -> Unit, prog: (Int) -> Unit) {
        base.mkdirs(); dl.mkdirs()
        if (base.usableSpace < 5L shl 30) log("WARNING: 5 GB se kam free storage hai, setup fail ho sakta hai")

        log("[1/5] proot download")
        val pf = File(dl, "proot.tar.gz")
        download(Cfg.PROOT_URL, pf, log, prog)
        pkg.deleteRecursively(); extract(pf, pkg)
        findFile(pkg, "proot")?.let { Os.chmod(it.path, 493) } ?: error("Download ke andar proot nahi mili")
        findFile(pkg, "loader")?.let { Os.chmod(it.path, 493) }
        pf.delete()

        log("[2/5] Ubuntu rootfs download")
        val rf = File(dl, "rootfs.tar.gz")
        download(Cfg.ROOTFS_URL, rf, log, prog)
        rootfs.deleteRecursively(); rootfs.mkdirs()
        log("[2/5] rootfs extract ho raha hai...")
        extract(rf, rootfs); rf.delete()

        log("[3/5] scripts likh raha hu")
        File(rootfs, "etc/resolv.conf").apply { delete(); writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n") }
        for (n in listOf("setup.sh", "start-desktop.sh")) {
            val out = File(rootfs, "root/$n"); out.parentFile?.mkdirs()
            ctx.assets.open(n).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            Os.chmod(out.path, 493)
        }

        log("[4/5] Linux packages install (XFCE, VNC, noVNC) - 10-20 min lag sakte hain")
        val code = runLogged(prootCmd("/bin/bash", "/root/setup.sh"), log)
        if (code != 0) error("setup.sh fail hua (exit $code). Upar ke logs dekho, dobara try karo.")
        marker.writeText("ok")

        try {
            installAntigravity(agUrl, log, prog)
        } catch (e: Throwable) {
            log("Antigravity install fail: ${e.message}")
            log("Desktop kaam karega. URL box me Linux ARM64 .tar.gz link daalke 'Antigravity install/update' dabao.")
        }
    }

    fun installAntigravity(override: String, log: (String) -> Unit, prog: (Int) -> Unit) {
        dl.mkdirs()
        log("[5/5] Antigravity link dhundh raha hu")
        val url = if (override.isNotBlank()) override else resolveAg(log)
        log("URL: $url")
        val f = File(dl, "antigravity.tar.gz")
        download(url, f, log, prog)
        val dest = File(rootfs, "opt/antigravity")
        dest.deleteRecursively(); dest.mkdirs()
        log("Antigravity extract ho raha hai...")
        extract(f, dest); f.delete()
        log("Antigravity install ho gaya.")
    }

    private fun resolveAg(log: (String) -> Unit): String {
        try {
            val html = URL(Cfg.AG_PAGE).openStream().bufferedReader().use { it.readText() }
            val m = Regex("""https://storage\.googleapis\.com/antigravity-public/antigravity-hub/[^"'\s\\]+/linux-arm[^"'\s\\]*/Antigravity\.tar\.gz""").find(html)
            if (m != null) return m.value
            log("Download page me ARM link nahi mila, fallback URL try kar raha hu")
        } catch (e: Exception) {
            log("Page fetch fail (${e.message}), fallback URL try kar raha hu")
        }
        return Cfg.AG_FALLBACK
    }

    private fun runLogged(pb: ProcessBuilder, log: (String) -> Unit): Int {
        val p = pb.start()
        p.inputStream.bufferedReader().forEachLine(log)
        return p.waitFor()
    }

    private fun download(url: String, out: File, log: (String) -> Unit, prog: (Int) -> Unit) {
        out.parentFile?.mkdirs()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = true
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}: $url")
        val total = c.contentLengthLong
        var done = 0L; var last = 0L
        c.inputStream.use { i ->
            out.outputStream().use { o ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    val now = System.currentTimeMillis()
                    if (now - last > 1000) {
                        last = now
                        if (total > 0) prog((done * 100 / total).toInt())
                        log("  ${done shr 20} MB" + if (total > 0) " / ${total shr 20} MB" else "")
                    }
                }
            }
        }
        prog(100)
    }

    /** tar.gz extract: symlinks, hardlinks aur permissions ke saath. */
    private fun extract(src: File, dest: File) {
        dest.mkdirs()
        val root = dest.toPath().normalize()
        TarArchiveInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(src), 1 shl 16))).use { tar ->
            while (true) {
                val e = tar.nextTarEntry ?: break
                val name = e.name.removePrefix("./").trimEnd('/')
                if (name.isEmpty()) continue
                val out = File(dest, name)
                if (!out.toPath().normalize().startsWith(root)) continue
                val mode = e.mode and 0xFFF
                when {
                    e.isDirectory -> { out.mkdirs(); Os.chmod(out.path, mode or 0x1C0) }
                    e.isSymbolicLink -> { out.parentFile?.mkdirs(); out.delete(); Os.symlink(e.linkName, out.path) }
                    e.isLink -> {
                        val t = File(dest, e.linkName.removePrefix("./"))
                        out.parentFile?.mkdirs()
                        if (t.exists()) { t.copyTo(out, true); Os.chmod(out.path, mode or 0x180) }
                    }
                    e.isFile -> {
                        out.parentFile?.mkdirs()
                        if (out.exists()) out.delete()
                        out.outputStream().use { tar.copyTo(it) }
                        Os.chmod(out.path, mode or 0x180)
                    }
                }
            }
        }
    }
}
