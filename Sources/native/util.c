#include "nemesis.h"

#include <dirent.h>
#include <fcntl.h>
#include <signal.h>
#include <stdarg.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <sys/wait.h>
#include <time.h>

int log_msg(const char *fmt, ...) {
    char line[MAX_LINE];
    va_list ap;
    time_t t = time(NULL);
    struct tm tm;
    localtime_r(&t, &tm);
    int n = snprintf(line, sizeof(line), "%04d-%02d-%02d %02d:%02d:%02d ",
                     tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday,
                     tm.tm_hour, tm.tm_min, tm.tm_sec);
    va_start(ap, fmt);
    vsnprintf(line + n, sizeof(line) - (size_t)n, fmt, ap);
    va_end(ap);
    size_t len = strlen(line);
    if (len + 1 < sizeof(line) && line[len - 1] != '\n') {
        line[len] = '\n';
        line[len + 1] = 0;
    }
    fputs(line, stderr);
    mkdir_p(NEMESIS_TMP, 0755);
    append_file(NEMESIS_TMP "/nemesis.log", line);
    return 0;
}

int path_exists(const char *path) {
    struct stat st;
    return path && stat(path, &st) == 0;
}

int is_dir(const char *path) {
    struct stat st;
    return path && stat(path, &st) == 0 && S_ISDIR(st.st_mode);
}

int is_file(const char *path) {
    struct stat st;
    return path && stat(path, &st) == 0 && S_ISREG(st.st_mode);
}

int mkdir_p(const char *path, mode_t mode) {
    char tmp[MAX_PATH];
    size_t len;
    if (!path || !*path) return -1;
    snprintf(tmp, sizeof(tmp), "%s", path);
    len = strlen(tmp);
    if (len && tmp[len - 1] == '/') tmp[len - 1] = 0;
    for (char *p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = 0;
            mkdir(tmp, mode);
            *p = '/';
        }
    }
    if (mkdir(tmp, mode) != 0 && errno != EEXIST) return -1;
    return 0;
}

int ensure_dirs(void) {
    mkdir_p(NEMESIS_ROOT, 0755);
    mkdir_p(NEMESIS_APPS, 0755);
    mkdir_p(NEMESIS_TMP, 0755);
    mkdir_p(NEMESIS_LOGS, 0755);
    mkdir_p(PIDS_DIR, 0755);
    mkdir_p(SESSION_DIR, 0755);
    chmod(NEMESIS_TMP, 0777);
    chmod(NEMESIS_LOGS, 0777);
    chmod(SESSION_DIR, 0777);
    chmod(NEMESIS_ROOT, 0755);
    chmod(PACKAGES_DB, 0644);
    chmod(SERVICES_DB, 0644);
    chmod(NEMESIS_TMP "/nemesis.log", 0666);
    chmod(BIN_PATH, 04755);
    return 0;
}

static void purge_dalvik_dir(const char *dir) {
    DIR *d = opendir(dir);
    struct dirent *de;
    if (!d) return;
    while ((de = readdir(d))) {
        if (!strstr(de->d_name, "NEMESIS") && !strstr(de->d_name, "nemesis")) continue;
        {
            char p[MAX_PATH];
            snprintf(p, sizeof(p), "%s/%s", dir, de->d_name);
            unlink(p);
        }
    }
    closedir(d);
}

void purge_manager_dalvik(void) {
    /* Overlay-installing /system/app/NEMESIS.apk does not invalidate Dalvik cache. */
    purge_dalvik_dir("/data/dalvik-cache");
    purge_dalvik_dir("/data/dalvik-cache/arm");
    purge_dalvik_dir("/cache/dalvik-cache");
    unlink("/data/dalvik-cache/system@app@NEMESIS.apk@classes.dex");
    unlink("/cache/dalvik-cache/system@app@NEMESIS.apk@classes.dex");
}

