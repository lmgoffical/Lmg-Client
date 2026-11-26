using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace LmgLauncher
{
    /// <summary>The app window: Home (you, Play, what's new), Mods, and Settings.</summary>
    class MainForm : Form
    {
        const int W = 960, H = 600, TITLE = 56;
        enum Tab { Home, Mods, Settings }

        readonly Installer installer = new Installer();
        readonly AppSettings settings = AppSettings.Load();
        readonly Font fTitle, fHero, fH2, fName, fBody, fSmall, fBadge, fButton, fPlay, fMono;
        readonly Random rnd = new Random(7);
        readonly List<PointF> stars = new List<PointF>();
        readonly List<float> starPhase = new List<float>();
        readonly Timer twinkle = new Timer { Interval = 60 };
        readonly Timer gameWatch = new Timer { Interval = 3000 };

        Tab tab = Tab.Home;
        Account account;
        string heading = "Checking for updates...";
        string sub = "One moment.";
        string status = "";
        Color statusColor = C.Faint;
        double progress = -1;
        bool busy, playing, updateReady;
        FileInfo crash;
        float playPulse;
        List<KeyValuePair<string, string>> news = new List<KeyValuePair<string, string>>();

        // controls per tab
        readonly List<Control> homeControls = new List<Control>(), modsControls = new List<Control>(), settingsControls = new List<Control>();
        readonly Btn tabHome, tabMods, tabSettings, play, switchAccount, crashOpen, crashDismiss;
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

            fTitle = new Font("Segoe UI Semibold", 11f);
            fHero = new Font("Segoe UI Semibold", 22f);
            fH2 = new Font("Segoe UI Semibold", 15f);
            fName = new Font("Segoe UI Semibold", 11.5f);
            fBody = new Font("Segoe UI", 10.5f);
            fSmall = new Font("Segoe UI", 9f);
            fBadge = new Font("Segoe UI Semibold", 7.5f);
            fButton = new Font("Segoe UI Semibold", 9.5f);
            fPlay = new Font("Segoe UI Semibold", 17f);
            fMono = new Font("Consolas", 9f);

            for (int i = 0; i < 70; i++)
            {
                stars.Add(new PointF((float)rnd.NextDouble() * W, TITLE + (float)rnd.NextDouble() * (H - TITLE)));
                starPhase.Add((float)(rnd.NextDouble() * Math.PI * 2));
            }

            // title bar
            tabHome = Add(null, new Btn("Home", fButton, Btn.Kind.Tab), 360, 13, 76, 30, () => ShowTab(Tab.Home));
            tabMods = Add(null, new Btn("Mods", fButton, Btn.Kind.Tab), 442, 13, 76, 30, () => ShowTab(Tab.Mods));
            tabSettings = Add(null, new Btn("Settings", fButton, Btn.Kind.Tab), 524, 13, 86, 30, () => ShowTab(Tab.Settings));
            Add(null, new Btn("–", fTitle, Btn.Kind.Ghost), W - 98, 13, 40, 30, () => WindowState = FormWindowState.Minimized);
            Add(null, new Btn("✕", fTitle, Btn.Kind.Ghost), W - 54, 13, 40, 30, Close);

            // home
            switchAccount = Add(homeControls, new Btn("Switch account", fButton, Btn.Kind.Ghost), 390, 105, 120, 30, () => Installer.OpenMinecraftLauncher());
            play = Add(homeControls, new Btn("PLAY", fPlay, Btn.Kind.Primary), 40, 300, 480, 66, () => Play());
            crashOpen = Add(homeControls, new Btn("Open report", fButton, Btn.Kind.Ghost), 330, 440, 96, 28, OpenCrash);
            crashDismiss = Add(homeControls, new Btn("Dismiss", fButton, Btn.Kind.Ghost), 432, 440, 76, 28, DismissCrash);
            Add(homeControls, new Btn("Game folder", fButton, Btn.Kind.Ghost), 40, 498, 114, 32, () => OpenFolder(Paths.GameDir));
            Add(homeControls, new Btn("Mods folder", fButton, Btn.Kind.Ghost), 162, 498, 114, 32, () => OpenFolder(Paths.Mods));
            Add(homeControls, new Btn("Screenshots", fButton, Btn.Kind.Ghost), 284, 498, 114, 32, () => OpenFolder(Path.Combine(Paths.GameDir, "screenshots")));
            Add(homeControls, new Btn("Website", fButton, Btn.Kind.Ghost), 406, 498, 114, 32, () => Open("https://lmgoffical.github.io/Lmg-Client/"));

            // mods
            modsView = Add(modsControls, new ModsView(fName, fSmall, fBadge), 40, 132, 880, 392, null);
            modsView.Message += m => SetStatus(m, m.EndsWith(" is on.") || m.EndsWith(" is off.") ? C.Faint : C.Gold);
            Add(modsControls, new Btn("Open folder", fButton, Btn.Kind.Ghost), 700, 86, 104, 30, () => OpenFolder(Paths.Mods));
            Add(modsControls, new Btn("Find more", fButton, Btn.Kind.Ghost), 812, 86, 104, 30, () => Open("https://modrinth.com/mods?g=categories:fabric"));

            // settings
            int total = Installer.TotalRamGb();
            ram = Add(settingsControls, new Slider { Min = 1, Max = Math.Max(3, Math.Min(12, total - 2)) }, 60, 150, 390, 30, null);
            ram.Value = settings.RamGb == 0 ? 1 : settings.RamGb;
            ram.Changed += v =>
            {
                settings.RamGb = v <= 1 ? 0 : v;
                settings.Save();
                SetStatus("Memory changes apply the next time you press Play.", C.Faint);
            };
            shaders = Add(settingsControls, new Toggle(), 404, 226, 46, 24, null);
            shaders.Changed += on =>
            {
                try { Game.SetShaders(on); SetStatus(on ? "Shaders on for next time." : "Shaders off for next time.", C.Faint); }
                catch (Exception ex) { SetStatus("Couldn't change shaders: " + ex.Message, C.Bad); }
            };
            closeOnLaunch = Add(settingsControls, new Toggle { On = settings.CloseOnLaunch }, 404, 302, 46, 24, null);
            closeOnLaunch.Changed += on => { settings.CloseOnLaunch = on; settings.Save(); };
            Add(settingsControls, new Btn("Repair", fButton, Btn.Kind.Ghost), 350, 372, 100, 30, () => { ShowTab(Tab.Home); Play(repairOnly: true); });
            Add(settingsControls, new Btn("Check now", fButton, Btn.Kind.Ghost), 800, 142, 100, 30, async () => { ShowTab(Tab.Home); await Check(); });
            Add(settingsControls, new Btn("Website", fButton, Btn.Kind.Ghost), 510, 222, 92, 30, () => Open("https://lmgoffical.github.io/Lmg-Client/"));
            Add(settingsControls, new Btn("Privacy", fButton, Btn.Kind.Ghost), 608, 222, 92, 30, () => Open("https://lmgoffical.github.io/Lmg-Client/#/privacy"));
            Add(settingsControls, new Btn("Report a problem", fButton, Btn.Kind.Ghost), 706, 222, 130, 30, () => Open("https://github.com/lmgoffical/Lmg-Client/issues/new"));
            Add(settingsControls, new Btn("Open", fButton, Btn.Kind.Ghost), 800, 296, 100, 30, () => OpenFolder(Paths.GameDir));
            Add(settingsControls, new Btn("Remove", fButton, Btn.Kind.Danger), 800, 372, 100, 30, RemoveApp);

            ShowTab(startTab == "mods" ? Tab.Mods : startTab == "settings" ? Tab.Settings : Tab.Home);

            twinkle.Tick += (s, e) =>
            {
                if (WindowState == FormWindowState.Minimized) return;
                playPulse += 0.06f;
                Invalidate(false);
            };
            twinkle.Start();
            gameWatch.Tick += async (s, e) => await WatchGame();

            Shown += async (s, e) =>
            {
                if (justUpdated) SetStatus("The app updated itself to " + AppSetup.CurrentText + ".", C.Good);
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

        void ShowTab(Tab t)
        {
            tab = t;
            tabHome.Selected = t == Tab.Home;
            tabMods.Selected = t == Tab.Mods;
            tabSettings.Selected = t == Tab.Settings;
            foreach (var c in homeControls) c.Visible = t == Tab.Home;
            foreach (var c in modsControls) c.Visible = t == Tab.Mods;
            foreach (var c in settingsControls) c.Visible = t == Tab.Settings;
            if (t == Tab.Mods) modsView.Reload(playing);
            if (t == Tab.Settings)
            {
                shaders.On = Game.ShadersOn();
                shaders.Enabled = !playing && Game.ShaderPack() != null;
            }
            UpdateHomeControls();
            status = "";
            foreach (Control c in Controls) c.Invalidate();
            Invalidate();
        }

        void UpdateHomeControls()
        {
            bool home = tab == Tab.Home;
            bool showCrash = home && crash != null;
            crashOpen.Visible = crashDismiss.Visible = showCrash;
            switchAccount.Visible = home;
            switchAccount.Label = account == null ? "Open launcher" : "Switch account";
            play.Enabled = !busy && !playing;
            play.Label = playing ? "PLAYING" : busy ? "WORKING..." : updateReady ? "UPDATE & PLAY" : "PLAY";
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
                if (have == null) { heading = "Let's get you set up."; sub = "Press Play and I'll install everything. It takes about a minute."; }
                else if (!updateReady) { heading = "Ready when you are."; sub = "You're on the newest Lmg Client (" + latest + ")."; }
                else { heading = "There's an update."; sub = "Lmg Client " + latest + " is out (you have " + have + "). Play gets it first."; }
                SetStatus("", C.Faint);
            }
            catch (Exception ex)
            {
                bool have = Installer.InstalledVersion() != null;
                heading = have ? "Ready when you are." : "Can't reach the update server.";
                sub = have ? "Couldn't check for updates, but you can still play." : "Check your internet, then try again.";
                SetStatus("Couldn't check for updates: " + Friendly(ex), C.Bad);
            }
            UpdateHomeControls();
            Invalidate();
        }

        async void Play(bool repairOnly = false)
        {
            if (busy || playing) return;
            busy = true;
            UpdateHomeControls();
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
                    heading = "All fixed.";
                    sub = "Everything was downloaded fresh.";
                    SetStatus("Done.", C.Good);
                    return;
                }
                Installer.SelectProfile();
                heading = "Opening Minecraft...";
                sub = "\"Lmg Client\" is picked in the Minecraft Launcher - just press Play there.";
                if (Installer.MinecraftLauncherRunning())
                {
                    SetStatus("The Minecraft Launcher is already open: pick \"Lmg Client\" there and press Play.", C.Gold);
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
                if (!IsDisposed) { UpdateHomeControls(); Invalidate(); }
            }
        }

        async Task WatchGame()
        {
            bool now = await Task.Run(() => Game.Running());
            if (now == playing) return;
            playing = now;
            if (playing) { heading = "Have fun!"; sub = "Lmg Client is running. Mods and shaders can be changed once you close the game."; }
            else
            {
                RefreshCrash();
                await Check();
            }
            UpdateHomeControls();
            if (tab != Tab.Home) ShowTab(tab);
            Invalidate();
        }

        void RefreshCrash()
        {
            var latest = Game.LatestCrash();
            crash = latest != null && latest.Name != settings.CrashSeen && latest.LastWriteTimeUtc > DateTime.UtcNow.AddDays(-3) ? latest : null;
            UpdateHomeControls();
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
            UpdateHomeControls();
            Invalidate();
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

        // ---- keyboard ---------------------------------------------------------------------------

        protected override void OnKeyDown(KeyEventArgs e)
        {
            if (e.KeyCode == Keys.Enter && tab == Tab.Home) { Play(); e.Handled = true; }
            else if (e.KeyCode == Keys.Escape && tab != Tab.Home) { ShowTab(Tab.Home); e.Handled = true; }
            else if (e.Control && e.KeyCode == Keys.D1) ShowTab(Tab.Home);
            else if (e.Control && e.KeyCode == Keys.D2) ShowTab(Tab.Mods);
            else if (e.Control && e.KeyCode == Keys.D3) ShowTab(Tab.Settings);
            base.OnKeyDown(e);
        }

        // ---- drawing ----------------------------------------------------------------------------

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Gfx.Setup(g);
            g.ScaleTransform(Dpi.Scale, Dpi.Scale);

            using (var brush = new LinearGradientBrush(new Rectangle(0, 0, W, H), C.Panel, C.Bg, 60f)) g.FillRectangle(brush, 0, 0, W, H);
            // twinkling stars
            for (int i = 0; i < stars.Count; i++)
            {
                float a = 0.12f + 0.25f * (0.5f + 0.5f * (float)Math.Sin(playPulse * 0.6f + starPhase[i]));
                float s = i % 7 == 0 ? 2f : 1.2f;
                using (var b = new SolidBrush(C.Alpha(Color.White, a))) g.FillEllipse(b, stars[i].X, stars[i].Y, s, s);
            }
            using (var pen = new Pen(C.Line)) g.DrawRectangle(pen, 0, 0, W - 1, H - 1);

            // title bar
            Gfx.Star(g, 28, TITLE / 2f, 10f, C.Blue, 2f);
            Gfx.Text(g, "Lmg Client", fTitle, C.Text, 46, 16);
            Gfx.Text(g, "app " + AppSetup.CurrentText, fSmall, C.Gold, 46 + g.MeasureString("Lmg Client", fTitle).Width + 6, 19);
            using (var pen = new Pen(C.Line)) g.DrawLine(pen, 0, TITLE, W, TITLE);

            switch (tab)
            {
                case Tab.Home: DrawHome(g); break;
                case Tab.Mods: DrawMods(g); break;
                default: DrawSettings(g); break;
            }

            Gfx.Text(g, "Not made by Mojang or Microsoft. You sign in with the normal Minecraft Launcher - this app never sees your password.", fSmall, C.Faint, 40, H - 28);
        }

        void DrawHome(Graphics g)
        {
            // you
            var card = new RectangleF(40, 80, 480, 84);
            Gfx.Fill(g, card, 12, C.Alpha(C.Panel2, 0.92f));
            Gfx.Outline(g, card, 12, C.Line);
            var face = new RectangleF(56, 94, 56, 56);
            Gfx.Fill(g, RectangleF.Inflate(face, 3, 3), 9, C.Line2);
            var state = g.Save();
            using (var clip = Gfx.Round(face, 7)) g.SetClip(clip);
            Account.DrawFace(g, account?.Skin, face);
            g.Restore(state);
            if (account != null)
            {
                Gfx.Text(g, account.Name, fH2, C.Text, 126, 94);
                Gfx.Text(g, "Signed in through the Minecraft Launcher", fSmall, C.Faint, 128, 126);
                using (var b = new SolidBrush(playing ? C.Good : C.Alpha(C.Good, 0.85f))) g.FillEllipse(b, 104, 140, 12, 12);
                using (var p = new Pen(C.Panel2, 2)) g.DrawEllipse(p, 104, 140, 12, 12);
            }
            else
            {
                Gfx.Text(g, "Not signed in", fH2, C.Text, 126, 94);
                Gfx.Text(g, "Open the Minecraft Launcher and sign in first.", fSmall, C.Faint, 128, 126);
            }

            // what's going on
            Gfx.TextIn(g, heading, fHero, C.Text, new RectangleF(38, 186, 480, 46));
            Gfx.TextIn(g, sub, fBody, C.Muted, new RectangleF(40, 234, 470, 50));

            // Play glow (breathes while ready)
            if (play.Enabled)
            {
                float k = 0.55f + 0.45f * (float)Math.Sin(playPulse);
                Gfx.Glow(g, new RectangleF(40, 300, 480, 66), 10, C.Blue, 0.6f + 0.4f * k);
            }

            if (progress >= 0)
            {
                Gfx.Fill(g, new RectangleF(40, 380, 480, 5), 2.5f, C.Line);
                Gfx.Fill(g, new RectangleF(40, 380, (float)(480 * Math.Max(0.02, Math.Min(1, progress))), 5), 2.5f, C.Blue);
            }
            Gfx.TextIn(g, status, fSmall, statusColor, new RectangleF(40, 392, 480, 34));

            if (crash != null)
            {
                var banner = new RectangleF(40, 432, 480, 44);
                Gfx.Fill(g, banner, 8, Color.FromArgb(0x2A, 0x12, 0x1A));
                Gfx.Outline(g, banner, 8, C.Alpha(C.Bad, 0.5f));
                Gfx.Text(g, "Minecraft crashed", fName, C.Bad, 54, 436);
                Gfx.Text(g, crash.LastWriteTime.ToString("dddd h:mm tt"), fSmall, C.Faint, 55, 456);
            }

            // what's new
            var right = new RectangleF(560, 80, 360, 452);
            Gfx.Fill(g, right, 12, C.Alpha(C.Panel, 0.94f));
            Gfx.Outline(g, right, 12, C.Line);
            Gfx.Text(g, "What's new", fName, C.Text, right.X + 20, right.Y + 16);
            float y = right.Y + 50;
            if (news.Count == 0) Gfx.Text(g, "Loading...", fSmall, C.Faint, right.X + 20, y);
            foreach (var n in news)
            {
                var size = g.MeasureString(n.Value, fSmall, (int)(right.Width - 90));
                if (y + size.Height > right.Bottom - 14) break;
                Gfx.Fill(g, new RectangleF(right.X + 18, y, 42, 18), 4, C.Alpha(C.Gold, 0.13f));
                Gfx.Text(g, "v" + n.Key, fMono, C.Gold, right.X + 21, y + 1);
                using (var b = new SolidBrush(C.Muted)) g.DrawString(n.Value, fSmall, b, new RectangleF(right.X + 70, y, right.Width - 90, size.Height + 2));
                y += Math.Max(22, size.Height) + 12;
            }
        }

        void DrawMods(Graphics g)
        {
            Gfx.Text(g, "Mods", fHero, C.Text, 38, 76);
            string count = modsView.Count + " mods" + (modsView.OffCount > 0 ? "  ·  " + modsView.OffCount + " off" : "") + "  ·  changes apply the next time you play";
            Gfx.Text(g, count, fSmall, C.Faint, 40, 114);
            var box = new RectangleF(36, 128, 888, 400);
            Gfx.Fill(g, box, 12, C.Panel);
            Gfx.Outline(g, box, 12, C.Line);
            if (playing) Gfx.Text(g, "Minecraft is running - close it to switch mods.", fSmall, C.Gold, 40, 538);
            else Gfx.TextIn(g, status, fSmall, statusColor, new RectangleF(40, 536, 880, 20));
        }

        void DrawSettings(Graphics g)
        {
            // left card: the game
            var left = new RectangleF(40, 80, 430, 452);
            Gfx.Fill(g, left, 12, C.Alpha(C.Panel, 0.94f));
            Gfx.Outline(g, left, 12, C.Line);
            Gfx.Text(g, "Game", fName, C.Text, 60, 96);
            int ramValue = settings.RamGb == 0 ? Installer.AutoRam() : settings.RamGb;
            Gfx.Text(g, "Memory", fBody, C.Text, 60, 124);
            string ramText = settings.RamGb == 0 ? "Auto (" + ramValue + " GB)" : ramValue + " GB";
            Gfx.Text(g, ramText, fButton, C.Gold, 450 - g.MeasureString(ramText, fButton).Width, 126);
            Gfx.Text(g, "How much RAM Minecraft can use. Far left is automatic.", fSmall, C.Faint, 60, 184);

            Gfx.Text(g, "Shaders", fBody, C.Text, 60, 220);
            string pack = Game.ShaderPack();
            Gfx.Text(g, pack == null ? "Pick a pack in game first (Cinematic tab)." : (playing ? "Close the game to change this." : pack.Replace(".zip", "")),
                fSmall, C.Faint, 60, 242);
            Gfx.Text(g, "Close when Minecraft opens", fBody, C.Text, 60, 296);
            Gfx.Text(g, "Gets out of your way after you press Play.", fSmall, C.Faint, 60, 318);
            Gfx.Text(g, "Repair", fBody, C.Text, 60, 366);
            Gfx.Text(g, "Downloads Lmg Client and the mods again.", fSmall, C.Faint, 60, 388);
            Gfx.TextIn(g, status, fSmall, statusColor, new RectangleF(60, 440, 390, 60));

            // right card: the app
            var right = new RectangleF(490, 80, 430, 452);
            Gfx.Fill(g, right, 12, C.Alpha(C.Panel, 0.94f));
            Gfx.Outline(g, right, 12, C.Line);
            Gfx.Text(g, "App", fName, C.Text, 510, 96);
            Gfx.Text(g, "Lmg Client app " + AppSetup.CurrentText, fBody, C.Text, 510, 140);
            Gfx.Text(g, "Updates itself every time it opens.", fSmall, C.Faint, 510, 162);
            Gfx.Text(g, "Game folder", fBody, C.Text, 510, 290);
            Gfx.TextIn(g, Paths.GameDir, fSmall, C.Faint, new RectangleF(510, 312, 280, 18));
            Gfx.Text(g, "Remove the app", fBody, C.Text, 510, 366);
            Gfx.Text(g, "Your worlds and settings stay.", fSmall, C.Faint, 510, 388);
            Gfx.Text(g, "Shortcuts: Enter = Play, Esc = Home, Ctrl+1/2/3 = tabs.", fSmall, C.Faint, 510, 500);
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
            twinkle.Stop();
            gameWatch.Stop();
            base.OnFormClosing(e);
        }

        [DllImport("user32.dll")] static extern bool ReleaseCapture();
        [DllImport("user32.dll")] static extern IntPtr SendMessage(IntPtr hWnd, int msg, int wParam, int lParam);
        [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            // rounded corners on Windows 11 (ignored elsewhere)
            try
            {
                int round = 2;
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
                cp.Style |= 0x20000;      // WS_MINIMIZEBOX: taskbar button minimises it
                cp.ClassStyle |= 0x20000; // CS_DROPSHADOW
                return cp;
            }
        }
    }
}
