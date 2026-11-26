using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Text;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;

namespace LmgLauncher
{
    /// <summary>
    /// The website's fonts, packed inside the app: IBM Plex Sans for text and Silkscreen (a pixel
    /// font) for small labels. Falls back to Segoe UI if they can't be loaded.
    /// </summary>
    static class Fonts
    {
        static readonly PrivateFontCollection Collection = new PrivateFontCollection();
        static FontFamily plex, plexMedium, plexSemi, pixel;

        static Fonts()
        {
            // The fonts are unpacked to a temp folder and loaded from there: loading straight from
            // memory works on Windows, but files work everywhere.
            var asm = Assembly.GetExecutingAssembly();
            string dir = Path.Combine(Path.GetTempPath(), "lmg-client-fonts");
            foreach (var name in asm.GetManifestResourceNames().Where(n => n.EndsWith(".ttf")))
            {
                try
                {
                    Directory.CreateDirectory(dir);
                    string file = Path.Combine(dir, name.Substring("fonts.".Length));
                    using (var s = asm.GetManifestResourceStream(name))
                    {
                        if (!File.Exists(file) || new FileInfo(file).Length != s.Length)
                            using (var o = File.Create(file)) s.CopyTo(o);
                    }
                    Collection.AddFontFile(file);
                }
                catch { }
            }
            foreach (var f in Collection.Families)
            {
                string n = f.Name;
                if (n.IndexOf("Silkscreen", StringComparison.OrdinalIgnoreCase) >= 0) pixel = f;
                // IBM shortens these in the font files: "SmBld" and "Medm"
                else if (n.IndexOf("SemiBold", StringComparison.OrdinalIgnoreCase) >= 0 || n.IndexOf("SmBld", StringComparison.OrdinalIgnoreCase) >= 0) plexSemi = f;
                else if (n.IndexOf("Medium", StringComparison.OrdinalIgnoreCase) >= 0 || n.IndexOf("Medm", StringComparison.OrdinalIgnoreCase) >= 0) plexMedium = f;
                else if (n.IndexOf("Plex", StringComparison.OrdinalIgnoreCase) >= 0) plex = f;
            }
        }

        /// <summary>A copy of the font installed on this PC, if there is one (used before our own).</summary>
        static FontFamily Installed(string name)
        {
            try
            {
                var f = new FontFamily(name);
                // some systems report the weight's family under its short name ("IBM Plex Sans")
                return name.StartsWith(f.Name, StringComparison.OrdinalIgnoreCase) && f.Name.Length > 3 ? f : null;
            }
            catch { return null; }
        }

        static Font Make(FontFamily family, string fallback, float size, FontStyle style = FontStyle.Regular)
        {
            try
            {
                if (family != null && family.IsStyleAvailable(style)) return new Font(family, size, style, GraphicsUnit.Point);
                if (family != null) return new Font(family, size, FontStyle.Regular, GraphicsUnit.Point);
            }
            catch { }
            return new Font(fallback, size, style);
        }

        public static Font Text(float size) => Make(Installed("IBM Plex Sans") ?? plex, "Segoe UI", size);
        public static Font Medium(float size) => Make(Installed("IBM Plex Sans Medm") ?? Installed("IBM Plex Sans Medium") ?? plexMedium ?? plex, "Segoe UI", size);
        public static Font Bold(float size) => Make(Installed("IBM Plex Sans SmBld") ?? Installed("IBM Plex Sans SemiBold") ?? plexSemi ?? plexMedium ?? plex, "Segoe UI Semibold", size);
        public static Font Pixel(float size) => Make(Installed("Silkscreen") ?? pixel, "Consolas", size);
    }
}
