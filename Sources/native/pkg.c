#include "nemesis.h"

#include <dirent.h>

static int pkg_id_ok(const char *id) {
    size_t i, n;
    if (!id || !*id) return 0;
    n = strlen(id);
    if (n > 64) return 0;
    if (id[0] == '.') return 0;
    if (strstr(id, "..")) return 0;
    for (i = 0; i < n; i++) {
        char c = id[i];
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-')
            continue;
        return 0;
    }
    return 1;
}

static int parse_pkg_line(const char *line, pkg_rec *p) {
    char buf[MAX_LINE];
    char *a, *b, *c, *d;
    snprintf(buf, sizeof(buf), "%s", line);
    a = buf;
    b = strchr(a, '|');
    if (!b) return -1;
    *b++ = 0;
    c = strchr(b, '|');
    if (!c) return -1;
    *c++ = 0;
    d = strchr(c, '|');
    if (!d) return -1;
    *d++ = 0;
    memset(p, 0, sizeof(*p));
    snprintf(p->id, sizeof(p->id), "%s", a);
    snprintf(p->name, sizeof(p->name), "%s", b);
    p->locked = atoi(c);
    snprintf(p->types, sizeof(p->types), "%s", d);
    return p->id[0] ? 0 : -1;
}

int pkg_list_load(pkg_rec **out, int *n) {
    FILE *f;
    char line[MAX_LINE];
    pkg_rec *list = NULL;
    int cap = 0, cnt = 0;
    *out = NULL;
    *n = 0;
    f = fopen(PACKAGES_DB, "r");
    if (!f) return 0;
    while (fgets(line, sizeof(line), f)) {
        pkg_rec p;
        size_t L = strlen(line);
        while (L && (line[L - 1] == '\n' || line[L - 1] == '\r')) line[--L] = 0;
        if (!line[0] || line[0] == '#') continue;
        if (parse_pkg_line(line, &p) != 0) continue;
        if (cnt >= cap) {
            cap = cap ? cap * 2 : 8;
            list = realloc(list, (size_t)cap * sizeof(pkg_rec));
        }
        list[cnt++] = p;
    }
    fclose(f);
    *out = list;
    *n = cnt;
    return 0;
}

int pkg_find(const char *id, pkg_rec *out) {
    pkg_rec *list = NULL;
    int n = 0, i, found = 0;
    pkg_list_load(&list, &n);
    for (i = 0; i < n; i++) {
        if (!strcmp(list[i].id, id)) {
            if (out) *out = list[i];
            found = 1;
            break;
        }
    }
    free(list);
    return found ? 0 : -1;
}

int pkg_save_all(pkg_rec *list, int n) {
    FILE *f;
    int i;
    mkdir_p(NEMESIS_ROOT, 0755);
    f = fopen(PACKAGES_DB, "w");
    if (!f) return -1;
    for (i = 0; i < n; i++) {
        fprintf(f, "%s|%s|%d|%s\n", list[i].id, list[i].name, list[i].locked, list[i].types);
    }
    fclose(f);
    return 0;
}

int pkg_upsert(const pkg_rec *p) {
    pkg_rec *list = NULL;
    int n = 0, i, found = 0;
    pkg_list_load(&list, &n);
    for (i = 0; i < n; i++) {
        if (!strcmp(list[i].id, p->id)) {
            list[i] = *p;
            found = 1;
            break;
        }
    }
    if (!found) {
        list = realloc(list, (size_t)(n + 1) * sizeof(pkg_rec));
        list[n++] = *p;
    }
    i = pkg_save_all(list, n);
    free(list);
    return i;
}

int pkg_remove(const char *id) {
    pkg_rec *list = NULL;
    int n = 0, i, w = 0;
    pkg_list_load(&list, &n);
    for (i = 0; i < n; i++) {
        if (!strcmp(list[i].id, id)) continue;
        list[w++] = list[i];
    }
    i = pkg_save_all(list, w);
    free(list);
    return i;
}

