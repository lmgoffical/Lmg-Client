using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace LmgLauncher
{
    /// <summary>
    /// The Photos tab: every screenshot from the game (newest first) as a grid. Click one to see it
    /// big, with Copy (paste it straight into Discord), Open, Show in folder and Delete.
    /// </summary>
    class PhotosView : Control
    {
        public static string Folder => Path.Combine(Paths.GameDir, "screenshots");

        const int ThumbW = 200, ThumbH = 113, Gap = 14;
        List<FileInfo> files = new List<FileInfo>();
        readonly ConcurrentDictionary<string, Image> thumbs = new ConcurrentDictionary<string, Image>();
        readonly HashSet<string> loading = new HashSet<string>();
        int scroll, hover = -1, open = -1;
        Image big;
        string bigPath;
        string hotButton;
        readonly Dictionary<string, RectangleF> buttons = new Dictionary<string, RectangleF>();
        readonly Font fName, fSmall, fBadge;
        FileSystemWatcher watcher;
        public event Action<string> Message;

        public PhotosView(Font name, Font small, Font badge)
        {
            fName = name;
            fSmall = small;
            fBadge = badge;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint
                | ControlStyles.ResizeRedraw | ControlStyles.Selectable, true);
            BackColor = C.Bg;
        }

        public int Count => files.Count;
        public bool Viewing => open >= 0;

        public void Reload()
        {
            try
            {
                var dir = new DirectoryInfo(Folder);
                files = dir.Exists
                    ? dir.GetFiles("*.png").Concat(dir.GetFiles("*.jpg")).OrderByDescending(f => f.LastWriteTimeUtc).ToList()
                    : new List<FileInfo>();
            }
            catch { files = new List<FileInfo>(); }
            if (open >= files.Count) Close();
            scroll = Math.Max(0, Math.Min(scroll, MaxScroll()));
            Watch();
            Invalidate();
            Parent?.Invalidate();   // the count in the heading
        }

        /// <summary>New photos show up on their own while the app is open.</summary>
        void Watch()
        {
            if (watcher != null || !Directory.Exists(Folder)) return;
            try
            {
                watcher = new FileSystemWatcher(Folder) { EnableRaisingEvents = true, IncludeSubdirectories = false };
                FileSystemEventHandler changed = (s, e) => { try { BeginInvoke((Action)Reload); } catch { } };
                watcher.Created += changed;
                watcher.Deleted += changed;
            }
            catch { watcher = null; }
        }

        int Cols => Math.Max(1, (Width + Dpi.D(Gap)) / (Dpi.D(ThumbW) + Dpi.D(Gap)));
        int RowH => Dpi.D(ThumbH) + Dpi.D(Gap) + Dpi.D(22);
        int MaxScroll() => Math.Max(0, (files.Count + Cols - 1) / Cols * RowH - Height + Dpi.D(10));

        RectangleF CellRect(int i)
        {
            int c = i % Cols, r = i / Cols;
            float w = (Width - (Cols - 1) * Dpi.D(Gap)) / (float)Cols;
            float h = w * ThumbH / ThumbW;
            return new RectangleF(c * (w + Dpi.D(Gap)), r * RowH - scroll, w, h);
        }

        int IndexAt(Point p)
        {
            for (int i = 0; i < files.Count; i++) if (CellRect(i).Contains(p)) return i;
            return -1;
        }

        // ---- input -------------------------------------------------------------------------------

        protected override void OnMouseWheel(MouseEventArgs e)
        {
            if (open >= 0) { Step(e.Delta < 0 ? 1 : -1); return; }
            scroll = Math.Max(0, Math.Min(MaxScroll(), scroll - Math.Sign(e.Delta) * RowH / 2));
            Invalidate();
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            if (open >= 0)
            {
                string hot = buttons.FirstOrDefault(b => b.Value.Contains(e.Location)).Key;
                Cursor = hot != null ? Cursors.Hand : Cursors.Default;
                if (hot != hotButton) { hotButton = hot; Invalidate(); }
                return;
            }
            int i = IndexAt(e.Location);
            Cursor = i >= 0 ? Cursors.Hand : Cursors.Default;
            if (i != hover) { hover = i; Invalidate(); }
        }

        protected override void OnMouseLeave(EventArgs e) { hover = -1; hotButton = null; Invalidate(); base.OnMouseLeave(e); }

        protected override void OnMouseClick(MouseEventArgs e)
        {
            Focus();
            if (open >= 0)
            {
                string b = buttons.FirstOrDefault(x => x.Value.Contains(e.Location)).Key;
                switch (b)
                {
                    case "copy": Copy(); break;
                    case "open": Run(files[open].FullName); break;
                    case "folder": Run("explorer.exe", "/select,\"" + files[open].FullName + "\""); break;
                    case "delete": Delete(); break;
                    case "prev": Step(-1); break;
                    case "next": Step(1); break;
                    case "close": Close(); break;
                    case null: if (!ImageRect().Contains(e.Location)) Close(); break;
                }
                return;
            }
            int i = IndexAt(e.Location);
            if (i >= 0) Show(i);
        }

        public bool HandleKey(Keys key)
        {
            if (open < 0) return false;
            if (key == Keys.Escape) { Close(); return true; }
            if (key == Keys.Left) { Step(-1); return true; }
            if (key == Keys.Right) { Step(1); return true; }
            if (key == (Keys.Control | Keys.C)) { Copy(); return true; }
            if (key == Keys.Delete) { Delete(); return true; }
            return false;
        }

        void Show(int i)
        {
            open = i;
            big?.Dispose();
            big = null;
            bigPath = files[i].FullName;
            string path = bigPath;
            Task.Run(() =>
            {
                try
                {
                    // read through a copy, so the file isn't locked (and can be deleted)
                    using (var ms = new MemoryStream(File.ReadAllBytes(path)))
                    {
                        var img = Image.FromStream(ms);
                        BeginInvoke((Action)(() =>
                        {
                            if (path == bigPath) { big = new Bitmap(img); Invalidate(); }
                            img.Dispose();
                        }));
                    }
                }
                catch { }
            });
            Invalidate();
        }

        void Close()
        {
            open = -1;
            big?.Dispose();
            big = null;
            bigPath = null;
            buttons.Clear();
            Invalidate();
        }

        void Step(int d)
        {
            if (files.Count == 0) return;
            Show((open + d + files.Count) % files.Count);
        }

        void Copy()
        {
            if (big == null) return;
            try
            {
                Clipboard.SetImage(big);
                Message?.Invoke("Copied - paste it anywhere (Discord, a message, ...).");
            }
            catch (Exception ex) { Message?.Invoke("Couldn't copy it: " + ex.Message); }
        }

        void Delete()
        {
            if (open < 0) return;
            var f = files[open];
            var ok = MessageBox.Show(this, "Delete " + f.Name + "?\nIt goes to the Recycle Bin.", "Lmg Client",
                MessageBoxButtons.OKCancel, MessageBoxIcon.Question);
            if (ok != DialogResult.OK) return;
            big?.Dispose();
            big = null;
            if (thumbs.TryRemove(f.FullName, out var t)) t.Dispose();
            try
            {
                if (!RecycleBin.Send(f.FullName)) f.Delete();
                Message?.Invoke(f.Name + " deleted.");
            }
            catch (Exception ex) { Message?.Invoke("Couldn't delete it: " + ex.Message); }
            int was = open;
            open = -1;
            Reload();
            if (files.Count > 0) Show(Math.Min(was, files.Count - 1));
        }

        static void Run(string file, string args = null)
        {
            try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(file, args ?? "") { UseShellExecute = true }); }
            catch { }
        }

        // ---- drawing -----------------------------------------------------------------------------

        Image Thumb(FileInfo f)
        {
            if (thumbs.TryGetValue(f.FullName, out var img)) return img;
            lock (loading)
            {
                if (loading.Contains(f.FullName)) return null;
                loading.Add(f.FullName);
            }
            string path = f.FullName;
            Task.Run(() =>
            {
                try
                {
                    using (var ms = new MemoryStream(File.ReadAllBytes(path)))
                    using (var src = Image.FromStream(ms))
                    {
                        int w = Dpi.D(ThumbW) * 2, h = w * src.Height / Math.Max(1, src.Width);
                        var bmp = new Bitmap(w, h);
                        using (var g = Graphics.FromImage(bmp))
                        {
                            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                            g.DrawImage(src, 0, 0, w, h);
                        }
                        thumbs[path] = bmp;
                    }
                    BeginInvoke((Action)Invalidate);
                }
                catch { }
            });
            return null;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Gfx.Setup(g);
            g.Clear(C.Bg);
            if (files.Count == 0) { DrawEmpty(g); return; }

            for (int i = 0; i < files.Count; i++)
            {
                var r = CellRect(i);
                if (r.Bottom < 0 || r.Top > Height) continue;
                bool over = i == hover;
                using (var path = Gfx.Round(r, Dpi.D(6)))
                {
                    using (var b = new SolidBrush(C.Panel2)) g.FillPath(b, path);
                    var img = Thumb(files[i]);
                    if (img != null)
                    {
                        var clip = g.Clip;
                        g.SetClip(path);
                        // fill the card, cropping the long side
                        float s = Math.Max(r.Width / img.Width, r.Height / img.Height);
                        float w = img.Width * s, h = img.Height * s;
                        g.DrawImage(img, r.X + (r.Width - w) / 2, r.Y + (r.Height - h) / 2, w, h);
                        g.Clip = clip;
                    }
                    if (over) using (var pen = new Pen(C.Blue, Dpi.D(2))) g.DrawPath(pen, path);
                }
                var f = files[i];
                bool photo = f.Name.StartsWith("lmg-photo", StringComparison.OrdinalIgnoreCase);
                string when = Ago(f.LastWriteTime);
                Gfx.Text(g, when, fSmall, over ? C.Text : C.Muted, r.X + 2, r.Bottom + Dpi.D(4));
                if (photo)
                {
                    var sz = g.MeasureString("PHOTO MODE", fBadge);
                    var br = new RectangleF(r.Right - sz.Width - Dpi.D(8), r.Bottom + Dpi.D(5), sz.Width + Dpi.D(6), sz.Height);
                    using (var b = new SolidBrush(Color.FromArgb(40, C.Gold))) g.FillRectangle(b, br);
                    Gfx.Text(g, "PHOTO MODE", fBadge, C.Gold, br.X + Dpi.D(3), br.Y);
                }
            }
            if (open >= 0) DrawViewer(g);
        }

        void DrawEmpty(Graphics g)
        {
            float cx = Width / 2f, y = Height / 2f - Dpi.D(40);
            Center(g, "No photos yet", fName, C.Text, cx, y);
            Center(g, "In game, press F8 for photo mode, or F2 for a quick screenshot.", fSmall, C.Muted, cx, y + Dpi.D(26));
            Center(g, "They show up here straight away.", fSmall, C.Faint, cx, y + Dpi.D(46));
        }

        RectangleF ImageRect()
        {
            float pad = Dpi.D(8), barH = Dpi.D(40);
            float maxW = Width - pad * 2 - Dpi.D(80), maxH = Height - pad * 2 - barH;
            if (big == null) return new RectangleF(Width / 2f - Dpi.D(100), maxH / 2f - Dpi.D(56), Dpi.D(200), Dpi.D(112));
            float s = Math.Min(maxW / big.Width, maxH / big.Height);
            float w = big.Width * s, h = big.Height * s;
            return new RectangleF((Width - w) / 2, pad + (maxH - h) / 2, w, h);
        }

        void DrawViewer(Graphics g)
        {
            using (var b = new SolidBrush(Color.FromArgb(235, C.Bg))) g.FillRectangle(b, ClientRectangle);
            var r = ImageRect();
            if (big != null)
            {
                g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                using (var shadow = new SolidBrush(Color.FromArgb(90, 0, 0, 0))) g.FillRectangle(shadow, r.X + 3, r.Y + 5, r.Width, r.Height);
                g.DrawImage(big, r);
            }
            else Center(g, "Loading...", fSmall, C.Muted, r.X + r.Width / 2, r.Y + r.Height / 2 - Dpi.D(8));

            // arrows at the sides
            buttons.Clear();
            Arrow(g, "prev", "‹", Dpi.D(6), r.Y + r.Height / 2 - Dpi.D(22));
            Arrow(g, "next", "›", Width - Dpi.D(42), r.Y + r.Height / 2 - Dpi.D(22));

            // the bar under the photo
            var f = files[open];
            float by = Height - Dpi.D(36);
            Gfx.Text(g, f.Name, fName, C.Text, Dpi.D(8), by + Dpi.D(2));
            Gfx.Text(g, f.LastWriteTime.ToString("d MMM yyyy, HH:mm") + "   " + (open + 1) + " of " + files.Count, fSmall, C.Muted, Dpi.D(8), by + Dpi.D(20));
            float x = Width - Dpi.D(8);
            foreach (var (id, label) in new[] { ("close", "Close"), ("delete", "Delete"), ("folder", "Show in folder"), ("open", "Open"), ("copy", "Copy") })
            {
                var size = g.MeasureString(label, fName);
                float w = size.Width + Dpi.D(22);
                var br = new RectangleF(x - w, by, w, Dpi.D(30));
                x -= w + Dpi.D(8);
                buttons[id] = br;
                bool hot = hotButton == id, primary = id == "copy", danger = id == "delete";
                using (var path = Gfx.Round(br, Dpi.D(6)))
                {
                    Color fill = primary ? (hot ? C.BlueLight : C.Blue) : hot ? C.Hover : C.Panel2;
                    using (var b = new SolidBrush(fill)) g.FillPath(b, path);
                }
                Center(g, label, fName, primary ? Color.White : danger && hot ? C.Bad : C.Text, br.X + br.Width / 2, br.Y + Dpi.D(6));
            }
        }

        void Arrow(Graphics g, string id, string glyph, float x, float y)
        {
            var r = new RectangleF(x, y, Dpi.D(36), Dpi.D(44));
            buttons[id] = r;
            using (var path = Gfx.Round(r, Dpi.D(8)))
            using (var b = new SolidBrush(hotButton == id ? C.Hover : Color.FromArgb(150, C.Panel2))) g.FillPath(b, path);
            Center(g, glyph, fName, C.Text, r.X + r.Width / 2, r.Y + Dpi.D(10));
        }

        static void Center(Graphics g, string text, Font f, Color c, float cx, float y)
        {
            var size = g.MeasureString(text, f);
            Gfx.Text(g, text, f, c, cx - size.Width / 2, y);
        }

        static string Ago(DateTime t)
        {
            var d = DateTime.Now - t;
            if (d.TotalMinutes < 2) return "just now";
            if (d.TotalHours < 1) return (int)d.TotalMinutes + " min ago";
            if (d.TotalDays < 1) return (int)d.TotalHours + " h ago";
            if (d.TotalDays < 7) return (int)d.TotalDays + (d.TotalDays < 2 ? " day ago" : " days ago");
            return t.ToString("d MMM yyyy");
        }
    }

    /// <summary>Deleting to the Recycle Bin, so a mistake can be undone.</summary>
    static class RecycleBin
    {
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        struct SHFILEOPSTRUCT
        {
            public IntPtr hwnd;
            public uint wFunc;
            public string pFrom;
            public string pTo;
            public ushort fFlags;
            public bool fAnyOperationsAborted;
            public IntPtr hNameMappings;
            public string lpszProgressTitle;
        }

        [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
        static extern int SHFileOperation(ref SHFILEOPSTRUCT op);

        public static bool Send(string path)
        {
            try
            {
                var op = new SHFILEOPSTRUCT { wFunc = 3 /* delete */, pFrom = path + "\0\0", fFlags = 0x40 | 0x10 | 0x4 /* undo, no confirm, silent */ };
                return SHFileOperation(ref op) == 0 && !File.Exists(path);
            }
            catch { return false; }
        }
    }
}
