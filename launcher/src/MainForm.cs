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
    /// <summary>
    /// The app window. Kept plain on purpose: the website's fonts and colours, text links instead
    /// of a wall of buttons, one solid Play button, and real information instead of slogans.
    /// </summary>
    class MainForm : Form
    {
        const int W = 940, H = 580, TITLE = 50;
        enum Tab { Home, Mods, Photos, Settings }

        readonly Installer installer = new Installer();
        readonly AppSettings settings = AppSettings.Load();
        readonly Font fWord, fNav, fHello, fH2, fBody, fSmall, fTiny, fLink, fPlay, fPixel, fPixelSmall, fRow;
        readonly Timer gameWatch = new Timer { Interval = 3000 };
        readonly Timer anim = new Timer { Interval = 30 };

        Tab tab = Tab.Home;
        Account account;
        string line1 = "Checking for updates...";
        string status = "";
        Color statusColor = C.Faint;
        double progress = -1;
        bool busy, playing, updateReady, checkedOnce;
        FileInfo crash;
        float stickerIn;
        List<KeyValuePair<string, string>> news = new List<KeyValuePair<string, string>>();

        readonly List<Control> homeControls = new List<Control>(), modsControls = new List<Control>(), settingsControls = new List<Control>(),
            photosControls = new List<Control>();
        readonly Btn navHome, navMods, navPhotos, navSettings, play, switchLink;
        readonly PhotosView photosView;
        RectangleF crashOpenRect, crashHideRect, changelogRect;   // links drawn onto the window
        int hotLink;
        readonly ModsView modsView;
        readonly Slider ram;
        readonly Toggle shaders, closeOnLaunch;

        public MainForm(bool justUpdated, string startTab = null)
        {
            Text = "Lmg Client";
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = C.Bg;
            DoubleBuffered = true;
            KeyPreview = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            ClientSize = new Size(Dpi.D(W), Dpi.D(H));
            RestorePosition();

            fWord = Fonts.Bold(11.5f);
            fNav = Fonts.Medium(10f);
            fHello = Fonts.Bold(24f);
            fH2 = Fonts.Bold(18f);
            fBody = Fonts.Text(10.5f);
            fSmall = Fonts.Text(9.5f);
            fTiny = Fonts.Text(8.5f);
            fLink = Fonts.Medium(9.5f);
            fPlay = Fonts.Bold(15f);
            fPixel = Fonts.Pixel(9f);
            fPixelSmall = Fonts.Pixel(7f);
            fRow = Fonts.Bold(10.5f);

            // title bar: name, three plain links, window buttons
            navHome = Add(null, new Btn("Home", fNav, Btn.Kind.Nav), 214, 10, 64, 34, () => ShowTab(Tab.Home));
            navMods = Add(null, new Btn("Mods", fNav, Btn.Kind.Nav), 282, 10, 60, 34, () => ShowTab(Tab.Mods));
            navPhotos = Add(null, new Btn("Photos", fNav, Btn.Kind.Nav), 346, 10, 70, 34, () => ShowTab(Tab.Photos));
            navSettings = Add(null, new Btn("Settings", fNav, Btn.Kind.Nav), 420, 10, 80, 34, () => ShowTab(Tab.Settings));
            Add(null, new Btn("—", fNav, Btn.Kind.Window), W - 86, 10, 36, 30, () => WindowState = FormWindowState.Minimized);
            Add(null, new Btn("✕", fNav, Btn.Kind.Window), W - 46, 10, 36, 30, Close);

            // home
            play = Add(homeControls, new Btn("Play", fPlay, Btn.Kind.Primary) { TagFont = fPixel }, 48, 238, 300, 58, () => Play());
            switchLink = Link(homeControls, "switch account", 0, 180, () => Installer.OpenMinecraftLauncher());
            int lx = 48;
            foreach (var (label, action) in new (string, Action)[] {
                ("Game folder", () => OpenFolder(Paths.GameDir)), ("Mods folder", () => OpenFolder(Paths.Mods)),
                ("Screenshots", () => OpenFolder(Path.Combine(Paths.GameDir, "screenshots"))), ("Website", () => Open("https://lmgoffical.github.io/Lmg-Client/")) })
            {
                var l = Link(homeControls, label, lx, 404, action);
                lx += (int)(l.Width / Dpi.Scale) + 22;
            }

            // mods
            modsView = Add(modsControls, new ModsView(Fonts.Bold(10.5f), fSmall, fPixelSmall), 48, 120, 844, 382, null);
            modsView.Message += m => SetStatus(m, m.EndsWith(" is on.") || m.EndsWith(" is off.") ? C.Faint : C.Gold);
            var findMore = Link(modsControls, "Find more on Modrinth", 0, 80, () => Open("https://modrinth.com/mods?g=categories:fabric"));
            findMore.Left = Dpi.D(892) - findMore.Width;
            var openFolder = Link(modsControls, "Open the folder", 0, 80, () => OpenFolder(Paths.Mods));
            openFolder.Left = findMore.Left - openFolder.Width - Dpi.D(20);

            // photos: your screenshots, newest first
            photosView = Add(photosControls, new PhotosView(Fonts.Bold(10.5f), fSmall, fPixelSmall), 48, 128, 844, 394, null);
            photosView.Message += m => SetStatus(m, C.Faint);
            var openShots = Link(photosControls, "Open the folder", 0, 80, () => OpenFolder(PhotosView.Folder));
            openShots.Left = Dpi.D(892) - openShots.Width;

            // settings: one plain list, control on the right of each row
            int total = Installer.TotalRamGb();
            ram = Add(settingsControls, new Slider { Min = 1, Max = Math.Max(3, Math.Min(12, total - 2)) }, 560, 120, 220, 26, null);
            ram.Value = settings.RamGb == 0 ? 1 : settings.RamGb;
            ram.Changed += v =>
            {
                settings.RamGb = v <= 1 ? 0 : v;
                settings.Save();
                SetStatus("Memory changes kick in next time you press Play.", C.Faint);
            };
            shaders = Add(settingsControls, new Toggle(), 744, 178, 40, 22, null);
            shaders.Changed += on =>
            {
                try { Game.SetShaders(on); SetStatus(on ? "Shaders will be on next time." : "Shaders will be off next time.", C.Faint); }
                catch (Exception ex) { SetStatus("Couldn't change shaders: " + ex.Message, C.Bad); }
            };
            closeOnLaunch = Add(settingsControls, new Toggle { On = settings.CloseOnLaunch }, 744, 236, 40, 22, null);
            closeOnLaunch.Changed += on => { settings.CloseOnLaunch = on; settings.Save(); };
            Link(settingsControls, "Check now", 700, 292, async () => { ShowTab(Tab.Home); await Check(); });
            Link(settingsControls, "Repair", 700, 350, () => { ShowTab(Tab.Home); Play(repairOnly: true); });
            Link(settingsControls, "Open", 700, 408, () => OpenFolder(Paths.GameDir));
            Add(settingsControls, new Btn("Remove", fLink, Btn.Kind.Danger), 700, 458, 80, 26, RemoveApp);
            int sx = 48;
            foreach (var (label, url) in new[] { ("Website", "https://lmgoffical.github.io/Lmg-Client/"), ("Privacy", "https://lmgoffical.github.io/Lmg-Client/#/privacy"),
                ("Report a problem", "https://github.com/lmgoffical/Lmg-Client/issues/new") })
            {
                var l = Link(settingsControls, label, sx, 516, () => Open(url));
                sx += (int)(l.Width / Dpi.Scale) + 22;
            }

            ShowTab(startTab == "mods" ? Tab.Mods : startTab == "settings" ? Tab.Settings : startTab == "photos" ? Tab.Photos : Tab.Home);
            gameWatch.Tick += async (s, e) => await WatchGame();
            anim.Tick += (s, e) =>
            {
                float target = updateReady && tab == Tab.Home ? 1f : 0f;
                if (Math.Abs(stickerIn - target) < 0.01f) { stickerIn = target; return; }
                stickerIn += (target - stickerIn) * 0.18f;
                Invalidate(false);
            };
            anim.Start();

            Shown += async (s, e) =>
            {
                if (justUpdated) SetStatus("Updated the app to " + AppSetup.CurrentText + ".", C.Good);
                account = Account.ReadActive();
                Invalidate();
                var skin = account?.LoadSkin();
                RefreshCrash();
                await Check();
                if (skin != null) { await skin; Invalidate(); }
                gameWatch.Start();
                await WatchGame();
            };
        }

        T Add<T>(List<Control> group, T c, int x, int y, int w, int h, Action click) where T : Control
        {
            c.Bounds = new Rectangle(Dpi.D(x), Dpi.D(y), Dpi.D(w), Dpi.D(h));
            if (click != null) c.Click += (s, e) => click();
            group?.Add(c);
            Controls.Add(c);
            return c;
        }

        Btn Link(List<Control> group, string text, int x, int y, Action click)
        {
            var b = Add(group, new Btn(text, fLink, Btn.Kind.Link), x, y, 100, 24, click);
            b.FitWidth();
            return b;
        }

        void ShowTab(Tab t)
        {
            tab = t;
            navHome.Selected = t == Tab.Home;
            navMods.Selected = t == Tab.Mods;
            navPhotos.Selected = t == Tab.Photos;
            navSettings.Selected = t == Tab.Settings;
            foreach (var c in homeControls) c.Visible = t == Tab.Home;
            foreach (var c in modsControls) c.Visible = t == Tab.Mods;
            foreach (var c in settingsControls) c.Visible = t == Tab.Settings;
            foreach (var c in photosControls) c.Visible = t == Tab.Photos;
            if (t == Tab.Photos) photosView.Reload();
            if (t == Tab.Mods) modsView.Reload(playing);
            if (t == Tab.Settings)
            {
                shaders.On = Game.ShadersOn();
                shaders.Enabled = !playing && Game.ShaderPack() != null;
            }
            status = "";
            UpdateHome();
            foreach (Control c in Controls) c.Invalidate();
            Invalidate();
        }

        void UpdateHome()
        {
            bool home = tab == Tab.Home;
            switchLink.Visible = home;
            switchLink.Label = account == null ? "open the Minecraft Launcher" : "switch account";
            switchLink.FitWidth();
            play.Enabled = !busy && !playing;
            play.Label = playing ? "Playing" : busy ? "One sec..." : updateReady ? "Update & play" : "Play";
            play.Tag2 = Installer.InstalledVersion() is string v && !updateReady ? v.Replace(".0", "") : installer.LatestVersion?.Replace(".0", "");
            play.Invalidate();
        }

        // ---- the work ---------------------------------------------------------------------------

        async Task Check()
        {
            try
            {
                await installer.Fetch();
                news = installer.Changelog();
                if (await AppSetup.UpdateSelf(installer, s => SetStatus(s, C.Faint))) { Close(); return; }
                string latest = installer.LatestVersion, have = Installer.InstalledVersion();
                updateReady = have != null && !installer.UpToDate();
                if (have == null) line1 = "Nothing's installed yet. Press Play and give it about a minute.";
                else if (!updateReady) line1 = "You're on " + Short(latest) + ", the newest one." + LastPlayed();
                else line1 = Short(latest) + " is out (you have " + Short(have) + "). Play updates it first.";
                SetStatus("", C.Faint);
            }
            catch (Exception ex)
            {
                bool have = Installer.InstalledVersion() != null;
                line1 = have ? "Couldn't check for updates, but you can still play." : "I can't reach the update server. Check your internet?";
                SetStatus(Friendly(ex), C.Bad);
            }
            checkedOnce = true;
            UpdateHome();
            Invalidate();
        }

        static string Short(string v) => v?.EndsWith(".0") == true ? v.Substring(0, v.Length - 2) : v;

        static string LastPlayed()
        {
            try
            {
                var log = new FileInfo(Path.Combine(Paths.GameDir, "logs", "latest.log"));
                if (!log.Exists) return "";
                var ago = DateTime.Now - log.LastWriteTime;
                string when = ago.TotalMinutes < 2 ? "just now" : ago.TotalHours < 1 ? (int)ago.TotalMinutes + " minutes ago"
                    : ago.TotalDays < 1 ? (int)ago.TotalHours + (ago.TotalHours < 2 ? " hour ago" : " hours ago")
                    : ago.TotalDays < 2 ? "yesterday" : (int)ago.TotalDays + " days ago";
                return " Last played " + when + ".";
            }
            catch { return ""; }
        }

        async void Play(bool repairOnly = false)
        {
            if (busy || playing) return;
            busy = true;
            UpdateHome();
            try
            {
                if (installer.Manifest == null) await installer.Fetch();
                if (await AppSetup.UpdateSelf(installer, s => SetStatus(s, C.Faint))) { Close(); return; }
                if (repairOnly || !installer.UpToDate())
                {
                    await installer.Install((msg, p) => BeginInvoke((Action)(() =>
                    {
                        status = msg;
                        statusColor = C.Faint;
                        progress = p;
                        Invalidate();
                    })), settings.RamGb, repairOnly);
                    updateReady = false;
                }
                if (repairOnly)
                {
                    line1 = "Everything's been downloaded fresh.";
                    SetStatus("Repaired.", C.Good);
                    return;
                }
                Installer.SelectProfile();
                line1 = "The Minecraft Launcher's opening with Lmg Client picked. Press Play there.";
                if (Installer.MinecraftLauncherRunning())
                {
                    SetStatus("The Minecraft Launcher was already open, so pick \"Lmg Client\" there.", C.Gold);
                    return;
                }
                Installer.OpenMinecraftLauncher();
                SetStatus("", C.Faint);
                if (settings.CloseOnLaunch)
                {
                    await Task.Delay(2500);
                    Close();
                }
            }
            catch (Exception ex)
            {
                SetStatus(Friendly(ex), C.Bad);
            }
            finally
            {
                busy = false;
                progress = -1;
                if (!IsDisposed) { UpdateHome(); Invalidate(); }
            }
        }

        async Task WatchGame()
        {
            bool now = await Task.Run(() => Game.Running());
            if (now == playing) return;
            playing = now;
            if (playing) line1 = "Minecraft's running. Mods and shaders can be changed once you close it.";
            else
            {
                RefreshCrash();
                await Check();
            }
            UpdateHome();
            if (tab != Tab.Home) ShowTab(tab);
            Invalidate();
        }

        void RefreshCrash()
        {
            var latest = Game.LatestCrash();
            crash = latest != null && latest.Name != settings.CrashSeen && latest.LastWriteTimeUtc > DateTime.UtcNow.AddDays(-3) ? latest : null;
            UpdateHome();
        }

        void OpenCrash()
        {
            if (crash == null) return;
            try { Process.Start(new ProcessStartInfo(crash.FullName) { UseShellExecute = true }); } catch { }
        }

        void DismissCrash()
        {
            if (crash != null) { settings.CrashSeen = crash.Name; settings.Save(); }
            crash = null;
            UpdateHome();
            Invalidate();
        }

        static string Friendly(Exception ex)
        {
            if (ex is AggregateException ag && ag.InnerException != null) ex = ag.InnerException;
            if (ex is System.Net.Http.HttpRequestException) return "No connection (or GitHub/Modrinth is having a moment).";
            return ex.Message;
        }

        void SetStatus(string text, Color color)
        {
            if (InvokeRequired) { BeginInvoke((Action)(() => SetStatus(text, color))); return; }
            status = text;
            statusColor = color;
            Invalidate();
        }

        void RemoveApp()
        {
            var ok = MessageBox.Show(this,
                "Remove the Lmg Client app and its shortcuts?\n\nYour Lmg game folder, worlds and settings stay, and you can still play from the Minecraft Launcher.",
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

        protected override void OnKeyDown(KeyEventArgs e)
        {
            if (tab == Tab.Photos && photosView.HandleKey(e.KeyData)) { e.Handled = true; return; }
            if (e.KeyCode == Keys.Enter && tab == Tab.Home) { Play(); e.Handled = true; }
            else if (e.KeyCode == Keys.Escape && tab != Tab.Home) { ShowTab(Tab.Home); e.Handled = true; }
            else if (e.Control && e.KeyCode == Keys.D1) ShowTab(Tab.Home);
            else if (e.Control && e.KeyCode == Keys.D2) ShowTab(Tab.Mods);
            else if (e.Control && e.KeyCode == Keys.D3) ShowTab(Tab.Photos);
            else if (e.Control && e.KeyCode == Keys.D4) ShowTab(Tab.Settings);
            base.OnKeyDown(e);
        }

        // ---- drawing ----------------------------------------------------------------------------

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Gfx.Setup(g);
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            g.ScaleTransform(Dpi.Scale, Dpi.Scale);

            g.Clear(C.Bg);
            if (tab == Tab.Home)
            {
                // the little landscape lives on Home; the other tabs stay quiet
                var oldInterp = g.InterpolationMode;
                g.InterpolationMode = InterpolationMode.NearestNeighbor;
                g.DrawImage(Scenery.Get(W, 150), 0, H - 150, W, 150);
                g.InterpolationMode = oldInterp;
            }
            using (var pen = new Pen(C.Line)) g.DrawRectangle(pen, 0, 0, W - 1, H - 1);

            // title bar
            Gfx.Star(g, 26, 26, 8f, C.Blue, 1.8f);
            Gfx.Text(g, "lmg client", fWord, C.Text, 42, 15);
            float ww = g.MeasureString("lmg client", fWord).Width;
            Gfx.Text(g, "app " + AppSetup.CurrentText, fPixelSmall, C.Gold, 42 + ww + 6, 20);
            using (var pen = new Pen(C.Line)) g.DrawLine(pen, 0, TITLE, W, TITLE);

            switch (tab)
            {
                case Tab.Home: DrawHome(g); break;
                case Tab.Mods: DrawMods(g); break;
                case Tab.Photos: DrawPhotos(g); break;
                default: DrawSettings(g); break;
            }
            Gfx.Text(g, "made by lmg. not affiliated with Mojang or Microsoft. you sign in with the normal Minecraft Launcher; this app never sees your password.",
                fTiny, C.Alpha(C.Faint, 0.8f), 48, H - 24);
        }

        void DrawHome(Graphics g)
        {
            // your face, a little crooked, like a photo stuck on
            var state = g.Save();
            g.TranslateTransform(84, 120);
            g.RotateTransform(-3f);
            var face = new RectangleF(-36, -36, 72, 72);
            using (var b = new SolidBrush(C.Line2)) g.FillRectangle(b, RectangleF.Inflate(face, 3, 3));
            Account.DrawFace(g, account?.Skin, face);
            g.Restore(state);

            string name = account?.Name;
            string hello = name != null ? "Hey " + name + "." : "Hey there.";
            Gfx.Text(g, hello, fHello, C.Text, 140, 80);
            if (name != null)
            {
                // a hand-drawn gold underline under your name
                float start = 140 + g.MeasureString("Hey ", fHello).Width - 6;
                float width = g.MeasureString(name, fHello).Width - 8;
                Squiggle(g, start, 124, width);
            }
            Gfx.TextIn(g, checkedOnce ? line1 : "Checking for updates...", fBody, C.Muted, new RectangleF(141, 134, 420, 44));
            string who = account != null ? "Signed in through the Minecraft Launcher." : "Not signed in yet.";
            Gfx.Text(g, who, fSmall, C.Faint, 141, 182);
            switchLink.Left = Dpi.D(141 + g.MeasureString(who, fSmall).Width + 2);
            switchLink.Top = Dpi.D(180);

            // a little sticker on Play when there's an update
            if (stickerIn > 0.01f)
            {
                var s = g.Save();
                g.TranslateTransform(342, 236);
                g.RotateTransform(8f);
                g.ScaleTransform(stickerIn, stickerIn);
                var r = new RectangleF(-30, -11, 60, 22);
                Gfx.Fill(g, r, 3, C.Gold);
                using (var fmt = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
                using (var b = new SolidBrush(C.Bg)) g.DrawString("NEW!", fPixel, b, r, fmt);
                g.Restore(s);
            }

            if (progress >= 0)
            {
                using (var b = new SolidBrush(C.Line)) g.FillRectangle(b, 48, 308, 300, 3);
                using (var b = new SolidBrush(C.Blue)) g.FillRectangle(b, 48, 308, (float)(300 * Math.Max(0.02, Math.Min(1, progress))), 3);
            }
            Gfx.TextIn(g, status, fSmall, statusColor, new RectangleF(48, 318, 480, 36));

            crashOpenRect = crashHideRect = RectangleF.Empty;
            if (crash != null)
            {
                using (var b = new SolidBrush(C.Bad)) g.FillRectangle(b, 48, 368, 6, 6);
                string msg = "Minecraft crashed " + When(crash.LastWriteTime) + ".";
                Gfx.Text(g, msg, fSmall, C.Bad, 60, 361);
                float mx = 60 + g.MeasureString(msg, fSmall).Width + 8;
                crashOpenRect = DrawnLink(g, "open the report", mx, 361, hotLink == 1);
                crashHideRect = DrawnLink(g, "hide", crashOpenRect.Right + 12, 361, hotLink == 2);
            }

            // changelog, plain
            float x = 600, y = 82;
            Gfx.Text(g, "CHANGELOG", fPixelSmall, C.Faint, x, y);
            y += 22;
            if (news.Count == 0) Gfx.Text(g, "Loading...", fSmall, C.Faint, x, y);
            for (int i = 0; i < news.Count; i++)
            {
                var n = news[i];
                var size = g.MeasureString(n.Value, fSmall, 290);
                if (y + size.Height + 20 > 470) break;
                Gfx.Text(g, Short(n.Key), fPixel, C.Gold, x, y);
                using (var b = new SolidBrush(i == 0 ? C.Text : C.Muted)) g.DrawString(n.Value, fSmall, b, new RectangleF(x, y + 16, 300, size.Height + 2));
                y += size.Height + 30;
            }
            changelogRect = news.Count > 0 ? DrawnLink(g, "Full changelog on the website", x, Math.Min(470, y - 6), hotLink == 3) : RectangleF.Empty;
        }

        /// <summary>A text link drawn straight onto the window (no separate control).</summary>
        RectangleF DrawnLink(Graphics g, string text, float x, float y, bool hot)
        {
            var size = g.MeasureString(text, fLink);
            Gfx.Text(g, text, fLink, hot ? C.BlueLight : C.Muted, x, y);
            if (hot) using (var p = new Pen(C.BlueLight)) g.DrawLine(p, x + 2, y + size.Height - 3, x + size.Width - 3, y + size.Height - 3);
            return new RectangleF(x, y, size.Width, size.Height);
        }

        PointF Logical(Point p) => new PointF(p.X / Dpi.Scale, p.Y / Dpi.Scale);

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            var p = Logical(e.Location);
            int hot = tab != Tab.Home ? 0 : crashOpenRect.Contains(p) ? 1 : crashHideRect.Contains(p) ? 2 : changelogRect.Contains(p) ? 3 : 0;
            if (hot != hotLink) { hotLink = hot; Cursor = hot != 0 ? Cursors.Hand : Cursors.Default; Invalidate(); }
        }

        protected override void OnMouseClick(MouseEventArgs e)
        {
            base.OnMouseClick(e);
            if (tab != Tab.Home || e.Button != MouseButtons.Left) return;
            var p = Logical(e.Location);
            if (crashOpenRect.Contains(p)) OpenCrash();
            else if (crashHideRect.Contains(p)) DismissCrash();
            else if (changelogRect.Contains(p)) Open("https://lmgoffical.github.io/Lmg-Client/#/changelog");
        }

        static string When(DateTime t)
        {
            var d = DateTime.Now.Date - t.Date;
            string day = d.TotalDays < 1 ? "today" : d.TotalDays < 2 ? "yesterday" : "on " + t.ToString("dddd");
            return day + " at " + t.ToString("h:mm tt").ToLowerInvariant();
        }

        /// <summary>A wobbly hand-drawn underline (same idea as the website's).</summary>
        static void Squiggle(Graphics g, float x, float y, float w)
        {
            using (var pen = new Pen(C.Gold, 2.6f) { StartCap = LineCap.Round, EndCap = LineCap.Round })
            {
                var pts = new[] {
                    new PointF(x, y + 3), new PointF(x + w * 0.18f, y - 1), new PointF(x + w * 0.36f, y + 4),
                    new PointF(x + w * 0.55f, y + 1), new PointF(x + w * 0.78f, y + 3), new PointF(x + w, y - 1) };
                g.DrawCurve(pen, pts, 0.6f);
            }
        }

        void DrawMods(Graphics g)
        {
            Gfx.Text(g, "Mods", fH2, C.Text, 46, 70);
            int on = modsView.Count - modsView.OffCount;
            string count = modsView.Count == 0 ? "None yet." : on + " on" + (modsView.OffCount > 0 ? ", " + modsView.OffCount + " switched off" : "") + ". Changes apply next time you play.";
            Gfx.Text(g, count, fSmall, C.Faint, 48, 100);
            if (playing) Gfx.Text(g, "Minecraft's running, so switching is paused until you close it.", fSmall, C.Gold, 48, 512);
            else Gfx.TextIn(g, status, fSmall, statusColor, new RectangleF(48, 510, 844, 20));
        }

        void DrawPhotos(Graphics g)
        {
            Gfx.Text(g, "Photos", fH2, C.Text, 46, 70);
            string count = photosView.Count == 0 ? "Screenshots and photo mode shots from the game."
                : photosView.Count + (photosView.Count == 1 ? " photo" : " photos") + ". Click one to see it big, copy it or share it.";
            Gfx.Text(g, count, fSmall, C.Faint, 48, 100);
            Gfx.TextIn(g, status, fSmall, statusColor, new RectangleF(48, 528, 844, 20));
        }

        void DrawSettings(Graphics g)
        {
            Gfx.Text(g, "Settings", fH2, C.Text, 46, 70);
            int ramValue = settings.RamGb == 0 ? Installer.AutoRam() : settings.RamGb;
            string pack = Game.ShaderPack();
            Row(g, 116, "Memory", "How much RAM Minecraft gets. All the way left lets me pick (" + Installer.AutoRam() + " GB on this PC).",
                settings.RamGb == 0 ? "auto" : ramValue + " GB");
            Row(g, 174, "Shaders", pack == null ? "Pick a pack in game first (Right Shift, Cinematic tab)." : playing ? "Close Minecraft to change this." : "Using " + pack.Replace(".zip", "").Replace("_", " ") + ".", null);
            Row(g, 232, "Close this app when Minecraft opens", "So it isn't sitting in your taskbar while you play.", null);
            Row(g, 290, "Updates", "The app checks every time it opens. App " + AppSetup.CurrentText + ", Lmg Client " + (Short(Installer.InstalledVersion()) ?? "not installed") + ".", null);
            Row(g, 348, "Repair", "Downloads Lmg Client and the mods again if something seems broken.", null);
            Row(g, 406, "Game folder", Paths.GameDir, null);
            Row(g, 458, "Remove the app", "Your worlds and settings stay where they are.", null, last: true);
            Gfx.TextIn(g, status, fSmall, statusColor, new RectangleF(480, 516, 412, 20));
        }

        void Row(Graphics g, float y, string title, string hint, string value, bool last = false)
        {
            Gfx.Text(g, title, fRow, C.Text, 48, y);
            Gfx.TextIn(g, hint, fSmall, C.Faint, new RectangleF(48, y + 20, 470, 32));
            if (value != null) Gfx.Text(g, value, fPixel, C.Gold, 800, y + 8);
            if (!last) using (var pen = new Pen(C.Alpha(C.Line, 0.9f))) g.DrawLine(pen, 48, y + 50, 892, y + 50);
        }

        // ---- window ------------------------------------------------------------------------------

        void RestorePosition()
        {
            if (settings.WindowX == int.MinValue) return;
            var where = new Rectangle(settings.WindowX, settings.WindowY, Width, Height);
            if (Screen.AllScreens.Any(s => s.WorkingArea.IntersectsWith(where)))
            {
                StartPosition = FormStartPosition.Manual;
                Location = where.Location;
            }
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (WindowState == FormWindowState.Normal)
            {
                settings.WindowX = Left;
                settings.WindowY = Top;
                settings.Save();
            }
            anim.Stop();
            gameWatch.Stop();
            base.OnFormClosing(e);
        }

        [DllImport("user32.dll")] static extern bool ReleaseCapture();
        [DllImport("user32.dll")] static extern IntPtr SendMessage(IntPtr hWnd, int msg, int wParam, int lParam);
        [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            try
            {
                int round = 2;   // rounded corners on Windows 11 (ignored elsewhere)
                DwmSetWindowAttribute(Handle, 33, ref round, sizeof(int));
            }
            catch { }
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button == MouseButtons.Left && e.Y < Dpi.D(TITLE))
            {
                try
                {
                    ReleaseCapture();
                    SendMessage(Handle, 0xA1, 0x2, 0);   // drag the window by its top bar
                }
                catch { }
            }
        }

        protected override CreateParams CreateParams
        {
            get
            {
                var cp = base.CreateParams;
                cp.Style |= 0x20000;      // WS_MINIMIZEBOX
                cp.ClassStyle |= 0x20000; // CS_DROPSHADOW
                return cp;
            }
        }
    }
}
