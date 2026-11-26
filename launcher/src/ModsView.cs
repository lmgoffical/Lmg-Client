using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace LmgLauncher
{
    /// <summary>The list on the Mods tab: icon, name, version, what it does, and an on/off switch.</summary>
    class ModsView : Control
    {
        List<ModInfo> mods = new List<ModInfo>();
        int scroll, hoverRow = -1;
        bool locked;
        readonly Font fName, fSmall, fBadge;
        public event Action<string> Message;

        int RowH => Dpi.D(58);

        public ModsView(Font name, Font small, Font badge)
        {
            fName = name;
            fSmall = small;
            fBadge = badge;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
            BackColor = C.Bg;
        }

        public void Reload(bool gameRunning)
        {
            locked = gameRunning;
            mods = ModInfo.All();
            scroll = Math.Max(0, Math.Min(scroll, MaxScroll()));
            Invalidate();
        }

        public int Count => mods.Count;
        public int OffCount => mods.Count(m => !m.On);

        int MaxScroll() => Math.Max(0, mods.Count * RowH - Height);

        protected override void OnMouseWheel(MouseEventArgs e)
        {
            scroll = Math.Max(0, Math.Min(MaxScroll(), scroll - Math.Sign(e.Delta) * RowH));
            Invalidate();
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            int row = (e.Y + scroll) / RowH;
            int next = row < mods.Count ? row : -1;
            Cursor = next >= 0 && e.X > Width - Dpi.D(80) ? Cursors.Hand : Cursors.Default;
            if (next != hoverRow) { hoverRow = next; Invalidate(); }
        }

        protected override void OnMouseLeave(EventArgs e) { hoverRow = -1; Invalidate(); base.OnMouseLeave(e); }

        protected override void OnMouseClick(MouseEventArgs e)
        {
            int row = (e.Y + scroll) / RowH;
            if (row < 0 || row >= mods.Count || e.X < Width - Dpi.D(80)) return;
            if (locked) { Message?.Invoke("Close Minecraft first - mods can only be switched while it's closed."); return; }
            try
            {
                string problem = mods[row].Toggle(mods);
                Message?.Invoke(problem ?? (mods[row].Name + (mods[row].On ? " is on." : " is off.")));
            }
            catch (Exception ex)
            {
                Message?.Invoke("Couldn't switch it: " + ex.Message);
            }
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Gfx.Setup(g);
            g.Clear(C.Bg);
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAliasGridFit;
            if (mods.Count == 0)
            {
                Gfx.TextIn(g, "Nothing here yet. Press Play on Home and I'll put the Lmg mods in.", fSmall, C.Faint, new RectangleF(0, 0, Width, Height), true);
                return;
            }
            int first = scroll / RowH;
            for (int i = first; i < mods.Count && i * RowH - scroll < Height; i++)
            {
                var m = mods[i];
                float y = i * RowH - scroll;
                var row = new RectangleF(0, y, Width - Dpi.D(8), RowH);
                if (i == hoverRow) Gfx.Fill(g, RectangleF.Inflate(row, 0, -Dpi.D(3)), Dpi.D(4), C.Alpha(Color.White, 0.03f));
                using (var pen = new Pen(C.Line)) g.DrawLine(pen, row.X, row.Bottom - 1, row.Right, row.Bottom - 1);

                // icon (or the first letter)
                var icon = new RectangleF(row.X + Dpi.D(4), row.Y + (row.Height - Dpi.D(30)) / 2, Dpi.D(30), Dpi.D(30));
                if (m.Icon != null)
                {
                    g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                    var state = g.Save();
                    using (var clip = Gfx.Round(icon, Dpi.D(3))) g.SetClip(clip);
                    if (!m.On)
                    {
                        using (var attrs = new System.Drawing.Imaging.ImageAttributes())
                        {
                            attrs.SetColorMatrix(new System.Drawing.Imaging.ColorMatrix { Matrix33 = 0.35f });
                            g.DrawImage(m.Icon, Rectangle.Round(icon), 0, 0, m.Icon.Width, m.Icon.Height, GraphicsUnit.Pixel, attrs);
                        }
                    }
                    else g.DrawImage(m.Icon, icon);
                    g.Restore(state);
                }
                else
                {
                    Gfx.Fill(g, icon, Dpi.D(3), C.Alpha(C.Blue, 0.2f));
                    Gfx.TextIn(g, m.Name.Substring(0, 1).ToUpperInvariant(), fName, C.Blue, icon, true);
                }

                float tx = icon.Right + Dpi.D(12);
                Gfx.Text(g, m.Name, fName, m.On ? C.Text : C.Faint, tx, row.Y + Dpi.D(8));
                float nx = tx + g.MeasureString(m.Name, fName).Width + Dpi.D(4);
                Gfx.Text(g, m.Version, fSmall, C.Faint, nx, row.Y + Dpi.D(10));
                float bx = nx + g.MeasureString(m.Version, fSmall).Width + Dpi.D(6);
                if (m.InPack) bx = Badge(g, "LMG PACK", C.Gold, bx, row.Y + Dpi.D(12));
                if (m.IsRequired) Badge(g, "NEEDED", C.Faint, bx, row.Y + Dpi.D(12));
                string desc = string.IsNullOrWhiteSpace(m.Description) ? Path(m.File) : m.Description.Replace("\n", " ");
                Gfx.TextIn(g, desc, fSmall, C.Faint, new RectangleF(tx, row.Y + Dpi.D(30), row.Right - Dpi.D(90) - tx, Dpi.D(18)));

                // switch
                var sw = new RectangleF(row.Right - Dpi.D(46), row.Y + (row.Height - Dpi.D(18)) / 2, Dpi.D(36), Dpi.D(18));
                Color track = m.IsRequired && m.On ? C.Mix(C.Line2, C.Blue, 0.5f) : (m.On ? C.Blue : C.Line2);
                if (locked) track = C.Mix(track, C.Panel2, 0.5f);
                Gfx.Fill(g, sw, sw.Height / 2, track);
                float d = sw.Height - Dpi.D(6);
                float kx = m.On ? sw.Right - d - Dpi.D(3) : sw.X + Dpi.D(3);
                using (var b = new SolidBrush(Color.White)) g.FillEllipse(b, kx, sw.Y + Dpi.D(3), d, d);
            }
            if (MaxScroll() > 0)
            {
                float total = mods.Count * RowH, barH = Math.Max(Dpi.D(24), Height * Height / total);
                float barY = (Height - barH) * scroll / MaxScroll();
                Gfx.Fill(g, new RectangleF(Width - Dpi.D(4), barY, Dpi.D(3), barH), Dpi.D(2), C.Alpha(C.Blue, 0.6f));
            }
        }

        float Badge(Graphics g, string text, Color c, float x, float y)
        {
            float w = g.MeasureString(text, fBadge).Width;
            Gfx.Text(g, text, fBadge, c, x, y);
            return x + w + Dpi.D(6);
        }

        static string Path(string file) => System.IO.Path.GetFileName(file);
    }
}