static int parse_svc_line(const char *line, svc_rec *s) {
    char buf[MAX_LINE];
    char *f[5];
    int i;
    snprintf(buf, sizeof(buf), "%s", line);
    f[0] = buf;
    for (i = 1; i < 5; i++) {
        f[i] = strchr(f[i - 1], '|');
        if (!f[i]) return -1;
        *f[i]++ = 0;
    }
    memset(s, 0, sizeof(*s));
    snprintf(s->qname, sizeof(s->qname), "%s", f[0]);
    snprintf(s->kind, sizeof(s->kind), "%s", f[1]);
    snprintf(s->path, sizeof(s->path), "%s", f[2]);
    snprintf(s->uid, sizeof(s->uid), "%s", f[3]);
    snprintf(s->mode, sizeof(s->mode), "%s", f[4]);
    return s->qname[0] ? 0 : -1;
}

int svc_list_load(svc_rec **out, int *n) {
    FILE *f;
    char line[MAX_LINE];
    svc_rec *list = NULL;
    int cap = 0, cnt = 0;
    *out = NULL;
    *n = 0;
    f = fopen(SERVICES_DB, "r");
    if (!f) return 0;
    while (fgets(line, sizeof(line), f)) {
        svc_rec s;
        size_t L = strlen(line);
        while (L && (line[L - 1] == '\n' || line[L - 1] == '\r')) line[--L] = 0;
        if (!line[0] || line[0] == '#') continue;
        if (parse_svc_line(line, &s) != 0) continue;
        if (cnt >= cap) {
            cap = cap ? cap * 2 : 8;
            list = realloc(list, (size_t)cap * sizeof(svc_rec));
        }
        list[cnt++] = s;
    }
    fclose(f);
    *out = list;
    *n = cnt;
    return 0;
}

int svc_save_all(svc_rec *list, int n) {
    FILE *f;
    int i;
    mkdir_p(NEMESIS_ROOT, 0755);
    f = fopen(SERVICES_DB, "w");
    if (!f) return -1;
    for (i = 0; i < n; i++) {
        fprintf(f, "%s|%s|%s|%s|%s\n", list[i].qname, list[i].kind, list[i].path, list[i].uid, list[i].mode);
    }
    fclose(f);
    return 0;
}

int svc_remove_prefix(const char *id) {
    svc_rec *list = NULL;
    int n = 0, i, w = 0;
    char prefix[MAX_ID + 2];
    snprintf(prefix, sizeof(prefix), "%s/", id);
    svc_list_load(&list, &n);
    for (i = 0; i < n; i++) {
        if (!strncmp(list[i].qname, prefix, strlen(prefix))) continue;
        if (!strcmp(list[i].qname, id)) continue;
        list[w++] = list[i];
    }
    i = svc_save_all(list, w);
    free(list);
    return i;
}

static int hook_path(char *out, size_t cap, const char *root, const char *rel) {
    if (!rel || !*rel || !strcmp(rel, "N/A")) {
        out[0] = 0;
        return 0;
    }
    if (rel[0] == '/') {
        snprintf(out, cap, "%s", rel);
        return 1;
    }
    snprintf(out, cap, "%s/%s", root, rel);
    return 1;
}

static int meta_from_ini(const ini_file *ini, char *id, size_t idcap, char *name, size_t ncap, char *types, size_t tcap) {
    const char *v;
    v = ini_get(ini, "nemesis", "id");
    if (!v || !*v) return -1;
    snprintf(id, idcap, "%s", v);
    v = ini_get(ini, "nemesis", "name");
    snprintf(name, ncap, "%s", v ? v : id);
    v = ini_get(ini, "nemesis", "types");
    snprintf(types, tcap, "%s", v ? v : "");
    return 0;
}

static int load_zip_meta(const char *zip, ini_file *ini, char *id, char *name, char *types) {
    char text[8192];
    if (zip_extract_file(zip, "NEMESIS.data", text, sizeof(text)) != 0) return -1;
    if (ini_load_mem(ini, text) != 0) return -1;
    return meta_from_ini(ini, id, MAX_ID, name, MAX_NAME, types, 80);
}

