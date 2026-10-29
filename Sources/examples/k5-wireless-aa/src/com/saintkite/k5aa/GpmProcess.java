package com.saintkite.k5aa;

import java.io.*;

/** Exact process identity for the one persistent AA app; never signal by name or UID alone. */
final class GpmProcess {
    static final class Identity {
        final int pid;final String startTime;
        Identity(int p,String s){pid=p;startTime=s;}
    }
    private static String read(File file) throws Exception {
        FileInputStream in=new FileInputStream(file);
        try {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;
            while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new IOException("Oversized process metadata");out.write(b,0,n);}
            return new String(out.toByteArray(),"UTF-8");
        }finally{in.close();}
    }
    static Identity inspect(File root,int pid) {
        if(pid<=1)return null;
        try {
            File dir=new File(root,Integer.toString(pid));String cmd=read(new File(dir,"cmdline"));
            int nul=cmd.indexOf('\0');if(nul<0 || !GpmAccess.PACKAGE.equals(cmd.substring(0,nul)))return null;
            boolean correctUid=false;
            for(String line:read(new File(dir,"status")).split("\n"))if(line.startsWith("Uid:")){
                String[] fields=line.trim().split("\\s+");if(fields.length!=5)return null;
                for(int i=1;i<5;i++)if(!"1000".equals(fields[i]))return null;correctUid=true;
            }
            if(!correctUid)return null;
            String stat=read(new File(dir,"stat"));int end=stat.lastIndexOf(") ");if(end<0)return null;
            if(!stat.startsWith(pid+" ("))return null;
            String[] fields=stat.substring(end+2).trim().split("\\s+");
            if(fields.length<20 || !fields[19].matches("[0-9]+"))return null;
            return new Identity(pid,fields[19]);
        }catch(Exception unavailable){return null;}
    }
    static Identity find(File root) throws Exception {
        Identity result=null;File[] entries=root.listFiles();if(entries==null)return null;
        for(File entry:entries){String name=entry.getName();if(!name.matches("[0-9]+"))continue;
            int pid;try{pid=Integer.parseInt(name);}catch(NumberFormatException invalid){continue;}
            Identity current=inspect(root,pid);if(current==null)continue;
            if(result!=null)throw new IOException("Multiple matching AA processes; refusing a reset");result=current;
        }
        return result;
    }
    static boolean killIfStillSame(File root,Identity before) {
        if(before==null)return false;
        Identity now=inspect(root,before.pid);
        if(now==null || !before.startTime.equals(now.startTime))return false;
        android.os.Process.killProcess(before.pid);return true;
    }
}
