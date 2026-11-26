using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Linq;
using System.Windows.Forms;

namespace LmgLauncher
{
    /// <summary>The website's colours.</summary>
    static class C
    {
        public static readonly Color Bg = Color.FromArgb(0x07, 0x0B, 0x14);
        public static readonly Color Panel = Color.FromArgb(0x0C, 0x13, 0x22);
        public static readonly Color Panel2 = Color.FromArgb(0x11, 0x1A, 0x2E);
        public static readonly Color Hover = Color.FromArgb(0x16, 0x21, 0x3A);
        public static readonly Color Line = Color.FromArgb(0x1C, 0x29, 0x43);
        public static readonly Color Line2 = Color.FromArgb(0x2A, 0x3B, 0x5E);
        public static readonly Color Text = Color.FromArgb(0xE8, 0xED, 0xF5);
        public static readonly Color Muted = Color.FromArgb(0xA3, 0xB0, 0xC4);
        public static readonly Color Faint = Color.FromArgb(0x7C, 0x8B, 0xA3);
        public static readonly Color Blue = Color.FromArgb(0x3D, 0x9B, 0xFF);
        public static readonly Color BlueLight = Color.FromArgb(0x6F, 0xB6, 0xFF);
        public static readonly Color Gold = Color.FromArgb(0xF2, 0xC1, 0x4E);
        public static readonly Color Bad = Color.FromArgb(0xFF, 0x6B, 0x78);
        public static readonly Color Good = Color.FromArgb(0x4F, 0xD4, 0x9A);

        public static Color Mix(Color a, Color b, float t)
        {
            t = Math.Max(0, Math.Min(1, t));
            return Color.FromArgb((int)(a.A + (b.A - a.A) * t), (int)(a.R + (b.R - a.R) * t), (int)(a.G + (b.G - a.G) * t), (int)(a.B + (b.B - a.B) * t));
        }

        public static Color Alpha(Color c, float a) => Color.FromArgb((int)(Math.Max(0, Math.Min(1, a)) * 255), c);
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

        public static int D(float v) => (int)Math.Round(v * Scale);
    }

    /// <summary>Shared drawing bits.</summary>
    static class Gfx
    {
        public static void Setup(Graphics g)
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        }

        public static GraphicsPath Round(RectangleF r, float radius)
        {
            var p = new GraphicsPath();
            radius = Math.Max(0.5f, Math.Min(radius, Math.Min(r.Width, r.Height) / 2f));
            float d = radius * 2;
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        public static void Fill(Graphics g, RectangleF r, float radius, Color c)
        {
            using (var path = Round(r, radius)) using (var b = new SolidBrush(c)) g.FillPath(b, path);
        }

        public static void Outline(Graphics g, RectangleF r, float radius, Color c, float width = 1f)
        {
            using (var path = Round(r, radius)) using (var p = new Pen(c, width)) g.DrawPath(p, path);
        }

        public static void Text(Graphics g, string text, Font f, Color c, float x, float y)
        {
            using (var b = new SolidBrush(c)) g.DrawString(text, f, b, x, y);
        }

        public static void TextIn(Graphics g, string text, Font f, Color c, RectangleF r, bool center = false)
        {
            using (var b = new SolidBrush(c))
            using (var fmt = new StringFormat { Trimming = StringTrimming.EllipsisWord })
            {
                if (center) { fmt.Alignment = StringAlignment.Center; fmt.LineAlignment = StringAlignment.Center; }
                g.DrawString(text, f, b, r, fmt);
            }
        }

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

        /// <summary>A soft glow under something (stacked translucent rounded rects).</summary>
        public static void Glow(Graphics g, RectangleF r, float radius, Color c, float strength)
        {
            for (int i = 6; i >= 1; i--)
            {
                var rr = RectangleF.Inflate(r, i * 2.2f, i * 2.2f);
                Fill(g, rr, radius + i * 2.2f, Alpha(c, strength * 0.05f));
            }
        }

        static Color Alpha(Color c, float a) => C.Alpha(c, a);
    }

    /// <summary>Base for our widgets: hover fade and transparent background.</summary>
    abstract class Widget : Control
    {
        protected float hover;
        readonly Timer timer = new Timer { Interval = 15 };
        protected bool over;

        protected Widget()
        {
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

        protected override void Dispose(bool disposing)
        {
            if (disposing) timer.Dispose();
            base.Dispose(disposing);
        }
    }

