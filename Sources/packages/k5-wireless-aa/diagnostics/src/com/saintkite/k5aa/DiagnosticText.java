package com.saintkite.k5aa;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded diagnostic reads. AP credentials never leave the private backup. */
public final class DiagnosticText {
    private static final Pattern SECRET=Pattern.compile("(?i)\\b(password|passphrase|presharedkey|connectivitypsk|wifipassword|wpa_psk|wpa_passphrase)\\b|(?:^|\\W)psk\\s*[:=]");
    public static String redact(String line) {
        return SECRET.matcher(line).find()?"<credential-bearing line omitted>":line;
    }
    public static String error(Throwable failure) {
        if(failure instanceof java.lang.reflect.InvocationTargetException && failure.getCause()!=null)failure=failure.getCause();
        String message=redact(failure.toString());return message.substring(0,Math.min(message.length(),512));
    }
    public static String read(File file,int limit) throws IOException {
        FileInputStream in=new FileInputStream(file);
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int count;
            while(out.size()<limit && (count=in.read(bytes,0,Math.min(bytes.length,limit-out.size())))!=-1)out.write(bytes,0,count);
            return new String(out.toByteArray(),"UTF-8");
        } finally {in.close();}
    }
    public static Map<String,String> apFields(String config) {
        Set<String> allowed=new HashSet<String>(Arrays.asList("interface","ctrl_interface","driver","hw_mode","channel","country_code",
            "ieee80211n","ieee80211ac","wpa","wpa_key_mgmt","rsn_pairwise","wpa_pairwise","auth_algs","ignore_broadcast_ssid"));
        Map<String,String> result=new LinkedHashMap<String,String>();
        for(String line:config.split("\\r?\\n")) {
            int pos=line.indexOf('=');if(pos<0)continue;
            String key=line.substring(0,pos).trim();
            if(allowed.contains(key))result.put(key,line.substring(pos+1).trim());
        }
        return result;
    }
    public static String ctrlServer(Map<String,String> fields) {
        String dir=fields.get("ctrl_interface"),iface=fields.get("interface");
        if(iface==null)iface="wlan0";
        if(!iface.matches("[A-Za-z0-9_.-]{1,15}"))return null;
        if(dir==null)return null;
        if(dir.startsWith("DIR="))dir=dir.substring(4);
        dir=dir.split("\\s+")[0];
        if(!dir.startsWith("/data/misc/") || dir.contains(".."))return null;
        return dir.replaceAll("/+$","")+"/"+iface;
    }
    public static String firstMac(String text) {
        String first=text.split("\\r?\\n",2)[0].trim();
        return first.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")?first:null;
    }
    public static synchronized void append(File file,String text,int limit) throws IOException {
        if(file.length()>=limit)return;
        byte[] bytes=text.getBytes("UTF-8");int count=(int)Math.min(bytes.length,limit-file.length());
        FileOutputStream out=new FileOutputStream(file,true);
        try {out.write(bytes,0,count);out.getFD().sync();}finally{out.close();}
    }
}