static unsigned long mnt_flags_from_opts(const char *opts, int rw) {
    unsigned long f = MS_REMOUNT;
    if (!rw) f |= MS_RDONLY;
    if (!opts) return f;
    /* Drop nosuid on rw remount so /system/xbin/nemesis SUID can work.
     * Watchdog still serves a root socket because fs_mgr may restore nosuid. */
    if (rw == 0 && strstr(opts, "nosuid")) f |= MS_NOSUID;
    if (strstr(opts, "nodev")) f |= MS_NODEV;
    if (strstr(opts, "noexec")) f |= MS_NOEXEC;
#ifdef MS_NOATIME
    if (strstr(opts, "noatime")) f |= MS_NOATIME;
#endif
#ifdef MS_RELATIME
    if (strstr(opts, "relatime")) f |= MS_RELATIME;
#endif
#ifdef MS_NODIRATIME
    if (strstr(opts, "nodiratime")) f |= MS_NODIRATIME;
#endif
#ifdef MS_SYNCHRONOUS
    if (strstr(opts, "sync") && !strstr(opts, "async")) f |= MS_SYNCHRONOUS;
#endif
    return f;
}

int remount_system(int rw) {
    char buf[512], dev[128], mnt[128], fstype[64], opts[256];
    FILE *f;
    int got_system = 0;
    f = fopen("/proc/mounts", "r");
    if (f) {
        while (fgets(buf, sizeof(buf), f)) {
            unsigned long flags;
            if (sscanf(buf, "%127s %127s %63s %255s", dev, mnt, fstype, opts) != 4) continue;
            if (strcmp(mnt, "/system") != 0) continue;
            flags = mnt_flags_from_opts(opts, rw);
            if (mount(dev, mnt, fstype, flags, NULL) == 0) {
                if (!strcmp(mnt, "/system")) got_system = 1;
            } else {
                log_msg("mount(%s -> %s remount %s) errno=%d", dev, mnt, rw ? "rw" : "ro", errno);
            }
        }
        fclose(f);
    }
    if (rw) {
        int fd = open("/system/xbin/.nemesis-rw", O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (fd >= 0) {
            close(fd);
            unlink("/system/xbin/.nemesis-rw");
            return 0;
        }
    } else if (got_system) {
        return 0;
    }
    if (rw) {
        if (run_cmd("mount -o remount,rw /system") == 0) return 0;
        if (run_cmd("mount -o rw,remount /system") == 0) return 0;
        return 1;
    }
    return run_cmd("mount -o remount,ro /system");
}

int read_file(const char *path, char *buf, size_t cap) {
    FILE *f;
    size_t n;
    if (!buf || cap == 0) return -1;
    buf[0] = 0;
    f = fopen(path, "r");
    if (!f) return -1;
    n = fread(buf, 1, cap - 1, f);
    fclose(f);
    buf[n] = 0;
    return (int)n;
}

int write_file(const char *path, const char *data, mode_t mode) {
    int fd;
    size_t len = data ? strlen(data) : 0;
    fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, mode);
    if (fd < 0) return -1;
    if (len && write(fd, data, len) != (ssize_t)len) {
        close(fd);
        return -1;
    }
    close(fd);
    chmod(path, mode);
    return 0;
}

int append_file(const char *path, const char *data) {
    int fd;
    size_t len = data ? strlen(data) : 0;
    fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd < 0) return -1;
    if (len) write(fd, data, len);
    close(fd);
    return 0;
}

int copy_file(const char *src, const char *dst, mode_t mode) {
    char buf[8192];
    int in, out;
    ssize_t n;
    struct stat st;
    if (stat(src, &st) != 0) return -1;
    in = open(src, O_RDONLY);
    if (in < 0) return -1;
    out = open(dst, O_WRONLY | O_CREAT | O_TRUNC, mode ? mode : (st.st_mode & 0777));
    if (out < 0) {
        close(in);
        return -1;
    }
    while ((n = read(in, buf, sizeof(buf))) > 0) {
        if (write(out, buf, (size_t)n) != n) {
            close(in);
            close(out);
            return -1;
        }
    }
    close(in);
    close(out);
    if (mode) chmod(dst, mode);
    else chmod(dst, st.st_mode & 0777);
    return n < 0 ? -1 : 0;
}