static void pm_uninstall_apks(const ini_file *ini, const char *root) {
    int i;
    for (i = 0; i < 32; i++) {
        char sec[32], cmd[MAX_PATH + 64];
        const char *kind, *path;
        snprintf(sec, sizeof(sec), "app.%d", i);
        if (!ini_has_section(ini, sec)) continue;
        kind = ini_get(ini, sec, "kind");
        path = ini_get(ini, sec, "path");
        if (!kind || strcmp(kind, "apk") || !path) continue;
        (void)root;
        /* Best-effort: pm may not be up. */
        snprintf(cmd, sizeof(cmd), "pm uninstall \"%s\" 2>/dev/null || true", path);
        run_cmd(cmd);
    }
}

static int install_apks(const ini_file *ini, const char *root) {
    int i, rc = 0;
    for (i = 0; i < 32; i++) {
        char sec[32], apk[MAX_PATH], cmd[MAX_PATH + 64];
        const char *kind, *path;
        snprintf(sec, sizeof(sec), "app.%d", i);
        if (!ini_has_section(ini, sec)) continue;
        kind = ini_get(ini, sec, "kind");
        path = ini_get(ini, sec, "path");
        if (!kind || strcmp(kind, "apk") || !path) continue;
        snprintf(apk, sizeof(apk), "%s/%s", root, path);
        if (!is_file(apk)) {
            log_msg("missing apk %s", apk);
            rc = 1;
            continue;
        }
        snprintf(cmd, sizeof(cmd), "pm install -r \"%s\"", apk);
        if (run_cmd(cmd) != 0) log_msg("pm install failed %s (ok if no zygote)", apk);
    }
    return rc;
}

static int register_services(const ini_file *ini, const char *id, const char *root) {
    svc_rec *list = NULL;
    int n = 0, i, w;
    char prefix[MAX_ID + 2];
    snprintf(prefix, sizeof(prefix), "%s/", id);
    svc_list_load(&list, &n);
    w = 0;
    for (i = 0; i < n; i++) {
        if (!strncmp(list[i].qname, prefix, strlen(prefix))) continue;
        list[w++] = list[i];
    }
    n = w;
    for (i = 0; i < 32; i++) {
        char sec[32], qn[MAX_QNAME], resolved[MAX_PATH];
        const char *local, *kind, *path, *uid, *mode;
        svc_rec s;
        snprintf(sec, sizeof(sec), "service.%d", i);
        if (!ini_has_section(ini, sec)) continue;
        local = ini_get(ini, sec, "name");
        kind = ini_get(ini, sec, "kind");
        path = ini_get(ini, sec, "path");
        uid = ini_get(ini, sec, "uid");
        mode = ini_get(ini, sec, "mode");
        if (!local || !*local) continue;
        qname_join(qn, sizeof(qn), id, local);
        memset(&s, 0, sizeof(s));
        snprintf(s.qname, sizeof(s.qname), "%s", qn);
        snprintf(s.kind, sizeof(s.kind), "%s", kind ? kind : "sh");
        if (path && path[0] == '/') snprintf(resolved, sizeof(resolved), "%s", path);
        else if (path) snprintf(resolved, sizeof(resolved), "%s/%s", root, path);
        else resolved[0] = 0;
        snprintf(s.path, sizeof(s.path), "%s", resolved);
        snprintf(s.uid, sizeof(s.uid), "%s", uid ? uid : "root");
        snprintf(s.mode, sizeof(s.mode), "%s", mode ? mode : "background");
        list = realloc(list, (size_t)(n + 1) * sizeof(svc_rec));
        list[n++] = s;
    }
    i = svc_save_all(list, n);
    free(list);
    return i;
}

static int start_pkg_services(const char *id) {
    svc_rec *list = NULL;
    int n = 0, i;
    char prefix[MAX_ID + 2];
    snprintf(prefix, sizeof(prefix), "%s/", id);
    svc_list_load(&list, &n);
    for (i = 0; i < n; i++) {
        if (!strncmp(list[i].qname, prefix, strlen(prefix))) svc_start(list[i].qname);
    }
    free(list);
    return 0;
}

