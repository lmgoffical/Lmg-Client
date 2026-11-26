using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

namespace LmgLauncher
{
    /// <summary>Where everything lives. All in your own user folders - nothing needs admin.</summary>
    static class Paths
    {
        public static readonly string AppData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        public static readonly string LocalAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        public static readonly string Minecraft = Path.Combine(AppData, ".minecraft");
        public static readonly string GameDir = Path.Combine(AppData, ".lmgclient");
        public static readonly string Mods = Path.Combine(GameDir, "mods");
        public static readonly string AppDir = Path.Combine(LocalAppData, "Programs", "Lmg Client");
        public static readonly string AppExe = Path.Combine(AppDir, "Lmg Client.exe");
        public static readonly string Settings = Path.Combine(AppDir, "settings.json");
    }

    /// <summary>Downloads, with a proper user agent and TLS 1.2+.</summary>
    static class Net
    {
        public const string UserAgent = "LmgClientApp/1.0 (github.com/lmgoffical/Lmg-Client)";
        static readonly HttpClient Http;

        static Net()
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12 | SecurityProtocolType.Tls13;
            Http = new HttpClient { Timeout = TimeSpan.FromMinutes(5) };
            Http.DefaultRequestHeaders.UserAgent.ParseAdd(UserAgent);
        }

        public static async Task<string> Text(string url, string accept = null)
        {
            using (var req = new HttpRequestMessage(HttpMethod.Get, url))
            {
                if (accept != null) req.Headers.Accept.ParseAdd(accept);
                using (var res = await Http.SendAsync(req).ConfigureAwait(false))
                {
                    res.EnsureSuccessStatusCode();
                    var bytes = await res.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
                    return Encoding.UTF8.GetString(bytes);
                }
            }
        }

        public static Task<byte[]> Bytes(string url) => Http.GetByteArrayAsync(url);

        /// <summary>Downloads to a temporary file, checks the SHA-1, then moves it into place.</summary>
        public static async Task Save(string url, string path, string sha1)
        {
            string tmp = path + ".part";
            using (var res = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead).ConfigureAwait(false))
            {
                res.EnsureSuccessStatusCode();
                using (var input = await res.Content.ReadAsStreamAsync().ConfigureAwait(false))
                using (var output = File.Create(tmp))
                    await input.CopyToAsync(output).ConfigureAwait(false);
            }
            if (!string.IsNullOrEmpty(sha1) && !Sha1(tmp).Equals(sha1, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(tmp);
                throw new Exception("The download of " + Path.GetFileName(path) + " was damaged. Press Play to try again.");
            }
            if (File.Exists(path)) File.Delete(path);
            File.Move(tmp, path);
        }

