using System;
using System.Linq;
using System.Threading;
using System.Windows.Forms;

namespace LmgLauncher
{
    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            AppSetup.CleanUp();
            if (!args.Contains("--portable") && AppSetup.EnsureInstalled()) return;

            // only one window at a time
            using (var mutex = new Mutex(true, "LmgClientApp", out bool first))
            {
                if (!first && !args.Contains("--updated")) return;
                Application.Run(new MainForm(args.Contains("--updated")));
            }
        }
    }
}