static int stop_pkg_services(const char *id) {
    svc_rec *list = NULL;
    int n = 0, i;
    char prefix[MAX_ID + 2];
    snprintf(prefix, sizeof(prefix), "%s/", id);
    svc_list_load(&list, &n);
    for (i = 0; i < n; i++) {
        if (!strncmp(list[i].qname, prefix, strlen(prefix))) svc_stop(list[i].qname);
    }
    free(list);
    return 0;
}

static int run_named_hook(const ini_file *ini, const char *root, const char *key) {
    char script[MAX_PATH];
    const char *rel = ini_get(ini, "nemesis", key);
    if (!hook_path(script, sizeof(script), root, rel)) return 0;
    log_msg("hook %s -> %s", key, script);
    return run_script(script, root, "root");
}

static int uninstall_tree(const char *id, int allow_locked) {
    pkg_rec rec;
    char root[MAX_PATH], meta[MAX_PATH], tmpun[MAX_PATH];
    ini_file ini;
    int have_ini = 0;
    if (pkg_find(id, &rec) != 0) {
        fprintf(stderr, "package not installed: %s\n", id);
        return 1;
    }
    if (rec.locked && !allow_locked) {
        fprintf(stderr, "package is locked: %s\n", id);
        return 2;
    }
    snprintf(root, sizeof(root), "%s/%s", NEMESIS_APPS, id);
    snprintf(meta, sizeof(meta), "%s/NEMESIS.data", root);
    if (is_file(meta) && ini_load(&ini, meta) == 0) have_ini = 1;
    stop_pkg_services(id);
    if (have_ini) run_named_hook(&ini, root, "preuninstall");
    if (have_ini) pm_uninstall_apks(&ini, root);
    svc_remove_prefix(id);
    if (have_ini) {
        const char *rel = ini_get(&ini, "nemesis", "postuninstall");
        char script[MAX_PATH];
        if (hook_path(script, sizeof(script), root, rel) && is_file(script)) {
            snprintf(tmpun, sizeof(tmpun), "%s/postun-%s.sh", NEMESIS_TMP, id);
            copy_file(script, tmpun, 0755);
            run_script(tmpun, root, "root");
            unlink(tmpun);
        }
        ini_free(&ini);
    }
    rm_rf(root);
    pkg_remove(id);
    log_msg("uninstalled %s", id);
    return 0;
}

static int do_fresh_install(const char *stage, ini_file *ini,
                            const char *id, const char *name, const char *types, int locked) {
    char dest[MAX_PATH];
    pkg_rec rec;
    int rc;
    snprintf(dest, sizeof(dest), "%s/%s", NEMESIS_APPS, id);
    rc = run_named_hook(ini, stage, "preinstall");
    if (rc != 0) {
        log_msg("preinstall failed rc=%d", rc);
        return rc;
    }
    rm_rf(dest);
    mkdir_p(dest, 0755);
    if (copy_tree(stage, dest) != 0) {
        log_msg("copy tree failed %s -> %s", stage, dest);
        return 1;
    }
    install_apks(ini, dest);
    register_services(ini, id, dest);
    memset(&rec, 0, sizeof(rec));
    snprintf(rec.id, sizeof(rec.id), "%s", id);
    snprintf(rec.name, sizeof(rec.name), "%s", name);
    rec.locked = locked;
    snprintf(rec.types, sizeof(rec.types), "%s", types);
    pkg_upsert(&rec);
    rc = run_named_hook(ini, dest, "postinstall");
    start_pkg_services(id);
    return rc;
}