    /// <summary>
    /// Buttons. Most are plain text links (like the website); Play is the one solid button.
    /// Text is drawn with GDI+ so the app's own fonts work.
    /// </summary>
    class Btn : Widget
    {
        public enum Kind { Primary, Link, Danger, Nav, Window }

        readonly Kind kind;
        string label;
        public bool Selected { get; set; }
        public string Label { get => label; set { label = value; Invalidate(); } }
        /// <summary>Small pixel-font text on the right of the Play button (the version).</summary>
        public string Tag2 { get; set; }
        public Font TagFont { get; set; }

        public Btn(string label, Font font, Kind kind)
        {
            this.label = label;
            this.kind = kind;
            Font = font;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Gfx.Setup(g);
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            var r = new RectangleF(0, 0, Width, Height);
            using (var fmt = new StringFormat { LineAlignment = StringAlignment.Center, Trimming = StringTrimming.EllipsisCharacter, FormatFlags = StringFormatFlags.NoWrap })
            {
                switch (kind)
                {
                    case Kind.Primary:
                    {
                        Color fill = Enabled ? C.Mix(C.Blue, C.BlueLight, hover * 0.6f) : C.Line2;
                        Gfx.Fill(g, r, Dpi.D(4), fill);
                        // a 2px darker lip at the bottom, like a key you can press
                        if (Enabled) Gfx.Fill(g, new RectangleF(0, Height - Dpi.D(3), Width, Dpi.D(3)), Dpi.D(2), C.Mix(fill, Color.Black, 0.25f));
                        fmt.Alignment = StringAlignment.Near;
                        using (var b = new SolidBrush(Enabled ? Color.White : C.Faint))
                            g.DrawString(label, Font, b, new RectangleF(Dpi.D(20), 0, Width - Dpi.D(40), Height - Dpi.D(2)), fmt);
                        if (!string.IsNullOrEmpty(Tag2) && TagFont != null)
                        {
                            fmt.Alignment = StringAlignment.Far;
                            using (var b = new SolidBrush(Enabled ? C.Alpha(Color.White, 0.7f) : C.Faint))
                                g.DrawString(Tag2, TagFont, b, new RectangleF(0, 0, Width - Dpi.D(18), Height - Dpi.D(2)), fmt);
                        }
                        break;
                    }
                    case Kind.Nav:
                    {
                        fmt.Alignment = StringAlignment.Center;
                        Color c = Selected ? C.Text : C.Mix(C.Faint, C.Text, hover);
                        using (var b = new SolidBrush(c)) g.DrawString(label, Font, b, new RectangleF(0, 0, Width, Height - Dpi.D(4)), fmt);
                        if (Selected) using (var b = new SolidBrush(C.Blue)) g.FillRectangle(b, Width * 0.2f, Height - Dpi.D(3), Width * 0.6f, Dpi.D(2));
                        break;
                    }
                    case Kind.Window:
                    {
                        if (hover > 0.01f) Gfx.Fill(g, r, Dpi.D(4), C.Alpha(Color.White, 0.06f * hover));
                        fmt.Alignment = StringAlignment.Center;
                        using (var b = new SolidBrush(C.Mix(C.Faint, C.Text, hover))) g.DrawString(label, Font, b, r, fmt);
                        break;
                    }
                    default:
                    {
                        // a text link: muted, blue (or red) and underlined on hover
                        Color baseColor = kind == Kind.Danger ? C.Mix(C.Bad, C.Faint, 0.35f) : C.Muted;
                        Color hot = kind == Kind.Danger ? C.Bad : C.BlueLight;
                        Color c = Enabled ? C.Mix(baseColor, hot, hover) : C.Line2;
                        fmt.Alignment = StringAlignment.Near;
                        using (var b = new SolidBrush(c)) g.DrawString(label, Font, b, new RectangleF(0, 0, Width, Height), fmt);
                        if (hover > 0.05f && Enabled)
                        {
                            float tw = g.MeasureString(label, Font).Width - Dpi.D(4);
                            using (var p = new Pen(C.Alpha(c, hover), 1)) g.DrawLine(p, Dpi.D(2), Height / 2f + Font.Height / 2f, Dpi.D(2) + tw, Height / 2f + Font.Height / 2f);
                        }
                        break;
                    }
                }
            }
        }