        public static string Sha1(string path)
        {
            using (var sha = SHA1.Create())
            using (var f = File.OpenRead(path))
                return BitConverter.ToString(sha.ComputeHash(f)).Replace("-", "").ToLowerInvariant();
        }
    }

    /// <summary>
    /// Installs and updates Lmg Client - the same steps as installer/install.ps1:
    /// Fabric, the mods (checked against their SHA-1), its own game folder, and a profile in the
    /// Minecraft Launcher. The Minecraft Launcher still does the signing in and starts the game.
    /// </summary>
    class Installer
    {
        public const string Repo = "lmgoffical/Lmg-Client";
        public const string Branch = "claude/modest-cray-bg98zr";
        const string ProfileKey = "lmg-client";
        const string ProfileName = "Lmg Client";

        static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = 64 * 1024 * 1024 };

        public string Raw { get; private set; }
        public Dictionary<string, object> Manifest { get; private set; }
        public string LatestVersion => Manifest != null ? Str(Manifest, "version") : null;

        /// <summary>The Lmg Client version in the mods folder now, or null.</summary>
        public static string InstalledVersion()
        {
            if (!Directory.Exists(Paths.Mods)) return null;
            var jar = Directory.GetFiles(Paths.Mods, "lmg-client-*.jar").Select(Path.GetFileName).FirstOrDefault();
            if (jar == null) return null;
            var m = Regex.Match(jar, @"lmg-client-(.+)\.jar$");
            return m.Success ? m.Groups[1].Value : null;
        }

        /// <summary>Reads the newest manifest. GitHub caches branch files for minutes, so ask for the newest commit first.</summary>
        public async Task Fetch()
        {
            Raw = "https://raw.githubusercontent.com/" + Repo + "/" + Branch;
            try
            {
                string sha = (await Net.Text("https://api.github.com/repos/" + Repo + "/commits/" + Uri.EscapeDataString(Branch),
                    "application/vnd.github.sha")).Trim();
                if (Regex.IsMatch(sha, "^[0-9a-f]{40}$")) Raw = "https://raw.githubusercontent.com/" + Repo + "/" + sha;
            }
            catch { /* fall back to the branch (may be a few minutes behind) */ }
            Manifest = Json.Deserialize<Dictionary<string, object>>(await Net.Text(Raw + "/download/manifest.json"));
        }

        public List<KeyValuePair<string, string>> Changelog()
        {
            var list = new List<KeyValuePair<string, string>>();
            if (Manifest != null && Manifest.TryGetValue("changelog", out var c) && c is ArrayList arr)
                foreach (var e in arr.OfType<Dictionary<string, object>>())
                    list.Add(new KeyValuePair<string, string>(Str(e, "version"), Str(e, "text")));
            return list;
        }

        public bool UpToDate()
        {
            if (Manifest == null) return false;
            var lmg = (Dictionary<string, object>)Manifest["lmgClient"];
            string path = Path.Combine(Paths.Mods, Str(lmg, "file"));
            return File.Exists(path) && ProfileExists();
        }

        static bool ProfileExists()
        {
            foreach (var file in ProfileFiles())
            {
                try
                {
                    var data = Json.Deserialize<Dictionary<string, object>>(File.ReadAllText(file, Encoding.UTF8));
                    if (data.TryGetValue("profiles", out var p) && p is Dictionary<string, object> profiles && profiles.ContainsKey(ProfileKey)) return true;
                }
                catch { }
            }
            return false;
        }

        static IEnumerable<string> ProfileFiles() =>
            new[] { "launcher_profiles.json", "launcher_profiles_microsoft_store.json" }
                .Select(f => Path.Combine(Paths.Minecraft, f)).Where(File.Exists);

        /// <summary>Installs or updates everything. {@code progress} gets (message, 0..1).</summary>
        public async Task Install(Action<string, double> progress, int ramGb, bool repair)
        {
            if (Manifest == null) await Fetch();
            if (!ProfileFiles().Any())
                throw new Exception("Open the Minecraft Launcher once and sign in, close it, then press Play again.");
            string mc = Str(Manifest, "minecraft");

            // ---- Fabric ----
            progress("Setting up Fabric", 0.05);
            var loaders = Json.Deserialize<ArrayList>(await Net.Text("https://meta.fabricmc.net/v2/versions/loader/" + mc));
            if (loaders.Count == 0) throw new Exception("Fabric doesn't support Minecraft " + mc + " yet.");
            var stable = loaders.OfType<Dictionary<string, object>>()
                .FirstOrDefault(l => ((Dictionary<string, object>)l["loader"]).TryGetValue("stable", out var s) && s is bool b && b)
                ?? (Dictionary<string, object>)loaders[0];
            string loaderVersion = Str((Dictionary<string, object>)stable["loader"], "version");
            string versionId = "fabric-loader-" + loaderVersion + "-" + mc;
            string versionDir = Path.Combine(Paths.Minecraft, "versions", versionId);
            Directory.CreateDirectory(versionDir);
            string fabricJson = await Net.Text("https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loaderVersion + "/profile/json");
            File.WriteAllText(Path.Combine(versionDir, versionId + ".json"), fabricJson);
            string dummyJar = Path.Combine(versionDir, versionId + ".jar");
            if (!File.Exists(dummyJar)) File.WriteAllBytes(dummyJar, new byte[0]);

            // ---- game folder ----
            progress("Getting your game folder ready", 0.12);
            Directory.CreateDirectory(Paths.Mods);
            foreach (var f in new[] { "options.txt", "servers.dat" })
            {
                string src = Path.Combine(Paths.Minecraft, f), dst = Path.Combine(Paths.GameDir, f);
                if (File.Exists(src) && !File.Exists(dst)) File.Copy(src, dst);
            }
            // worlds and resource packs are shared with normal Minecraft (folder links, not copies)
            foreach (var f in new[] { "saves", "resourcepacks" })
            {
                string src = Path.Combine(Paths.Minecraft, f), dst = Path.Combine(Paths.GameDir, f);
                Directory.CreateDirectory(src);
                if (!Directory.Exists(dst) && !File.Exists(dst)) Junction(dst, src);
            }

            // ---- mods ----
            var mods = new List<Dictionary<string, object>>();
            var lmg = (Dictionary<string, object>)Manifest["lmgClient"];
            mods.Add(new Dictionary<string, object> {
                { "name", "Lmg Client" }, { "file", Str(lmg, "file") }, { "url", Raw + "/download/" + Str(lmg, "file") },
                { "sha1", Str(lmg, "sha1") }, { "pattern", "lmg-client-*.jar" } });
            if (Manifest.TryGetValue("mods", out var m) && m is ArrayList extra) mods.AddRange(extra.OfType<Dictionary<string, object>>());

            for (int i = 0; i < mods.Count; i++)
            {
                var mod = mods[i];
                string file = Str(mod, "file"), dest = Path.Combine(Paths.Mods, file), sha1 = Str(mod, "sha1");
                double p = 0.2 + 0.7 * i / mods.Count;
                // remove older versions of the same mod (also switched-off ones)
                foreach (var old in Directory.GetFiles(Paths.Mods, Str(mod, "pattern")))
                    if (!Path.GetFileName(old).Equals(file, StringComparison.OrdinalIgnoreCase)) TryDelete(old);
                foreach (var old in Directory.GetFiles(Paths.Mods, Str(mod, "pattern") + ".disabled"))
                    if (!Path.GetFileName(old).Equals(file + ".disabled", StringComparison.OrdinalIgnoreCase)) TryDelete(old);
                // you switched this mod off in the Mods screen: leave it off
                if (File.Exists(dest + ".disabled") && !File.Exists(dest) && Str(mod, "name") != "Lmg Client" && !repair)
                {
                    progress(Str(mod, "name") + " is switched off - leaving it", p);
                    continue;
                }
                if (!repair && File.Exists(dest) && Net.Sha1(dest) == sha1.ToLowerInvariant())
                {
                    progress(Str(mod, "name") + " is up to date", p);
                    continue;
                }
                progress("Downloading " + Str(mod, "name"), p);
                await Net.Save(Str(mod, "url"), dest, sha1);
            }

            // ---- launcher profile ----
            progress("Adding Lmg Client to the Minecraft Launcher", 0.93);
            byte[] iconBytes = await Net.Bytes(Raw + "/src/main/resources/assets/lmgclient/icon.png");
            WriteProfile(versionId, "data:image/png;base64," + Convert.ToBase64String(iconBytes), ramGb);
            progress("Ready", 1.0);
        }

        static void WriteProfile(string versionId, string icon, int ramGb)
        {
            string now = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");
            int heap = ramGb > 0 ? ramGb : AutoRam();
            string defaultArgs = "-Xmx" + heap + "G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M";
            foreach (var path in ProfileFiles())
            {
                File.Copy(path, path + ".lmg-backup", true);
                var data = Json.Deserialize<Dictionary<string, object>>(File.ReadAllText(path, Encoding.UTF8)) ?? new Dictionary<string, object>();
                if (!(data.TryGetValue("profiles", out var p) && p is Dictionary<string, object> profiles))
                {
                    profiles = new Dictionary<string, object>();
                    data["profiles"] = profiles;
                }
                profiles.TryGetValue(ProfileKey, out var oldObj);
                var old = oldObj as Dictionary<string, object>;
                string javaArgs = old != null && old.ContainsKey("javaArgs") ? Str(old, "javaArgs") : defaultArgs;
                // the memory setting in this app always wins over what's saved
                if (ramGb > 0) javaArgs = Regex.IsMatch(javaArgs, @"-Xmx\d+[GgMm]") ? Regex.Replace(javaArgs, @"-Xmx\d+[GgMm]", "-Xmx" + ramGb + "G") : "-Xmx" + ramGb + "G " + javaArgs;
                profiles[ProfileKey] = new Dictionary<string, object> {
                    { "name", ProfileName }, { "type", "custom" },
                    { "created", old != null && old.ContainsKey("created") ? old["created"] : now },
                    { "lastUsed", now }, { "icon", icon }, { "lastVersionId", versionId },
                    { "gameDir", Paths.GameDir }, { "javaArgs", javaArgs } };
                File.WriteAllText(path, Json.Serialize(data), new UTF8Encoding(false));
            }
        }

        /// <summary>Marks the Lmg profile as the last one used, so the Minecraft Launcher has it selected.</summary>
        public static void SelectProfile()
        {
            string now = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");
            foreach (var path in ProfileFiles())
            {
                try
                {
                    var data = Json.Deserialize<Dictionary<string, object>>(File.ReadAllText(path, Encoding.UTF8));
                    if (data.TryGetValue("profiles", out var p) && p is Dictionary<string, object> profiles
                        && profiles.TryGetValue(ProfileKey, out var e) && e is Dictionary<string, object> entry)
                    {
                        entry["lastUsed"] = now;
                        File.WriteAllText(path, Json.Serialize(data), new UTF8Encoding(false));
                    }
                }
                catch { }
            }
        }

        /// <summary>Opens the Minecraft Launcher (the normal one, or the Microsoft Store / Xbox app one).</summary>
        public static void OpenMinecraftLauncher()
        {
            var paths = new[] {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Minecraft Launcher", "MinecraftLauncher.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe"),
                Path.Combine(Paths.LocalAppData, "Programs", "Minecraft Launcher", "MinecraftLauncher.exe") };
            var exe = paths.FirstOrDefault(File.Exists);
            if (exe != null) Process.Start(new ProcessStartInfo(exe) { UseShellExecute = true });
            else Process.Start(new ProcessStartInfo("explorer.exe", @"shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft") { UseShellExecute = true });
        }

        public static bool MinecraftLauncherRunning() =>
            Process.GetProcessesByName("MinecraftLauncher").Length > 0 || Process.GetProcessesByName("Minecraft").Length > 0;

        static void Junction(string link, string target)
        {
            var psi = new ProcessStartInfo("cmd.exe", "/c mklink /J \"" + link + "\" \"" + target + "\"")
            { CreateNoWindow = true, UseShellExecute = false };
            using (var p = Process.Start(psi)) p.WaitForExit(10000);
        }

        static void TryDelete(string path)
        {
            try { File.Delete(path); } catch { }
        }

        /// <summary>How much memory to give Minecraft by default: 4 GB on 16 GB PCs, 3 on 8 GB, else 2.</summary>
        public static int AutoRam()
        {
            int gb = TotalRamGb();
            return gb >= 16 ? 4 : gb >= 8 ? 3 : 2;
        }

        /// <summary>The PC's memory in GB (8 if it can't be read).</summary>
        public static int TotalRamGb()
        {
            try
            {
                var status = new MemoryStatusEx();
                return GlobalMemoryStatusEx(status) ? (int)Math.Round(status.ullTotalPhys / (1024.0 * 1024 * 1024)) : 8;
            }
            catch
            {
                return 8;
            }
        }

        internal static string Str(Dictionary<string, object> d, string key) =>
            d != null && d.TryGetValue(key, out var v) && v != null ? v.ToString() : "";

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        class MemoryStatusEx
        {
            public uint dwLength = (uint)Marshal.SizeOf(typeof(MemoryStatusEx));
            public uint dwMemoryLoad;
            public ulong ullTotalPhys, ullAvailPhys, ullTotalPageFile, ullAvailPageFile, ullTotalVirtual, ullAvailVirtual, ullAvailExtendedVirtual;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        static extern bool GlobalMemoryStatusEx([In, Out] MemoryStatusEx buffer);
    }
}