int cmd_install(int argc, char **argv) {
    int update = 0, i, locked_exist = 0;
    const char *zip = NULL;
    char stage[MAX_PATH], id[MAX_ID], name[MAX_NAME], types[80];
    ini_file ini;
    pkg_rec existing;
    int have_existing;
    for (i = 0; i < argc; i++) {
        if (!strcmp(argv[i], "--update")) update = 1;
        else if (argv[i][0] != '-') zip = argv[i];
    }
    if (!zip) {
        fprintf(stderr, "usage: nemesis install [--update] <zip>\n");
        return 1;
    }
    if (!is_file(zip)) {
        fprintf(stderr, "missing zip: %s\n", zip);
        return 1;
    }
    ensure_dirs();
    memset(&ini, 0, sizeof(ini));
    if (load_zip_meta(zip, &ini, id, name, types) != 0) {
        fprintf(stderr, "invalid package metadata\n");
        return 1;
    }
    if (!pkg_id_ok(id)) {
        fprintf(stderr, "invalid package id\n");
        ini_free(&ini);
        return 1;
    }
    have_existing = pkg_find(id, &existing) == 0;
    if (have_existing && existing.locked) locked_exist = 1;
    if (have_existing && locked_exist) {
        fprintf(stderr, "package is locked and cannot be updated: %s\n", id);
        ini_free(&ini);
        return 2;
    }
    if (have_existing && !update) {
        fprintf(stderr, "already installed: %s (pass --update)\n", id);
        ini_free(&ini);
        return 3;
    }
    snprintf(stage, sizeof(stage), "%s/stage/%s", NEMESIS_TMP, id);
    rm_rf(stage);
    mkdir_p(stage, 0755);
    if (zip_extract(zip, stage) != 0) {
        fprintf(stderr, "unzip failed\n");
        ini_free(&ini);
        rm_rf(stage);
        return 1;
    }
    if (have_existing && update) {
        char oldroot[MAX_PATH], oldmeta[MAX_PATH];
        ini_file oldini;
        snprintf(oldroot, sizeof(oldroot), "%s/%s", NEMESIS_APPS, id);
        snprintf(oldmeta, sizeof(oldmeta), "%s/NEMESIS.data", oldroot);
        stop_pkg_services(id);
        if (is_file(oldmeta) && ini_load(&oldini, oldmeta) == 0) {
            run_named_hook(&oldini, oldroot, "preuninstall");
            pm_uninstall_apks(&oldini, oldroot);
            svc_remove_prefix(id);
            run_named_hook(&oldini, oldroot, "postuninstall");
            ini_free(&oldini);
        } else {
            svc_remove_prefix(id);
        }
        rm_rf(oldroot);
        log_msg("update replacing %s", id);
    }
    i = do_fresh_install(stage, &ini, id, name, types, 0);
    ini_free(&ini);
    rm_rf(stage);
    if (i == 0) fprintf(stdout, "ok %s %s\n", have_existing ? "updated" : "installed", id);
    return i;
}

int cmd_uninstall(const char *id, int allow_locked) {
    if (!id || !*id) {
        fprintf(stderr, "usage: nemesis uninstall <package-id>\n");
        return 1;
    }
    ensure_dirs();
    return uninstall_tree(id, allow_locked);
}

int cmd_pkg_info(const char *zip) {
    ini_file ini;
    char id[MAX_ID], name[MAX_NAME], types[80];
    pkg_rec rec;
    int inst;
    if (!zip) return 1;
    memset(&ini, 0, sizeof(ini));
    if (load_zip_meta(zip, &ini, id, name, types) != 0) {
        fprintf(stderr, "invalid package metadata\n");
        return 1;
    }
    if (!pkg_id_ok(id)) {
        fprintf(stderr, "invalid package id\n");
        ini_free(&ini);
        return 1;
    }
    inst = pkg_find(id, &rec) == 0;
    printf("id=%s\nname=%s\ntypes=%s\ninstalled=%d\nlocked=%d\n",
           id, name, types, inst, inst ? rec.locked : 0);
    ini_free(&ini);
    return 0;
}

int cmd_pkg_list(void) {
    pkg_rec *list = NULL;
    int n = 0, i;
    seed_core_packages();
    pkg_list_load(&list, &n);
    for (i = 0; i < n; i++) {
        printf("%s|%s|%d|%s\n", list[i].id, list[i].name, list[i].locked, list[i].types);
    }
    free(list);
    fflush(stdout);
    return 0;
}

