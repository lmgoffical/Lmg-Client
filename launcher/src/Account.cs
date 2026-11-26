using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

namespace LmgLauncher
{
    /// <summary>
    /// Who's signed into the Minecraft Launcher, so the app can say hi and show your face.
    /// Only the player name and ID are read from the launcher's account file - nothing else in it
    /// is used, kept or sent anywhere. The face comes from your skin on Mojang's own servers.
    /// </summary>
    class Account
    {
        public string Name { get; private set; }
        public string Id { get; private set; }      // 32 hex characters
        public Bitmap Skin { get; private set; }

        public static Account ReadActive()
        {
            foreach (var file in new[] { "launcher_accounts.json", "launcher_accounts_microsoft_store.json" })
            {
                string path = Path.Combine(Paths.Minecraft, file);
                if (!File.Exists(path)) continue;
                try
                {
                    var data = new JavaScriptSerializer { MaxJsonLength = 16 * 1024 * 1024 }
                        .Deserialize<Dictionary<string, object>>(File.ReadAllText(path, Encoding.UTF8));
                    if (!(data.TryGetValue("accounts", out var a) && a is Dictionary<string, object> accounts) || accounts.Count == 0) continue;
                    string active = data.TryGetValue("activeAccountLocalId", out var act) ? act as string : null;
                    var entry = (active != null && accounts.TryGetValue(active, out var e) ? e : accounts.Values.First()) as Dictionary<string, object>;
                    if (entry == null || !(entry.TryGetValue("minecraftProfile", out var mp) && mp is Dictionary<string, object> profile)) continue;
                    string name = profile.TryGetValue("name", out var n) ? n as string : null;
                    string id = profile.TryGetValue("id", out var i) ? (i as string ?? "").Replace("-", "").ToLowerInvariant() : "";
                    if (string.IsNullOrEmpty(name)) continue;
                    return new Account { Name = name, Id = id };
                }
                catch { /* unreadable or in a newer format: just don't show an account */ }
            }
            return null;
        }

        /// <summary>Loads your skin: from the cache straight away, then fresh from Mojang.</summary>
        public async Task LoadSkin()
        {
            if (Id == null || Id.Length != 32) return;
            string cache = Path.Combine(Paths.AppDir, "skin-" + Id + ".png");
            try { if (File.Exists(cache)) Skin = new Bitmap(new MemoryStream(File.ReadAllBytes(cache))); } catch { }
            try
            {
                var profile = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(
                    await Net.Text("https://sessionserver.mojang.com/session/minecraft/profile/" + Id));
                var props = profile["properties"] as System.Collections.ArrayList;
                var textures = props?.OfType<Dictionary<string, object>>().FirstOrDefault(p => (p["name"] as string) == "textures");
                if (textures == null) return;
                string json = Encoding.UTF8.GetString(Convert.FromBase64String((string)textures["value"]));
                var t = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(json);
                var skinEntry = ((Dictionary<string, object>)t["textures"])["SKIN"] as Dictionary<string, object>;
                string url = skinEntry["url"] as string;
                // only ever from Mojang's texture server
                if (url == null || !(url.StartsWith("http://textures.minecraft.net/") || url.StartsWith("https://textures.minecraft.net/"))) return;
                byte[] png = await Net.Bytes(url.Replace("http://", "https://"));
                Skin = new Bitmap(new MemoryStream(png));
                Directory.CreateDirectory(Paths.AppDir);
                File.WriteAllBytes(cache, png);
            }
            catch { /* offline: keep the cached face (or the default) */ }
        }

        static Bitmap hatChecked;
        static bool hatUsable;

        /// <summary>
        /// Minecraft ignores the hat layer of old 64x32 skins when it's completely solid
        /// (they couldn't be see-through back then). Same rule here.
        /// </summary>
        static bool HatIsUsable(Bitmap skin)
        {
            if (ReferenceEquals(skin, hatChecked)) return hatUsable;
            bool anyClear = false;
            for (int x = 32; x < 64 && !anyClear; x++)
                for (int y = 0; y < 16 && !anyClear; y++)
                    if (skin.GetPixel(x, y).A < 128) anyClear = true;
            hatChecked = skin;
            hatUsable = skin.Height >= 64 || anyClear;
            return hatUsable;
        }

        /// <summary>Draws the face (with the hat layer) at the given size, crisp like in game.</summary>
        public static void DrawFace(Graphics g, Bitmap skin, RectangleF r)
        {
            var old = g.InterpolationMode;
            var oldOffset = g.PixelOffsetMode;
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
            g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
            if (skin != null && skin.Width >= 64)
            {
                g.DrawImage(skin, r, new RectangleF(8, 8, 8, 8), GraphicsUnit.Pixel);
                // like the game: old skins with a solid hat layer have no hat
                if (HatIsUsable(skin)) g.DrawImage(skin, r, new RectangleF(40, 8, 8, 8), GraphicsUnit.Pixel);
            }
            else
            {
                // no skin yet: a plain Steve-coloured face
                using (var b = new SolidBrush(Color.FromArgb(0xB7, 0x83, 0x6B))) g.FillRectangle(b, r);
                using (var b = new SolidBrush(Color.FromArgb(0x3B, 0x2A, 0x1E))) g.FillRectangle(b, r.X, r.Y, r.Width, r.Height * 0.25f);
                float px = r.Width / 8f;
                using (var b = new SolidBrush(Color.White)) { g.FillRectangle(b, r.X + px, r.Y + px * 4, px, px); g.FillRectangle(b, r.X + px * 6, r.Y + px * 4, px, px); }
                using (var b = new SolidBrush(Color.FromArgb(0x49, 0x3C, 0xAB))) { g.FillRectangle(b, r.X + px * 2, r.Y + px * 4, px, px); g.FillRectangle(b, r.X + px * 5, r.Y + px * 4, px, px); }
            }
            g.InterpolationMode = old;
            g.PixelOffsetMode = oldOffset;
        }
    }
}
