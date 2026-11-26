using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace LmgLauncher
{
    /// <summary>The app window: Play on the left, what's new (or settings) on the right.</summary>
    class MainForm : Form
    {
        // the website's colours
        public static readonly Color Bg = Color.FromArgb(0x07, 0x0B, 0x14);
        public static readonly Color Panel = Color.FromArgb(0x0C, 0x13, 0x22);
        public static readonly Color Panel2 = Color.FromArgb(0x11, 0x1A, 0x2E);
        public static readonly Color Line = Color.FromArgb(0x1C, 0x29, 0x43);
        public static readonly Color TextC = Color.FromArgb(0xE8, 0xED, 0xF5);
        public static readonly Color Muted = Color.FromArgb(0xA3, 0xB0, 0xC4);
        public static readonly Color Faint = Color.FromArgb(0x7C, 0x8B, 0xA3);
        public static readonly Color Blue = Color.FromArgb(0x3D, 0x9B, 0xFF);
        public static readonly Color Gold = Color.FromArgb(0xF2, 0xC1, 0x4E);
        public static readonly Color Bad = Color.FromArgb(0xFF, 0x6B, 0x78);
        public static readonly Color Good = Color.FromArgb(0x4F, 0xD4, 0x9A);

        const int W = 880, H = 540, TITLE = 46;

        readonly Installer installer = new Installer();
        readonly AppSettings settings = AppSettings.Load();
        readonly Font fTitle, fHero, fBody, fSmall, fButton, fPlay, fMono;
        readonly Btn play, settingsBtn, minBtn, closeBtn, gameFolder, modsFolder, website;
        readonly Btn ramMinus, ramPlus, closeToggle, repair, remove, back;
        string heading = "Checking for updates...";
        string sub = "Lmg keeps itself and your mods up to date.";
        string status = "";
        Color statusColor = Faint;
        double progress = -1;
        bool busy, showSettings;
        List<KeyValuePair<string, string>> news = new List<KeyValuePair<string, string>>();
        float scale = 1f;

        public MainForm(bool justUpdated)
        {
            Text = "Lmg Client";
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Bg;
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            scale = Dpi.Scale;
            ClientSize = new Size(D(W), D(H));

            fTitle = new Font("Segoe UI Semibold", 11f);
            fHero = new Font("Segoe UI Semibold", 22f);
            fBody = new Font("Segoe UI", 10.5f);
            fSmall = new Font("Segoe UI", 9f);
            fButton = new Font("Segoe UI Semibold", 9.5f);
            fPlay = new Font("Segoe UI Semibold", 16f);
            fMono = new Font("Consolas", 9f);

            play = Add(new Btn("PLAY", fPlay, Btn.Kind.Primary), 40, 250, 320, 62, () => Play());
            gameFolder = Add(new Btn("Game folder", fButton, Btn.Kind.Ghost), 40, 392, 104, 30, () => OpenFolder(Paths.GameDir));
            modsFolder = Add(new Btn("Mods folder", fButton, Btn.Kind.Ghost), 150, 392, 104, 30, () => OpenFolder(Paths.Mods));
            website = Add(new Btn("Website", fButton, Btn.Kind.Ghost), 260, 392, 100, 30, () => Open("https://lmgoffical.github.io/Lmg-Client/"));
            settingsBtn = Add(new Btn("Settings", fButton, Btn.Kind.Ghost), W - 186, 9, 82, 28, () => { showSettings = !showSettings; Layout2(); });
            minBtn = Add(new Btn("–", fTitle, Btn.Kind.Ghost), W - 98, 9, 40, 28, () => WindowState = FormWindowState.Minimized);
            closeBtn = Add(new Btn("✕", fTitle, Btn.Kind.Ghost), W - 54, 9, 40, 28, Close);

            int sx = 490;
            ramMinus = Add(new Btn("–", fTitle, Btn.Kind.Ghost), sx + 218, 128, 36, 30, () => ChangeRam(-1));
            ramPlus = Add(new Btn("+", fTitle, Btn.Kind.Ghost), sx + 318, 128, 36, 30, () => ChangeRam(1));
            closeToggle = Add(new Btn("", fButton, Btn.Kind.Ghost), sx + 254, 194, 100, 30, () => { settings.CloseOnLaunch = !settings.CloseOnLaunch; settings.Save(); Layout2(); });
            repair = Add(new Btn("Repair", fButton, Btn.Kind.Ghost), sx + 254, 260, 100, 30, () => Play(repairOnly: true));
            remove = Add(new Btn("Remove", fButton, Btn.Kind.Danger), sx + 254, 326, 100, 30, RemoveApp);
            back = Add(new Btn("Back", fButton, Btn.Kind.Ghost), sx + 20, 430, 90, 30, () => { showSettings = false; Layout2(); });
            Layout2();

            Shown += async (s, e) =>
            {
                if (justUpdated) SetStatus("The app updated itself to " + AppSetup.CurrentText + ".", Good);
                await Check();
            };
        }

        int D(int v) => (int)Math.Round(v * scale);

        Btn Add(Btn b, int x, int y, int w, int h, Action click)
        {
            b.Bounds = new Rectangle(D(x), D(y), D(w), D(h));
            b.Click += (s, e) => click();
            Controls.Add(b);
            return b;
        }

        void Layout2()
        {
            foreach (var b in new[] { ramMinus, ramPlus, closeToggle, repair, remove, back }) b.Visible = showSettings;
            closeToggle.Label = settings.CloseOnLaunch ? "On" : "Off";
            settingsBtn.Label = showSettings ? "Close settings" : "Settings";
            settingsBtn.Width = D(showSettings ? 110 : 82);
            settingsBtn.Left = D(showSettings ? W - 214 : W - 186);
            play.Enabled = gameFolder.Enabled = !busy;
            repair.Enabled = !busy;
            Invalidate();
        }

        // ---- the work ---------------------------------------------------------------------------

        async Task Check()
        {
            try
            {
                await installer.Fetch();
                news = installer.Changelog();
                if (await AppSetup.UpdateSelf(installer, s => SetStatus(s, Faint))) { Close(); return; }
                string latest = installer.LatestVersion, have = Installer.InstalledVersion();
                if (have == null) { heading = "Let's get you set up."; sub = "Press Play and I'll install everything (about a minute)."; }
                else if (installer.UpToDate()) { heading = "Ready when you are."; sub = "You're on the newest Lmg Client (" + latest + ")."; }
                else { heading = "There's an update."; sub = "Lmg Client " + latest + " is out (you have " + have + "). Press Play to get it."; }
                SetStatus("", Faint);
            }
            catch (Exception ex)
            {
                heading = Installer.InstalledVersion() != null ? "Ready when you are." : "Can't reach the update server.";
                sub = "Check your internet, then try again.";
                SetStatus("Couldn't check for updates: " + Friendly(ex), Bad);
            }
            Invalidate();
        }

        async void Play(bool repairOnly = false)
        {
            if (busy) return;
            busy = true;
            Layout2();
            try
            {
                if (installer.Manifest == null) await installer.Fetch();
                if (await AppSetup.UpdateSelf(installer, s => SetStatus(s, Faint))) { Close(); return; }
                if (repairOnly || !installer.UpToDate())
                {
                    await installer.Install((msg, p) => BeginInvoke((Action)(() => { status = msg; statusColor = Faint; progress = p; Invalidate(); })),
                        settings.RamGb, repairOnly);
                }
                if (repairOnly)
                {
                    heading = "All fixed.";
                    sub = "Everything was downloaded fresh.";
                    SetStatus("Done.", Good);
                    return;
                }
                Installer.SelectProfile();
                if (Installer.MinecraftLauncherRunning())
                {
                    SetStatus("The Minecraft Launcher is already open: pick \"Lmg Client\" and press Play there.", Gold);
                    return;
                }
                Installer.OpenMinecraftLauncher();
                heading = "Opening Minecraft...";
                sub = "In the Minecraft Launcher, \"Lmg Client\" is picked - just press Play.";
                SetStatus("", Faint);
                if (settings.CloseOnLaunch)
                {
                    await Task.Delay(2500);
                    Close();
                }
            }
            catch (Exception ex)
            {
                SetStatus(Friendly(ex), Bad);
            }
            finally
            {
                busy = false;
                progress = -1;
                if (!IsDisposed) Layout2();
            }
        }

        static string Friendly(Exception ex)
        {
            if (ex is AggregateException ag && ag.InnerException != null) ex = ag.InnerException;
            if (ex is System.Net.Http.HttpRequestException) return "couldn't reach the internet (or GitHub/Modrinth is down).";
            return ex.Message;
        }

        void SetStatus(string text, Color color)
        {
            if (InvokeRequired) { BeginInvoke((Action)(() => SetStatus(text, color))); return; }
            status = text;
            statusColor = color;
            Invalidate();
        }

        void ChangeRam(int dir)
        {
            int total = Installer.TotalRamGb();
            int max = Math.Max(2, Math.Min(12, total - 2));
            int cur = settings.RamGb == 0 ? Installer.AutoRam() : settings.RamGb;
            int next = cur + dir;
            if (dir < 0 && settings.RamGb == 0) next = cur - 1;
            if (next < 2) { settings.RamGb = 0; }                // below 2: back to automatic
            else settings.RamGb = Math.Min(max, next);
            settings.Save();
            SetStatus("Memory changes apply the next time you press Play.", Faint);
            Invalidate();
        }

        void RemoveApp()
        {
            var ok = MessageBox.Show(this,
                "Remove the Lmg Client app and its shortcuts?\n\nYour Lmg game folder, worlds and settings stay. You can still play from the Minecraft Launcher.",
                "Remove Lmg Client", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
            if (ok != DialogResult.Yes) return;
            AppSetup.Uninstall();
            Close();
        }

        void OpenFolder(string path)
        {
            try
            {
                Directory.CreateDirectory(path);
                Process.Start(new ProcessStartInfo("explorer.exe", "\"" + path + "\"") { UseShellExecute = true });
            }
            catch { }
        }

        static void Open(string url)
        {
            try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { }
        }

        // ---- drawing ----------------------------------------------------------------------------

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            g.ScaleTransform(scale, scale);

            // background with a soft glow behind the logo area
            using (var brush = new LinearGradientBrush(new Rectangle(0, 0, W, H), Panel, Bg, 60f)) g.FillRectangle(brush, 0, 0, W, H);
            using (var pen = new Pen(Line)) g.DrawRectangle(pen, 0, 0, W - 1, H - 1);

            // title bar
            Star(g, 26, TITLE / 2f, 9f, Blue, 2f);
            TextAt(g, "Lmg Client", fTitle, TextC, 42, 13);
            TextAt(g, "app " + AppSetup.CurrentText, fSmall, Gold, 42 + Measure(g, "Lmg Client", fTitle) + 8, 15);
            using (var pen = new Pen(Line)) g.DrawLine(pen, 0, TITLE, W, TITLE);

            // big faint star behind the left side
            Star(g, 200, 300, 150f, Color.FromArgb(18, Blue), 10f);

            // left: what's going on + Play
            using (var b = new SolidBrush(TextC)) g.DrawString(heading, fHero, b, new RectangleF(38, 84, 430, 90));
            using (var b = new SolidBrush(Muted)) g.DrawString(sub, fBody, b, new RectangleF(40, 158, 420, 52));
            if (Installer.InstalledVersion() is string have)
                TextAt(g, "Lmg Client " + have + "  ·  Minecraft " + (installer.Manifest != null ? Installer.Str(installer.Manifest, "minecraft") : "26.3"), fSmall, Faint, 40, 214);

            // progress bar under Play
            if (progress >= 0)
            {
                Round(g, new RectangleF(40, 324, 320, 5), 2.5f, Line);
                Round(g, new RectangleF(40, 324, (float)(320 * Math.Max(0.02, progress)), 5), 2.5f, Blue);
            }
            using (var b = new SolidBrush(statusColor)) g.DrawString(status, fSmall, b, new RectangleF(40, 336, 400, 44));

            // right card: what's new, or settings
            var card = new RectangleF(490, 70, 360, 400);
            Round(g, card, 10f, Panel);
            using (var pen = new Pen(Line)) RoundOutline(g, card, 10f, pen);
            if (showSettings) DrawSettings(g, card);
            else DrawNews(g, card);

            // footer
            TextAt(g, "Not made by Mojang or Microsoft. You sign in with the normal Minecraft Launcher - this app never sees your password.", fSmall, Faint, 40, 500);
        }

        void DrawNews(Graphics g, RectangleF card)
        {
            TextAt(g, "What's new", fTitle, TextC, card.X + 20, card.Y + 16);
            float y = card.Y + 48;
            if (news.Count == 0)
            {
                TextAt(g, "Loading...", fSmall, Faint, card.X + 20, y);
                return;
            }
            foreach (var n in news)
            {
                if (y > card.Bottom - 50) break;
                TextAt(g, "v" + n.Key, fMono, Gold, card.X + 20, y);
                var rect = new RectangleF(card.X + 70, y - 1, card.Width - 90, 200);
                var size = g.MeasureString(n.Value, fSmall, (int)rect.Width);
                float h = Math.Min(size.Height, card.Bottom - 20 - y);
                using (var b = new SolidBrush(Muted)) g.DrawString(n.Value, fSmall, b, new RectangleF(rect.X, rect.Y, rect.Width, h));
                y += Math.Max(20, size.Height) + 12;
            }
        }

        void DrawSettings(Graphics g, RectangleF card)
        {
            TextAt(g, "Settings", fTitle, TextC, card.X + 20, card.Y + 16);
            float x = card.X + 20;
            int ram = settings.RamGb == 0 ? Installer.AutoRam() : settings.RamGb;
            Row(g, x, 132, "Memory for Minecraft", "How much RAM the game can use.");
            string ramText = settings.RamGb == 0 ? "Auto (" + ram + " GB)" : ram + " GB";
            float tw = g.MeasureString(ramText, fButton).Width;
            TextAt(g, ramText, fButton, TextC, card.X + 300 - tw / 2f, 134);
            Row(g, x, 198, "Close when Minecraft opens", "Gets out of your way after Play.");
            Row(g, x, 264, "Repair", "Downloads everything again.");
            Row(g, x, 330, "Remove the app", "Your worlds and settings stay.");
            TextAt(g, "Game folder: " + Paths.GameDir, fSmall, Faint, x, 392);
        }

        void Row(Graphics g, float x, float y, string title, string hint)
        {
            TextAt(g, title, fBody, TextC, x, y - 4);
            TextAt(g, hint, fSmall, Faint, x, y + 16);
        }

        static void TextAt(Graphics g, string text, Font f, Color c, float x, float y)
        {
            using (var b = new SolidBrush(c)) g.DrawString(text, f, b, x, y);
        }

        static float Measure(Graphics g, string text, Font f) => g.MeasureString(text, f).Width;

        /// <summary>The Lmg logo: two outlined triangles.</summary>
        public static void Star(Graphics g, float cx, float cy, float r, Color color, float width)
        {
            using (var pen = new Pen(color, width) { LineJoin = LineJoin.Round })
            {
                foreach (double rot in new[] { 0.0, Math.PI })
                {
                    var pts = Enumerable.Range(0, 3).Select(k => new PointF(
                        cx + (float)Math.Sin(rot + 2 * Math.PI * k / 3) * r,
                        cy - (float)Math.Cos(rot + 2 * Math.PI * k / 3) * r)).ToArray();
                    g.DrawPolygon(pen, pts);
                }
            }
        }

        public static GraphicsPath RoundPath(RectangleF r, float radius)
        {
            var p = new GraphicsPath();
            float d = radius * 2;
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        public static void Round(Graphics g, RectangleF r, float radius, Color c)
        {
            using (var path = RoundPath(r, radius))
            using (var b = new SolidBrush(c)) g.FillPath(b, path);
        }

        static void RoundOutline(Graphics g, RectangleF r, float radius, Pen pen)
        {
            using (var path = RoundPath(r, radius)) g.DrawPath(pen, path);
        }

        // ---- drag the window by its top bar -----------------------------------------------------

        [DllImport("user32.dll")] static extern bool ReleaseCapture();
        [DllImport("user32.dll")] static extern IntPtr SendMessage(IntPtr hWnd, int msg, int wParam, int lParam);

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button == MouseButtons.Left && e.Y < D(TITLE))
            {
                ReleaseCapture();
                SendMessage(Handle, 0xA1, 0x2, 0);   // WM_NCLBUTTONDOWN, HTCAPTION
            }
        }

        protected override CreateParams CreateParams
        {
            get
            {
                var cp = base.CreateParams;
                cp.Style |= 0x20000;     // WS_MINIMIZEBOX: lets the taskbar button minimise it
                cp.ClassStyle |= 0x20000; // CS_DROPSHADOW
                return cp;
            }
        }
    }

    /// <summary>Screen scaling (125%, 150%...), read the classic way so it works on every Windows.</summary>
    static class Dpi
    {
        public static readonly float Scale = Read();

        static float Read()
        {
            try
            {
                using (var g = Graphics.FromHwnd(IntPtr.Zero)) return Math.Max(1f, g.DpiX / 96f);
            }
            catch { return 1f; }
        }
    }

    /// <summary>A flat rounded button with a hover fade.</summary>
    class Btn : Control
    {
        public enum Kind { Primary, Ghost, Danger }

        readonly Kind kind;
        float hover;
        readonly Timer timer = new Timer { Interval = 15 };
        bool over;
        string label;

        public string Label { get => label; set { label = value; Invalidate(); } }

        public Btn(string label, Font font, Kind kind)
        {
            this.label = label;
            this.kind = kind;
            Font = font;
            Cursor = Cursors.Hand;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
            timer.Tick += (s, e) =>
            {
                float target = over && Enabled ? 1f : 0f;
                hover += (target - hover) * 0.25f;
                if (Math.Abs(target - hover) < 0.01f) { hover = target; timer.Stop(); }
                Invalidate();
            };
        }

        protected override void OnMouseEnter(EventArgs e) { over = true; timer.Start(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { over = false; timer.Start(); base.OnMouseLeave(e); }
        protected override void OnEnabledChanged(EventArgs e) { Invalidate(); base.OnEnabledChanged(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            var r = new RectangleF(0.5f, 0.5f, Width - 1.5f, Height - 1.5f);
            float radius = Math.Min(8f * Dpi.Scale, Height / 2f);
            Color fill, text, border;
            switch (kind)
            {
                case Kind.Primary:
                    fill = Enabled ? Mix(MainForm.Blue, Color.FromArgb(0x6F, 0xB6, 0xFF), hover) : MainForm.Line;
                    text = Enabled ? Color.White : MainForm.Faint;
                    border = fill;
                    break;
                case Kind.Danger:
                    fill = Mix(MainForm.Panel2, Color.FromArgb(0x3A, 0x16, 0x1E), hover);
                    text = MainForm.Bad;
                    border = Mix(MainForm.Line, MainForm.Bad, hover);
                    break;
                default:
                    fill = Mix(MainForm.Panel2, Color.FromArgb(0x16, 0x21, 0x3A), hover);
                    text = Enabled ? Mix(MainForm.Muted, Color.White, hover) : MainForm.Faint;
                    border = Mix(MainForm.Line, MainForm.Blue, hover * 0.8f);
                    break;
            }
            using (var path = MainForm.RoundPath(r, radius))
            {
                using (var b = new SolidBrush(fill)) g.FillPath(b, path);
                using (var p = new Pen(border)) g.DrawPath(p, path);
            }
            TextRenderer.DrawText(g, label, Font, Rectangle.Round(r), text,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPadding);
        }

        static Color Mix(Color a, Color b, float t) => Color.FromArgb(
            (int)(a.R + (b.R - a.R) * t), (int)(a.G + (b.G - a.G) * t), (int)(a.B + (b.B - a.B) * t));
    }
}
