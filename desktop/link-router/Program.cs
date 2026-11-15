using System;
using System.IO;
using System.IO.Pipes;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using Microsoft.Win32;

// Unpackaged Win32 HTTP/HTTPS dispatcher. UserChoice is read only, never overwritten.
internal static class Program {
    static readonly JavaScriptSerializer Json = new JavaScriptSerializer();
    static string Identity { get {
#if ROUTER_TEST
        string id = Environment.GetEnvironmentVariable("SEEKTOP_ROUTER_TEST_ID");
        if (String.IsNullOrEmpty(id) || !Regex.IsMatch(id, "^[a-f0-9]{32}$")) throw new Exception("Invalid test identity");
        return "SeekTop.RouterTest." + id;
#else
        return "SeekTop.LinkRouter";
#endif
    } }
    static string Folder { get {
#if ROUTER_TEST
        return Path.GetFullPath(Environment.GetEnvironmentVariable("SEEKTOP_ROUTER_TEST_DIR"));
#else
        return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "SeekTop", "links");
#endif
    } }
    static string ConfigFile { get { return Path.Combine(Folder, "routing.json"); } }
    static string ClassId(string scheme) { return Identity + "." + scheme; }
    static string RegistryBase { get { return @"Software\" + Identity; } }
    static string RegisteredApps { get {
#if ROUTER_TEST
        return RegistryBase + @"\RegisteredApplications";
#else
        return @"Software\RegisteredApplications";
#endif
    } }
    static string Sid { get { return WindowsIdentity.GetCurrent().User.Value; } }
    static string PipeName { get { return Identity + "." + Sid; } }
    internal sealed class Config {
        public bool enabled = false;
        public string http = "", https = "", httpName = "", httpsName = "", app = "";
    }
    static Config ReadConfig() {
        if (!File.Exists(ConfigFile)) return new Config();
        if (new FileInfo(ConfigFile).Length > 32768) throw new Exception("LINK_CONFIG_INVALID");
        Config c = Json.Deserialize<Config>(File.ReadAllText(ConfigFile, Encoding.UTF8));
        if (c == null) throw new Exception("LINK_CONFIG_INVALID");
        return c;
    }
    static void Save(Config c) {
        Directory.CreateDirectory(Folder);
        string temp = Path.Combine(Folder, Guid.NewGuid().ToString("N") + ".tmp");
        try {
            File.WriteAllText(temp, Json.Serialize(c), new UTF8Encoding(false));
            if (File.Exists(ConfigFile)) File.Replace(temp, ConfigFile, null); else File.Move(temp, ConfigFile);
        } finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    [DllImport("shlwapi.dll", CharSet = CharSet.Unicode)]
    static extern uint AssocQueryString(uint flags, uint str, string assoc, string extra, StringBuilder output, ref uint length);
    static string Association(string scheme, uint kind) {
#if ROUTER_TEST
        using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RegistryBase + @"\Choices")) {
            return key == null ? "" : (string)key.GetValue(scheme + (kind == 4 ? "Name" : ""), "");
        }
#else
        uint length = 4096; StringBuilder value = new StringBuilder((int)length);
        return AssocQueryString(0, kind, scheme, null, value, ref length) == 0 ? value.ToString() : "";
#endif
    }
    static bool ValidFallback(string id) {
        if (String.IsNullOrEmpty(id) || id.Length > 256 || !Regex.IsMatch(id, @"^[A-Za-z0-9_.-]+$") || id.StartsWith("SeekTop.", StringComparison.OrdinalIgnoreCase) || id == "http" || id == "https") return false;
        using (RegistryKey key = Registry.ClassesRoot.OpenSubKey(id + @"\shell\open\command")) { return key != null; }
    }
    static bool Active(string scheme) { return String.Equals(Association(scheme, 20), ClassId(scheme), StringComparison.OrdinalIgnoreCase); }
    static void Status() {
        Config c = ReadConfig();
        Console.WriteLine(Json.Serialize(new { enabled = c.enabled && File.Exists(c.app), httpActive = Active("http"), httpsActive = Active("https"), prepared = File.Exists(ConfigFile), httpName = c.httpName, httpsName = c.httpsName }));
    }
    static void Register() {
        string helper = Path.Combine(Folder, "SeekTopLinkRouter.exe");
        foreach (string scheme in new [] { "http", "https" }) {
            string id = ClassId(scheme);
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(@"Software\Classes\" + id)) {
                key.SetValue("", "SeekTop Links"); key.SetValue("URL Protocol", "");
                using (RegistryKey command = key.CreateSubKey(@"shell\open\command")) command.SetValue("", "\"" + helper + "\" \"%1\"");
                using (RegistryKey icon = key.CreateSubKey("DefaultIcon")) icon.SetValue("", helper + ",0");
            }
        }
        using (RegistryKey key = Registry.CurrentUser.CreateSubKey(RegistryBase + @"\Capabilities")) {
            key.SetValue("ApplicationName", "SeekTop Links");
            key.SetValue("ApplicationDescription", "Open NodeSeek posts in SeekTop; other URLs use your previous browser.");
            key.SetValue("ApplicationIcon", helper + ",0");
            using (RegistryKey urls = key.CreateSubKey("URLAssociations")) { urls.SetValue("http", ClassId("http")); urls.SetValue("https", ClassId("https")); }
        }
        using (RegistryKey key = Registry.CurrentUser.CreateSubKey(RegisteredApps)) key.SetValue(Identity, RegistryBase + @"\Capabilities");
        SHChangeNotify(0x08000000, 0, IntPtr.Zero, IntPtr.Zero);
    }
    [DllImport("shell32.dll")] static extern void SHChangeNotify(uint id, uint flags, IntPtr a, IntPtr b);
    static void Prepare(string app) {
        app = Path.GetFullPath(app);
        if (!File.Exists(app) || !String.Equals(Path.GetFileName(app), "SeekTop.exe", StringComparison.OrdinalIgnoreCase)) throw new Exception("LINK_APP_MISSING");
        Config c = ReadConfig();
        foreach (string scheme in new [] { "http", "https" }) {
            string current = Association(scheme, 20);
            string saved = scheme == "http" ? c.http : c.https;
            if (!Active(scheme)) saved = current;
            if (!ValidFallback(saved)) throw new Exception("LINK_BROWSER_UNAVAILABLE");
            string name = Active(scheme) ? (scheme == "http" ? c.httpName : c.httpsName) : Association(scheme, 4);
            if (scheme == "http") { c.http = saved; c.httpName = name; } else { c.https = saved; c.httpsName = name; }
        }
        Directory.CreateDirectory(Folder);
        string target = Path.Combine(Folder, "SeekTopLinkRouter.exe");
        string self = Process.GetCurrentProcess().MainModule.FileName;
        if (!String.Equals(Path.GetFullPath(self), Path.GetFullPath(target), StringComparison.OrdinalIgnoreCase)) File.Copy(self, target, true);
        c.app = app;
        // Commit the captured browsers before registering as a candidate. Never recapture ourselves.
        c.enabled = true; Save(c); Register(); Status();
    }
    static void Disable(bool retire) {
        Config c = ReadConfig(); c.enabled = false; Save(c);
        if (retire && !Active("http") && !Active("https")) {
            foreach (string scheme in new [] { "http", "https" }) Registry.CurrentUser.DeleteSubKeyTree(@"Software\Classes\" + ClassId(scheme), false);
            using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RegisteredApps, true)) if (key != null) key.DeleteValue(Identity, false);
            Registry.CurrentUser.DeleteSubKeyTree(RegistryBase + @"\Capabilities", false);
            SHChangeNotify(0x08000000, 0, IntPtr.Zero, IntPtr.Zero);
        }
        // When still the system default, keep the tiny forwarder and captured browser available.
        Status();
    }
    static Uri WebUrl(string raw) {
        Uri u;
        if (String.IsNullOrEmpty(raw) || raw.Length > 8192 || raw.IndexOfAny(new [] {'\r','\n','\0'}) >= 0 || !Uri.TryCreate(raw, UriKind.Absolute, out u) || (u.Scheme != "http" && u.Scheme != "https") || !String.IsNullOrEmpty(u.UserInfo)) throw new Exception("LINK_INVALID_URL");
        return u;
    }
    internal static bool Supported(string raw) {
        Uri u = WebUrl(raw);
        if (!u.IsDefaultPort || (u.Host != "nodeseek.com" && u.Host != "www.nodeseek.com")) return false;
        // Only routes rendered natively by SeekTop. Account management and unsupported pages stay on the web.
        return Regex.IsMatch(u.AbsolutePath, @"^/post-[1-9][0-9]{0,17}-[1-9][0-9]{0,8}/?$") || Regex.IsMatch(u.AbsolutePath, @"^/space/[1-9][0-9]{0,17}/?$");
    }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    struct ShellInfo {
        public int cbSize; public uint fMask; public IntPtr hwnd;
        public string lpVerb, lpFile, lpParameters, lpDirectory; public int nShow; public IntPtr hInstApp, lpIDList;
        public string lpClass; public IntPtr hkeyClass; public uint dwHotKey; public IntPtr hIcon, hProcess;
    }
    [DllImport("shell32.dll", CharSet = CharSet.Unicode, SetLastError = true)] static extern bool ShellExecuteEx(ref ShellInfo info);
    static void Browser(string raw, Config c) {
        Uri u = WebUrl(raw); string id = u.Scheme == "http" ? c.http : c.https;
        // If the user has since picked another real browser, respect the current selection.
        if (!Active(u.Scheme)) { string current = Association(u.Scheme, 20); if (ValidFallback(current)) id = current; }
        if (!ValidFallback(id)) throw new Exception("LINK_BROWSER_UNAVAILABLE");
        ShellInfo info = new ShellInfo { cbSize = Marshal.SizeOf(typeof(ShellInfo)), fMask = 0x1 | 0x100 | 0x400, lpVerb = "open", lpFile = u.AbsoluteUri, lpClass = id, nShow = 1 };
        // SEE_MASK_CLASSNAME selects the saved ProgID instead of invoking the default dispatcher again.
        // SEE_MASK_NOASYNC is required because this process exits immediately after launching.
        if (!ShellExecuteEx(ref info)) throw new Exception("LINK_BROWSER_FAILED");
    }
    static bool Send(string url) {
        bool connected = false;
        try {
            using (NamedPipeClientStream pipe = new NamedPipeClientStream(".", PipeName, PipeDirection.InOut)) {
                pipe.Connect(250); connected = true;
                using (StreamWriter writer = new StreamWriter(pipe, new UTF8Encoding(false), 1024, true)) {
                    writer.WriteLine(Json.Serialize(new { url = url })); writer.Flush();
                    var reading = System.Threading.Tasks.Task.Factory.StartNew(() => new StreamReader(pipe, Encoding.UTF8, false, 1024, true).ReadLine());
                    if (!reading.Wait(3000) || reading.Result != "OK") throw new Exception("LINK_DELIVERY_FAILED");
                    return true;
                }
            }
        } catch { if (connected) throw new Exception("LINK_DELIVERY_FAILED"); return false; }
    }
    static void Open(string raw) {
        WebUrl(raw); Config c = ReadConfig();
        if (!c.enabled || !Supported(raw) || !File.Exists(c.app)) { Browser(raw, c); return; }
        using (Mutex gate = new Mutex(false, "Local\\" + PipeName + ".Launch")) {
            bool owned = false;
            try {
                try { owned = gate.WaitOne(40000); } catch (AbandonedMutexException) { owned = true; }
                if (!owned) throw new Exception("LINK_START_TIMEOUT");
                if (Send(raw)) return;
                Process.Start(new ProcessStartInfo(c.app, "--open-url \"" + raw.Replace("\"", "%22") + "\"") { UseShellExecute = true, WorkingDirectory = Path.GetDirectoryName(c.app) });
                // Wait for the first instance's receiver before releasing the cold-start gate.
                for (int n = 0; n < 120; n++) { if (Send("")) return; Thread.Sleep(100); }
                throw new Exception("LINK_START_TIMEOUT");
            } finally { if (owned) gate.ReleaseMutex(); }
        }
    }
    static void Listen(int parent) {
        using (Process owner = Process.GetProcessById(parent))
        using (Mutex gate = new Mutex(false, "Local\\" + PipeName + ".Listener")) {
            bool locked;
            try { locked = gate.WaitOne(0); } catch (AbandonedMutexException) { locked = true; }
            if (!locked) return;
            Thread watchdog = new Thread(() => { try { owner.WaitForExit(); } finally { Environment.Exit(0); } }); watchdog.IsBackground = true; watchdog.Start();
            try {
                while (true) {
                    PipeSecurity acl = new PipeSecurity(); acl.SetAccessRuleProtection(true, false);
                    acl.AddAccessRule(new PipeAccessRule(WindowsIdentity.GetCurrent().User, PipeAccessRights.FullControl, AccessControlType.Allow));
                    using (NamedPipeServerStream pipe = new NamedPipeServerStream(PipeName, PipeDirection.InOut, 1, PipeTransmissionMode.Byte, PipeOptions.Asynchronous, 16384, 16384, acl)) {
                        pipe.WaitForConnection();
                        try {
                            // Bound reads and time out unresponsive local clients.
                            var reading = System.Threading.Tasks.Task.Factory.StartNew(() => {
                                StringBuilder b = new StringBuilder(); int ch;
                                while ((ch = pipe.ReadByte()) >= 0 && ch != 10) { if (b.Length >= 65536) throw new Exception("LINK_INVALID_URL"); b.Append((char)ch); }
                                return Encoding.UTF8.GetString(Array.ConvertAll(b.ToString().ToCharArray(), x => (byte)x));
                            });
                            if (!reading.Wait(3000)) continue;
                            var q = Json.Deserialize<System.Collections.Generic.Dictionary<string, string>>(reading.Result);
                            string url = q["url"];
                            if (url.Length > 0) {
                                if (!Supported(url)) throw new Exception("LINK_INVALID_URL");
                                Console.WriteLine(Json.Serialize(new { url = url })); Console.Out.Flush();
                            }
                            byte[] ack = Encoding.ASCII.GetBytes("OK\n"); pipe.Write(ack, 0, ack.Length); pipe.Flush();
                        } catch { /* Reject one bad client without terminating the receiver. */ }
                    }
                }
            } finally { gate.ReleaseMutex(); }
        }
    }
    [STAThread] static int Main(string[] args) {
        // Console code pages vary on Chinese Windows; JVM/IPC status always uses UTF-8.
        Console.SetOut(new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false)) { AutoFlush = true });
        try {
            if (args.Length == 1 && args[0] == "--status") { Status(); return 0; }
            if (args.Length == 2 && args[0] == "--prepare") { Prepare(args[1]); return 0; }
            if (args.Length == 1 && args[0] == "--disable") { Disable(false); return 0; }
            if (args.Length == 1 && args[0] == "--retire") { if (File.Exists(ConfigFile)) Disable(true); return 0; }
            if (args.Length == 1 && args[0] == "--settings") { Process.Start("ms-settings:defaultapps?registeredAppUser=" + Uri.EscapeDataString(Identity)); return 0; }
            if (args.Length == 2 && args[0] == "--listen") { Listen(Int32.Parse(args[1])); return 0; }
#if ROUTER_TEST
            if (args.Length == 2 && args[0] == "--classify") { Console.WriteLine(Supported(args[1]) ? "native" : "browser"); return 0; }
            if (args.Length == 2 && args[0] == "--browser") { Browser(args[1], ReadConfig()); return 0; }
#endif
            if (args.Length == 1 && !args[0].StartsWith("--")) { Open(args[0]); return 0; }
            throw new Exception("LINK_INVALID_ARGUMENTS");
        } catch (Exception e) {
            string code = e.Message.StartsWith("LINK_") ? e.Message : "LINK_OPERATION_FAILED";
            Console.WriteLine(Json.Serialize(new { error = code }));
#if !ROUTER_TEST
            if (args.Length == 1 && !args[0].StartsWith("--")) System.Windows.Forms.MessageBox.Show("SeekTop could not open this link. Check Settings > External links, or restore your browser in Windows Default apps.\n" + code, "SeekTop Links");
#endif
            return 1;
        }
    }
}
