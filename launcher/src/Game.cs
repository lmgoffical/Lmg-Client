using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Web.Script.Serialization;

namespace LmgLauncher
{
    /// <summary>Things about the game itself: is it running, did it crash, shaders on or off.</summary>
    static class Game
    {
        /// <summary>True while Lmg Client's Minecraft is running (Java with the .lmgclient game folder).</summary>
        public static bool Running()
        {
            try
            {
                using (var searcher = new System.Management.ManagementObjectSearcher(
                    "SELECT CommandLine FROM Win32_Process WHERE Name = 'javaw.exe' OR Name = 'java.exe'"))
                {
                    foreach (var o in searcher.Get())
                    {
                        string cmd = o["CommandLine"] as string;
                        if (cmd != null && cmd.IndexOf(".lmgclient", StringComparison.OrdinalIgnoreCase) >= 0) return true;
                    }
                }
            }
            catch { }
            return false;
        }

        /// <summary>The newest crash report, if there is one.</summary>
        public static FileInfo LatestCrash()
        {
            try
            {
                var dir = new DirectoryInfo(Path.Combine(Paths.GameDir, "crash-reports"));
                if (!dir.Exists) return null;
                return dir.GetFiles("crash-*.txt").OrderByDescending(f => f.LastWriteTimeUtc).FirstOrDefault();
            }
            catch { return null; }
        }

        // ---- shaders (Iris keeps its settings in config/iris.properties) ------------------------

        static string IrisFile => Path.Combine(Paths.GameDir, "config", "iris.properties");

        public static string ShaderPack()
        {
            var props = ReadProps(IrisFile);
            return props.TryGetValue("shaderPack", out var p) ? p : null;
        }

        public static bool ShadersOn()
        {
            var props = ReadProps(IrisFile);
            return props.TryGetValue("enableShaders", out var v) && v.Trim().Equals("true", StringComparison.OrdinalIgnoreCase);
        }

        /// <summary>Turns shaders on/off for the next time the game starts (only while it's closed).</summary>
        public static void SetShaders(bool on)
        {
            var lines = File.Exists(IrisFile) ? File.ReadAllLines(IrisFile).ToList() : new List<string>();
            int i = lines.FindIndex(l => l.TrimStart().StartsWith("enableShaders="));
            if (i >= 0) lines[i] = "enableShaders=" + (on ? "true" : "false");
            else lines.Add("enableShaders=" + (on ? "true" : "false"));
            Directory.CreateDirectory(Path.GetDirectoryName(IrisFile));
            File.WriteAllLines(IrisFile, lines);
        }

        static Dictionary<string, string> ReadProps(string path)
        {
            var d = new Dictionary<string, string>();
            try
            {
                if (!File.Exists(path)) return d;
                foreach (var line in File.ReadAllLines(path))
                {
                    if (line.StartsWith("#")) continue;
                    int eq = line.IndexOf('=');
                    if (eq > 0) d[line.Substring(0, eq).Trim()] = line.Substring(eq + 1).Trim();
                }
            }
            catch { }
            return d;
        }
    }

    /// <summary>One mod in the mods folder, read from its fabric.mod.json.</summary>
    class ModInfo
    {
        public string Id, Name, Version, Description, File;
        public Bitmap Icon;
        public bool On;
        public List<string> Depends = new List<string>();

        static readonly HashSet<string> Required = new HashSet<string> { "lmgclient", "fabric-api" };
        static readonly HashSet<string> Pack = new HashSet<string> { "lmgclient", "fabric-api", "sodium", "lithium", "iris" };

        public bool IsRequired => Required.Contains(Id);
        public bool InPack => Pack.Contains(Id);

        public static List<ModInfo> All()
        {
            var list = new List<ModInfo>();
            if (!Directory.Exists(Paths.Mods)) return list;
            foreach (var path in Directory.GetFiles(Paths.Mods))
            {
                string name = Path.GetFileName(path).ToLowerInvariant();
                if (!(name.EndsWith(".jar") || name.EndsWith(".jar.disabled"))) continue;
                var m = Read(path);
                if (m != null) list.Add(m);
            }
            return list.OrderBy(m => m.InPack ? 0 : 1).ThenBy(m => m.Name, StringComparer.OrdinalIgnoreCase).ToList();
        }

        static ModInfo Read(string path)
        {
            var info = new ModInfo { File = path, On = path.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) };
            info.Id = info.Name = Path.GetFileName(path).Replace(".disabled", "");
            info.Version = "";
            info.Description = "";
            try
            {
                using (var zip = ZipFile.OpenRead(path))
                {
                    var entry = zip.GetEntry("fabric.mod.json");
                    if (entry == null) return info;
                    string json;
                    using (var r = new StreamReader(entry.Open(), Encoding.UTF8)) json = r.ReadToEnd();
                    var d = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(json);
                    info.Id = Str(d, "id", info.Id);
                    info.Name = Str(d, "name", info.Id);
                    info.Version = Str(d, "version", "");
                    info.Description = Str(d, "description", "");
                    if (d.TryGetValue("depends", out var dep) && dep is Dictionary<string, object> deps) info.Depends.AddRange(deps.Keys);
                    string icon = null;
                    if (d.TryGetValue("icon", out var ic))
                    {
                        if (ic is string s) icon = s;
                        else if (ic is Dictionary<string, object> sizes && sizes.Count > 0) icon = sizes.Values.Last() as string;
                    }
                    var iconEntry = icon != null ? zip.GetEntry(icon) : null;
                    if (iconEntry != null)
                        using (var s = iconEntry.Open()) using (var ms = new MemoryStream()) { s.CopyTo(ms); info.Icon = new Bitmap(new MemoryStream(ms.ToArray())); }
                }
            }
            catch { }
            return info;
        }

        static string Str(Dictionary<string, object> d, string key, string fallback) =>
            d.TryGetValue(key, out var v) && v is string s ? s : fallback;

        /// <summary>Switches the mod on or off (renames to .jar.disabled, like the in-game Mods screen).</summary>
        public string Toggle(List<ModInfo> all)
        {
            if (IsRequired && On) return Name + " is needed to run Lmg Client.";
            if (On)
            {
                var needs = all.Where(m => m.On && m != this && m.Depends.Contains(Id)).Select(m => m.Name).ToList();
                if (needs.Count > 0) return Name + " is needed by " + string.Join(", ", needs) + ". Switch that off first.";
            }
            if (!On)
            {
                var missing = all.Where(m => !m.On && Depends.Contains(m.Id)).Select(m => m.Name).ToList();
                if (missing.Count > 0) return Name + " needs " + string.Join(", ", missing) + ". Switch that on first.";
            }
            string target = On ? File + ".disabled" : File.Substring(0, File.Length - ".disabled".Length);
            System.IO.File.Move(File, target);
            File = target;
            On = !On;
            return null;
        }
    }
}
