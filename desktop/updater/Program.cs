using System;
using System.IO;
using System.Diagnostics;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

// Runs outside the installed directory so Windows can release every app/runtime file first.
internal static class Program {
    static readonly JavaScriptSerializer Json = new JavaScriptSerializer();
    static string planFolder;
    static void Result(string status, string message) {
        File.WriteAllText(Path.Combine(planFolder, "result.json"), Json.Serialize(new { status = status, message = message }));
    }
    static string Full(string path) { return Path.GetFullPath(path).TrimEnd(Path.DirectorySeparatorChar); }
    static bool Same(string a, string b) { return String.Equals(Full(a), Full(b), StringComparison.OrdinalIgnoreCase); }
    static bool Bundle(string path) {
        return File.Exists(Path.Combine(path, "SeekTop.exe")) && File.Exists(Path.Combine(path, "app", "SeekTop.cfg")) && File.Exists(Path.Combine(path, "runtime", "release"));
    }
    static void MoveRetry(string from, string to) {
        Exception last = null;
        for (int i = 0; i < 240; i++) {
            try { Directory.Move(from, to); return; }
            catch (IOException e) { last = e; Thread.Sleep(250); }
            catch (UnauthorizedAccessException e) { last = e; Thread.Sleep(250); }
        }
        throw new IOException("Cannot replace the application directory. Close other SeekTop instances.", last);
    }
    static Process Launch(string directory, string ack) {
        var info = new ProcessStartInfo(Path.Combine(directory, "SeekTop.exe"));
        info.WorkingDirectory = directory;
        info.UseShellExecute = false;
        if (ack != null) info.EnvironmentVariables["SEEKTOP_UPDATE_ACK"] = ack;
        else info.EnvironmentVariables.Remove("SEEKTOP_UPDATE_ACK");
        return Process.Start(info);
    }
    [STAThread]
    static int Main(string[] args) {
        bool movedOld = false, movedNew = false;
        string target = null, stage = null, backup = null;
        bool restart = false, interactive = false;
        try {
            if (args.Length != 1) return 2;
            string plan = Path.GetFullPath(args[0]);
            planFolder = Path.GetDirectoryName(plan);
            var data = Json.Deserialize<Plan>(File.ReadAllText(plan));
            target = Full(data.target); stage = Full(data.stage); backup = Full(data.backup); restart = data.restart; interactive = data.interactive;
            string parent = Path.GetDirectoryName(target), name = Path.GetFileName(target);
            if (parent == null || data.token == null || !System.Text.RegularExpressions.Regex.IsMatch(data.token, "^[a-f0-9]{32}$")) throw new IOException("Invalid update plan");
            if (!Same(stage, Path.Combine(parent, "." + name + ".update-" + data.token)) || !Same(backup, Path.Combine(parent, "." + name + ".backup-" + data.token))) throw new IOException("Update paths are not validated siblings");
            if (!Bundle(target) || !Bundle(stage) || Directory.Exists(backup)) throw new IOException("Incomplete update bundle");
            if ((File.GetAttributes(target) & FileAttributes.ReparsePoint) != 0 || (File.GetAttributes(stage) & FileAttributes.ReparsePoint) != 0) throw new IOException("Linked application directories are unsupported");
            using (var updateLock = new FileStream(Path.Combine(parent, "." + name + ".upgrade.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None)) {
                Result("ready", "Waiting for the running application to exit");
                var limit = DateTime.UtcNow.AddSeconds(90);
                foreach (int id in data.pids) {
                    Process process;
                    try { process = Process.GetProcessById(id); } catch (ArgumentException) { continue; }
                    using (process) {
                        string executable;
                        try { executable = process.MainModule.FileName; }
                        catch (Exception) { if (process.HasExited) continue; throw; }
                        if (!Same(executable, Path.Combine(target, "SeekTop.exe")) && !Same(executable, Path.Combine(target, "app", "resources", "browser-runtime", "SeekTopBrowser.exe"))) throw new IOException("Refusing to wait on an unrelated process");
                        while (!process.WaitForExit(250)) if (DateTime.UtcNow > limit) throw new IOException("Application did not exit; update cancelled");
                    }
                }
                MoveRetry(target, backup); movedOld = true;
                MoveRetry(stage, target); movedNew = true;
                if (restart) {
                    string ack = Path.Combine(planFolder, "launch.ok");
                    using (var launched = Launch(target, ack)) {
                        var launchLimit = DateTime.UtcNow.AddSeconds(60);
                        while (!File.Exists(ack) && DateTime.UtcNow < launchLimit) {
                            if (launched.WaitForExit(250)) {
                                if (!File.Exists(ack)) throw new IOException("New application exited before confirming startup");
                            }
                        }
                        if (!File.Exists(ack)) {
                            Result("unconfirmed", "The new process is still running but startup was not confirmed. The old version is preserved at " + backup);
                            if (interactive) MessageBox.Show("新版启动尚未确认，旧版已完整保留：\n" + backup, "SeekTop 更新", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                            return 3;
                        }
                    }
                }
                Result("installed", "Update installed; previous version preserved at " + backup);
            }
            return 0;
        } catch (Exception failure) {
            string message = failure.Message;
            if (planFolder != null) File.WriteAllText(Path.Combine(planFolder, "failure.log"), failure.ToString());
            try {
                if (movedOld) {
                    if (movedNew) MoveRetry(target, stage);
                    MoveRetry(backup, target);
                    if (restart) Launch(target, null);
                    Result("rolledBack", message);
                } else if (planFolder != null) Result("failed", message);
            } catch (Exception rollback) {
                if (planFolder != null) Result("recoveryRequired", message + "\n" + rollback.Message + "\nBackup: " + backup);
            }
            if (interactive) MessageBox.Show("升级未完成，已保留旧版文件。\n" + message, "SeekTop 更新", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return 1;
        }
    }
    public class Plan {
        public string target { get; set; }
        public string stage { get; set; }
        public string backup { get; set; }
        public string token { get; set; }
        public int[] pids { get; set; }
        public bool restart { get; set; }
        public bool interactive { get; set; }
    }
}