static int copy_tree_inner(const char *src, const char *dst) {
    DIR *d = opendir(src);
    struct dirent *de;
    if (!d) return -1;
    mkdir_p(dst, 0755);
    while ((de = readdir(d))) {
        char sp[MAX_PATH], dp[MAX_PATH];
        struct stat st;
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, "..")) continue;
        snprintf(sp, sizeof(sp), "%s/%s", src, de->d_name);
        snprintf(dp, sizeof(dp), "%s/%s", dst, de->d_name);
        if (lstat(sp, &st) != 0) continue;
        if (S_ISDIR(st.st_mode)) {
            if (copy_tree_inner(sp, dp) != 0) {
                closedir(d);
                return -1;
            }
        } else if (S_ISREG(st.st_mode)) {
            if (copy_file(sp, dp, st.st_mode & 0777) != 0) {
                closedir(d);
                return -1;
            }
        }
    }
    closedir(d);
    return 0;
}

int copy_tree(const char *src, const char *dst) {
    struct stat st;
    if (stat(src, &st) != 0) return -1;
    if (S_ISREG(st.st_mode)) return copy_file(src, dst, st.st_mode & 0777);
    return copy_tree_inner(src, dst);
}

int rm_rf(const char *path) {
    DIR *d;
    struct dirent *de;
    struct stat st;
    if (!path || !*path || !strcmp(path, "/") || !strcmp(path, "/data")) return -1;
    if (lstat(path, &st) != 0) return 0;
    if (!S_ISDIR(st.st_mode) || S_ISLNK(st.st_mode)) {
        unlink(path);
        return 0;
    }
    d = opendir(path);
    if (!d) {
        rmdir(path);
        return 0;
    }
    while ((de = readdir(d))) {
        char child[MAX_PATH];
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, "..")) continue;
        snprintf(child, sizeof(child), "%s/%s", path, de->d_name);
        rm_rf(child);
    }
    closedir(d);
    rmdir(path);
    return 0;
}

int run_cmd(const char *cmd) {
    pid_t pid;
    int st;
    if (!cmd || !*cmd) return 0;
    pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        execl("/system/bin/sh", "sh", "-c", cmd, (char *)NULL);
        _exit(127);
    }
    if (waitpid(pid, &st, 0) < 0) return -1;
    if (WIFEXITED(st)) return WEXITSTATUS(st);
    return 1;
}

int run_script(const char *script, const char *cwd, const char *uid_name) {
    pid_t pid;
    int st;
    uid_t uid = 0;
    gid_t gid = 0;
    if (!script || !*script || !strcmp(script, "N/A")) return 0;
    if (!is_file(script)) {
        log_msg("skip missing script %s", script);
        return 0;
    }
    if (uid_name && !strcmp(uid_name, "system")) {
        uid = 1000;
        gid = 1000;
    }
    chmod(script, 0755);
    pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        if (cwd && *cwd) chdir(cwd);
        if (uid) {
            setgid(gid);
            setuid(uid);
        }
        execl("/system/bin/sh", "sh", script, (char *)NULL);
        _exit(127);
    }
    if (waitpid(pid, &st, 0) < 0) return -1;
    if (WIFEXITED(st)) return WEXITSTATUS(st);
    return 1;
}

int pid_alive(pid_t pid) {
    char path[64], buf[256];
    char *rp;
    int fd, n;
    if (pid <= 0) return 0;
    /* Zombies still succeed kill(0); /proc/pid/stat state Z/X means dead. */
    if (kill(pid, 0) != 0 && errno != EPERM) return 0;
    snprintf(path, sizeof(path), "/proc/%d/stat", (int)pid);
    fd = open(path, O_RDONLY);
    if (fd < 0) return 1;
    n = (int)read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return 1;
    buf[n] = 0;
    rp = strrchr(buf, ')');
    if (!rp || rp[1] != ' ' || !rp[2]) return 1;
    if (rp[2] == 'Z' || rp[2] == 'X') return 0;
    return 1;
}

