package com.nemesis.manager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class Cli {
    public static class Result {
        public int rc;
        public String out;

        Result(int rc, String out) {
            this.rc = rc;
            this.out = out != null ? out : "";
        }
    }

    public static Result exec(String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "/system/xbin/nemesis";
            System.arraycopy(args, 0, cmd, 1, args.length);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream in = p.getInputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            int rc = p.waitFor();
            return new Result(rc, bos.toString("UTF-8"));
        } catch (Exception e) {
            return new Result(1, "ERR " + e.getMessage());
        }
    }

    public static String run(String... args) {
        return exec(args).out;
    }

    public static int runRc(String... args) {
        return exec(args).rc;
    }
}
