using System;
using System.Drawing;

namespace LmgLauncher
{
    /// <summary>
    /// A little night landscape in blocks along the bottom of the window: two rows of hills,
    /// a couple of oak trees and a few stars. Drawn once into a picture, then reused.
    /// </summary>
    static class Scenery
    {
        public const int Block = 8;
        static Bitmap cached;

        public static Bitmap Get(int width, int height)
        {
            if (cached != null && cached.Width == width && cached.Height == height) return cached;
            cached?.Dispose();
            cached = Draw(width, height);
            return cached;
        }

        static Bitmap Draw(int width, int height)
        {
            var bmp = new Bitmap(width, height);
            var rnd = new Random(26);
            int cols = width / Block + 1, rows = height / Block;
            using (var g = Graphics.FromImage(bmp))
            {
                g.Clear(Color.Transparent);
                // a few stars (not twinkling - just there)
                using (var b = new SolidBrush(Color.FromArgb(70, 0xE8, 0xED, 0xF5)))
                    for (int i = 0; i < 14; i++) g.FillRectangle(b, rnd.Next(width), rnd.Next(height / 3), 2, 2);
                // the moon, a square one
                using (var b = new SolidBrush(Color.FromArgb(60, 0xF2, 0xC1, 0x4E))) g.FillRectangle(b, width - 150, 18, 16, 16);
                using (var b = new SolidBrush(Color.FromArgb(25, 0xF2, 0xC1, 0x4E))) g.FillRectangle(b, width - 154, 14, 24, 24);

                int[] back = Heights(cols, rows, rnd, 4, 9);
                int[] front = Heights(cols, rows, rnd, 2, 6);
                Layer(g, back, rows, Color.FromArgb(0x0B, 0x12, 0x21), Color.FromArgb(0x10, 0x1A, 0x2E));
                Layer(g, front, rows, Color.FromArgb(0x0E, 0x17, 0x29), Color.FromArgb(0x16, 0x27, 0x30));
                // two oak trees on the front hills
                foreach (int c in new[] { cols / 5, cols * 3 / 4 })
                {
                    int top = rows - front[c];
                    var trunk = Color.FromArgb(0x1C, 0x17, 0x1A);
                    var leaves = Color.FromArgb(0x12, 0x24, 0x22);
                    using (var b = new SolidBrush(trunk)) g.FillRectangle(b, c * Block, (top - 3) * Block, Block, 3 * Block);
                    using (var b = new SolidBrush(leaves))
                    {
                        g.FillRectangle(b, (c - 2) * Block, (top - 6) * Block, 5 * Block, 2 * Block);
                        g.FillRectangle(b, (c - 1) * Block, (top - 7) * Block, 3 * Block, Block);
                    }
                }
            }
            return bmp;
        }

        static int[] Heights(int cols, int rows, Random rnd, int min, int max)
        {
            var h = new int[cols];
            double y = (min + max) / 2.0;
            for (int i = 0; i < cols; i++)
            {
                y += rnd.Next(-1, 2) * (rnd.NextDouble() < 0.45 ? 1 : 0);
                y = Math.Max(min, Math.Min(max, y));
                h[i] = (int)y;
            }
            return h;
        }

        static void Layer(Graphics g, int[] heights, int rows, Color fill, Color top)
        {
            using (var b = new SolidBrush(fill))
            using (var t = new SolidBrush(top))
            {
                for (int i = 0; i < heights.Length; i++)
                {
                    int y = (rows - heights[i]) * Block;
                    g.FillRectangle(b, i * Block, y, Block, heights[i] * Block);
                    g.FillRectangle(t, i * Block, y, Block, 2);   // a thin "grass" edge
                }
            }
        }
    }
}