int cmd_pkg_launch(const char *id) {
    pkg_rec rec;
    char root[MAX_PATH], meta[MAX_PATH];
    ini_file ini;
    int i, launched = 0;
    if (!id || !*id) {
        fprintf(stderr, "usage: nemesis pkg-launch <package-id>\n");
        return 1;
    }
    if (pkg_find(id, &rec) != 0) {
        fprintf(stderr, "package not installed: %s\n", id);
        return 1;
    }
    snprintf(root, sizeof(root), "%s/%s", NEMESIS_APPS, id);
    snprintf(meta, sizeof(meta), "%s/NEMESIS.data", root);
    if (!is_file(meta) || ini_load(&ini, meta) != 0) {
        fprintf(stderr, "missing package metadata: %s\n", id);
        return 1;
    }
    for (i = 0; i < 32; i++) {
        char sec[32], script[MAX_PATH], cmd[MAX_PATH + 80];
        const char *kind, *path, *comp, *pkg;
        snprintf(sec, sizeof(sec), "app.%d", i);
        if (!ini_has_section(&ini, sec)) continue;
        kind = ini_get(&ini, sec, "kind");
        path = ini_get(&ini, sec, "path");
        comp = ini_get(&ini, sec, "component");
        pkg = ini_get(&ini, sec, "package");
        if (comp && *comp) {
            snprintf(cmd, sizeof(cmd), "am start -n '%s'", comp);
            run_cmd(cmd);
            launched = 1;
        } else if (pkg && *pkg) {
            snprintf(cmd, sizeof(cmd),
                     "monkey -p '%s' -c android.intent.category.LAUNCHER 1", pkg);
            run_cmd(cmd);
            launched = 1;
        } else if (kind && !strcmp(kind, "sh") && path && *path) {
            if (path[0] == '/') snprintf(script, sizeof(script), "%s", path);
            else snprintf(script, sizeof(script), "%s/%s", root, path);
            run_script(script, root, "root");
            launched = 1;
        }
    }
    {
        svc_rec *list = NULL;
        int n = 0, j;
        char prefix[MAX_ID + 2];
        snprintf(prefix, sizeof(prefix), "%s/", id);
        svc_list_load(&list, &n);
        for (j = 0; j < n; j++) {
            if (strncmp(list[j].qname, prefix, strlen(prefix))) continue;
            if (!strcmp(list[j].kind, "instance")) {
                svc_start(list[j].qname);
                launched = 1;
            }
        }
        free(list);
    }
    ini_free(&ini);
    if (!launched) {
        fprintf(stderr, "package has nothing to launch: %s\n", id);
        return 1;
    }
    printf("ok launched %s\n", id);
    fflush(stdout);
    return 0;
}

static const char *USB_HANDLER_META =
    "[nemesis]\n"
    "version=1.0\n"
    "id=usb-handler\n"
    "name=USB Handler\n"
    "types=service\n"
    "\n"
    "[service.0]\n"
    "name=usb-handler\n"
    "kind=sh\n"
    "path=svc/usb-handler.sh\n"
    "uid=root\n"
    "mode=restart\n";

static const char *USB_HANDLER_SH =
    "#!/system/bin/sh\n"
    "export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin\n"
    "exec /system/xbin/nemesis usb-handler\n";