int write_pidfile(const char *path, pid_t pid) {
    char b[32];
    snprintf(b, sizeof(b), "%d\n", (int)pid);
    return write_file(path, b, 0644);
}

pid_t read_pidfile(const char *path) {
    char b[32];
    if (read_file(path, b, sizeof(b)) < 0) return 0;
    return (pid_t)atoi(b);
}

int usb0_mounted(void) {
    char buf[4096];
    FILE *f = fopen("/proc/mounts", "r");
    if (!f)
        return (is_dir(USB0) && path_exists(USB0)) ||
               (is_dir(USB1) && path_exists(USB1)) ||
               (is_dir(USB2) && path_exists(USB2));
    while (fgets(buf, sizeof(buf), f)) {
        if (strstr(buf, " /storage/usb0 ") || strstr(buf, " /mnt/media_rw/usb0 ") ||
            strstr(buf, " /storage/usb1 ") || strstr(buf, " /mnt/media_rw/usb1 ") ||
            strstr(buf, " /storage/usb2 ") || strstr(buf, " /mnt/media_rw/usb2 ")) {
            fclose(f);
            return 1;
        }
    }
    fclose(f);
    return 0;
}

/* Leave the caller's process group so Android init oneshot services (flash_recovery,
 * qb_uid_check) cannot kill watchdog when their shell exits. Parent returns 1. */
int detach_daemon(void) {
    pid_t pid;
    int fd;
    pid = fork();
    if (pid < 0) return -1;
    if (pid > 0) return 1;
    if (setsid() < 0) _exit(1);
    signal(SIGHUP, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);
    pid = fork();
    if (pid < 0) _exit(1);
    if (pid > 0) _exit(0);
    umask(0);
    fd = open("/dev/null", O_RDWR);
    if (fd >= 0) {
        dup2(fd, 0);
        dup2(fd, 1);
        dup2(fd, 2);
        if (fd > 2) close(fd);
    }
    return 0;
}

int framework_up(void) {
    char v[96];
    v[0] = 0;
    /* libc property lookup; no fork, so USB Handler cannot stall on getprop. */
    if (__system_property_get("sys.boot_completed", v) <= 0) return 0;
    return v[0] == '1';
}

void qname_join(char *out, size_t cap, const char *id, const char *local) {
    if (local && !strncmp(local, id, strlen(id)) && local[strlen(id)] == '/') {
        snprintf(out, cap, "%s", local);
        return;
    }
    snprintf(out, cap, "%s/%s", id, local ? local : "");
}

int qname_split(const char *qname, char *id, size_t idcap, char *local, size_t lcap) {
    const char *slash = qname ? strrchr(qname, '/') : NULL;
    if (!slash || slash == qname) {
        if (id) snprintf(id, idcap, "%s", qname ? qname : "");
        if (local) snprintf(local, lcap, "%s", qname ? qname : "");
        return -1;
    }
    size_t n = (size_t)(slash - qname);
    if (id) {
        if (n >= idcap) n = idcap - 1;
        memcpy(id, qname, n);
        id[n] = 0;
    }
    if (local) snprintf(local, lcap, "%s", slash + 1);
    return 0;
}

void pid_path_for(char *out, size_t cap, const char *qname) {
    char tmp[MAX_QNAME];
    size_t i;
    snprintf(tmp, sizeof(tmp), "%s", qname ? qname : "");
    for (i = 0; tmp[i]; i++) {
        if (tmp[i] == '/') tmp[i] = '+';
    }
    snprintf(out, cap, "%s/%s", PIDS_DIR, tmp);
}

int become_root(void) {
    if (geteuid() != 0) {
        fprintf(stderr, "nemesis: not SUID root (euid=%d)\n", (int)geteuid());
        return -1;
    }
    setgid(0);
    setuid(0);
    setenv("PATH", "/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin", 1);
    setenv("LD_LIBRARY_PATH", "/vendor/lib:/system/lib", 1);
    return 0;
}
