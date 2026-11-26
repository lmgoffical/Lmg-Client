using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace LmgLauncher
{
    /// <summary>Your choices in the app.</summary>
    class AppSettings
    {
        public int RamGb { get; set; } = 0;          // 0 = pick automatically
        public bool CloseOnLaunch { get; set; } = true;

        public static AppSettings Load()
        {
            try
            {
                if (File.Exists(Paths.Settings))
                    return new JavaScriptSerializer().Deserialize<AppSettings>(File.ReadAllText(Paths.Settings)) ?? new AppSettings();
            }
            catch { }
            return new AppSettings();
        }

        public void Save()
        {
            try
            {
                Directory.CreateDirectory(Paths.AppDir);
                File.WriteAllText(Paths.Settings, new JavaScriptSerializer().Serialize(this));
            }
            catch { }
        }
    }

    /// <summary>Installing the app itself, keeping it up to date, and removing it.</summary>
    static class AppSetup
    {
        public static Version Current => Assembly.GetExecutingAssembly().GetName().Version;

        public static string CurrentText => Current.Major + "." + Current.Minor + "." + Current.Build;

        /// <summary>
        /// First run from Downloads (or a zip): copy the app into your user folder and add
        /// Desktop and Start Menu shortcuts, then start that copy. True if this copy should close.
        /// </summary>
        public static bool EnsureInstalled()
        {
            string me = Application.ExecutablePath;
            if (string.Equals(Path.GetFullPath(me), Path.GetFullPath(Paths.AppExe), StringComparison.OrdinalIgnoreCase)) return false;
            try
            {
                Directory.CreateDirectory(Paths.AppDir);
                File.Copy(me, Paths.AppExe, true);
                Shortcut(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "Lmg Client.lnk"));
                Shortcut(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "Lmg Client.lnk"));
                Process.Start(new ProcessStartInfo(Paths.AppExe) { UseShellExecute = true, WorkingDirectory = Paths.AppDir });
                return true;
            }
            catch
            {
                return false;   // couldn't install (e.g. blocked): just run from here
            }
        }

        static void Shortcut(string lnk)
        {
            Type t = Type.GetTypeFromProgID("WScript.Shell");
            object shell = Activator.CreateInstance(t);
            object sc = t.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { lnk });
            Type st = sc.GetType();
            st.InvokeMember("TargetPath", BindingFlags.SetProperty, null, sc, new object[] { Paths.AppExe });
            st.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, sc, new object[] { Paths.AppDir });
            st.InvokeMember("Description", BindingFlags.SetProperty, null, sc, new object[] { "Lmg Client" });
            st.InvokeMember("IconLocation", BindingFlags.SetProperty, null, sc, new object[] { Paths.AppExe + ",0" });
            st.InvokeMember("Save", BindingFlags.InvokeMethod, null, sc, null);
        }

        /// <summary>Removes the leftover from the last self-update.</summary>
        public static void CleanUp()
        {
            try
            {
                string old = Application.ExecutablePath + ".old";
                if (File.Exists(old)) File.Delete(old);
            }
            catch { }
        }

        /// <summary>
        /// If a newer app is out: download it, check its SHA-1, swap it in and restart.
        /// True if the app is restarting (the caller should close).
        /// </summary>
        public static async Task<bool> UpdateSelf(Installer inst, Action<string> status)
        {
            if (inst.Manifest == null || !inst.Manifest.TryGetValue("launcher", out var l) || !(l is Dictionary<string, object> info)) return false;
            if (!Version.TryParse(Installer.Str(info, "version"), out var latest)) return false;
            var mine = Current;
            int cmp = new Version(latest.Major, latest.Minor, Math.Max(0, latest.Build)).CompareTo(new Version(mine.Major, mine.Minor, Math.Max(0, mine.Build)));
            if (cmp <= 0) return false;

            status("Updating the app to " + latest + "...");
            string exe = Application.ExecutablePath, fresh = exe + ".new", old = exe + ".old";
            await Net.Save(inst.Raw + "/download/" + Installer.Str(info, "file"), fresh, Installer.Str(info, "sha1"));
            if (File.Exists(old)) File.Delete(old);
            File.Move(exe, old);     // Windows lets a running program be renamed, just not deleted
            File.Move(fresh, exe);
            Process.Start(new ProcessStartInfo(exe, "--updated") { UseShellExecute = true, WorkingDirectory = Path.GetDirectoryName(exe) });
            return true;
        }

        /// <summary>Removes the app and its shortcuts. Your game folder, worlds and settings stay.</summary>
        public static void Uninstall()
        {
            foreach (var lnk in new[] {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "Lmg Client.lnk"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "Lmg Client.lnk") })
            {
                try { if (File.Exists(lnk)) File.Delete(lnk); } catch { }
            }
            // the folder can only go once this program has closed
            Process.Start(new ProcessStartInfo("cmd.exe", "/c timeout /t 2 /nobreak >nul & rmdir /s /q \"" + Paths.AppDir + "\"")
            { CreateNoWindow = true, UseShellExecute = false });
        }
    }
}