int seed_core_packages(void) {
    char root[MAX_PATH], sh[MAX_PATH], meta[MAX_PATH];
    pkg_rec rec;
    ini_file ini;
    ensure_dirs();
    snprintf(root, sizeof(root), "%s/%s", NEMESIS_APPS, LOCKED_ID);
    mkdir_p(root, 0755);
    {
        char svcdir[MAX_PATH];
        snprintf(svcdir, sizeof(svcdir), "%s/svc", root);
        mkdir_p(svcdir, 0755);
    }
    snprintf(meta, sizeof(meta), "%s/NEMESIS.data", root);
    snprintf(sh, sizeof(sh), "%s/svc/usb-handler.sh", root);
    write_file(meta, USB_HANDLER_META, 0644);
    write_file(sh, USB_HANDLER_SH, 0755);
    memset(&ini, 0, sizeof(ini));
    ini_load_mem(&ini, USB_HANDLER_META);
    register_services(&ini, LOCKED_ID, root);
    ini_free(&ini);
    memset(&rec, 0, sizeof(rec));
    snprintf(rec.id, sizeof(rec.id), "%s", LOCKED_ID);
    snprintf(rec.name, sizeof(rec.name), "%s", "USB Handler");
    rec.locked = 1;
    snprintf(rec.types, sizeof(rec.types), "%s", "service");
    pkg_upsert(&rec);
    {
        char fp[MAX_PATH], failp[MAX_PATH];
        pid_path_for(fp, sizeof(fp), LOCKED_ID "/usb-handler");
        snprintf(failp, sizeof(failp), "%s.fails", fp);
        unlink(failp);
    }
    return 0;
}

static int strip_qb_hook(void) {
    FILE *in, *out;
    char tmp[MAX_PATH], line[MAX_LINE];
    int skip = 0, rc, found = 0, depth = 0;
    if (!is_file(QB_RUNTIME)) return 0;
    in = fopen(QB_RUNTIME, "r");
    if (!in) return -1;
    while (fgets(line, sizeof(line), in)) {
        if (strstr(line, QB_MARKER)) {
            found = 1;
            break;
        }
    }
    fclose(in);
    if (!found) return 0;
    ensure_dirs();
    snprintf(tmp, sizeof(tmp), "%s/qb_runtime.stripped", NEMESIS_TMP);
    in = fopen(QB_RUNTIME, "r");
    out = fopen(tmp, "w");
    if (!in || !out) {
        if (in) fclose(in);
        if (out) fclose(out);
        return -1;
    }
    while (fgets(line, sizeof(line), in)) {
        char *t = line;
        if (strstr(line, QB_MARKER)) {
            skip = 1;
            depth = 0;
            continue;
        }
        if (skip) {
            while (*t == ' ' || *t == '\t') t++;
            if (t[0] == 'i' && t[1] == 'f' &&
                (t[2] == ' ' || t[2] == '\t' || t[2] == '['))
                depth++;
            if (t[0] == 'f' && t[1] == 'i' &&
                (t[2] == 0 || t[2] == '\n' || t[2] == '\r' || t[2] == ' ' ||
                 t[2] == '\t' || t[2] == ';' || t[2] == '#')) {
                depth--;
                if (depth <= 0) skip = 0;
            }
            continue;
        }
        fputs(line, out);
    }
    fclose(in);
    fclose(out);
    rc = copy_file(tmp, QB_RUNTIME, 0644);
    unlink(tmp);
    return rc;
}

int cmd_wipe(int confirm) {
    pkg_rec *list = NULL;
    int n = 0, i;
    if (!confirm) {
        fprintf(stderr, "usage: nemesis wipe --confirm\n");
        return 1;
    }
    ensure_dirs();
    log_msg("wipe begin");
    pkg_list_load(&list, &n);
    for (i = 0; i < n; i++) {
        uninstall_tree(list[i].id, 1);
    }
    free(list);
    purge_manager_dalvik();
    if (remount_system(1) != 0)
        log_msg("wipe: remount /system rw failed errno=%d", errno);
    unlink(MANAGER_APK);
    unlink(INSTALL_RECOVERY);
    strip_qb_hook();
    unlink(BIN_PATH);
    rm_rf(NEMESIS_ROOT);
    rm_rf(NEMESIS_TMP);
    purge_manager_dalvik();
    if (path_exists(BIN_PATH) || path_exists(MANAGER_APK) || path_exists(INSTALL_RECOVERY)) {
        log_msg("wipe incomplete: system files still present");
        fprintf(stderr, "wipe incomplete: /system stayed read-only\n");
        fprintf(stdout, "wipe incomplete\n");
        fflush(stdout);
        return 1;
    }
    log_msg("wipe done");
    fprintf(stdout, "ok wiped\n");
    fflush(stdout);
    return 0;
}