        /// <summary>Makes a link exactly as wide as its text.</summary>
        public void FitWidth()
        {
            using (var g = CreateGraphics()) Width = (int)Math.Ceiling(g.MeasureString(label, Font).Width) + Dpi.D(4);
        }
    }

    /// <summary>An on/off switch.</summary>
    class Toggle : Widget
    {
        bool on;
        float knob = -1;
        readonly Timer anim = new Timer { Interval = 15 };
        public event Action<bool> Changed;

        public bool On
        {
            get => on;
            set { on = value; anim.Start(); Invalidate(); }
        }

        public Toggle()
        {
            anim.Tick += (s, e) =>
            {
                float target = on ? 1f : 0f;
                if (knob < 0) knob = target;
                knob += (target - knob) * 0.3f;
                if (Math.Abs(target - knob) < 0.01f) { knob = target; anim.Stop(); }
                Invalidate();
            };
            Click += (s, e) =>
            {
                if (!Enabled) return;
                On = !on;
                Changed?.Invoke(on);
            };
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Gfx.Setup(g);
            if (knob < 0) knob = on ? 1 : 0;
            var r = new RectangleF(0.5f, 0.5f, Width - 1.5f, Height - 1.5f);
            Color track = Enabled ? C.Mix(C.Line2, C.Blue, knob) : C.Line;
            r = new RectangleF(r.X, r.Y + r.Height * 0.12f, r.Width, r.Height * 0.76f);
            Gfx.Fill(g, r, r.Height / 2, C.Mix(track, Color.White, hover * 0.08f));
            float d = r.Height - Dpi.D(6);
            float x = r.X + Dpi.D(3) + (r.Width - d - Dpi.D(6)) * knob;
            using (var b = new SolidBrush(Enabled ? Color.White : C.Faint)) g.FillEllipse(b, x, r.Y + Dpi.D(3), d, d);
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing) anim.Dispose();
            base.Dispose(disposing);
        }
    }

    /// <summary>A slider with whole-number steps.</summary>
    class Slider : Widget
    {
        public int Min { get; set; } = 0;
        public int Max { get; set; } = 10;
        int value;
        bool dragging;
        public event Action<int> Changed;

        public int Value
        {
            get => value;
            set { this.value = Math.Max(Min, Math.Min(Max, value)); Invalidate(); }
        }

        float Fraction => Max == Min ? 0 : (value - Min) / (float)(Max - Min);

        protected override void OnMouseDown(MouseEventArgs e) { dragging = true; SetFrom(e.X); base.OnMouseDown(e); }
        protected override void OnMouseMove(MouseEventArgs e) { if (dragging) SetFrom(e.X); base.OnMouseMove(e); }
        protected override void OnMouseUp(MouseEventArgs e) { dragging = false; base.OnMouseUp(e); }

        protected override void OnMouseWheel(MouseEventArgs e)
        {
            int old = value;
            Value = value + Math.Sign(e.Delta);
            if (old != value) Changed?.Invoke(value);
        }

        void SetFrom(int x)
        {
            float pad = Height / 2f;
            float t = (x - pad) / Math.Max(1f, Width - pad * 2);
            int v = Min + (int)Math.Round(Math.Max(0, Math.Min(1, t)) * (Max - Min));
            if (v != value)
            {
                Value = v;
                Changed?.Invoke(value);
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Gfx.Setup(g);
            float pad = Height / 2f, cy = Height / 2f, th = Dpi.D(3);
            var track = new RectangleF(pad, cy - th / 2, Width - pad * 2, th);
            Gfx.Fill(g, track, th / 2, C.Line2);
            var fill = new RectangleF(pad, cy - th / 2, Math.Max(th, (Width - pad * 2) * Fraction), th);
            Gfx.Fill(g, fill, th / 2, C.Blue);
            for (int i = Min; i <= Max; i++)
            {
                float tx = pad + (Width - pad * 2) * (Max == Min ? 0 : (i - Min) / (float)(Max - Min));
                using (var b = new SolidBrush(C.Alpha(C.Text, i <= value ? 0.5f : 0.25f))) g.FillEllipse(b, tx - 1.5f, cy + th, 3, 3);
            }
            float kx = pad + (Width - pad * 2) * Fraction;
            float kr = Dpi.D(6) + hover * Dpi.D(1f);
            // a square knob, like a block
            Gfx.Fill(g, new RectangleF(kx - kr, cy - kr, kr * 2, kr * 2), Dpi.D(2), Color.White);
        }
    }
}
