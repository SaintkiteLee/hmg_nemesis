package com.saintkite.k5aa;

import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Compare in memory. No credential, credential hash or raw configuration is returned. */
public final class ApComparison {
    static Map<String,String> parse(String text) {
        Map<String,String> result=new LinkedHashMap<String,String>();
        for(String line:text.split("\\r?\\n")) {
            if(line.trim().startsWith("#"))continue;
            int p=line.indexOf('=');if(p<1)continue;
            result.put(line.substring(0,p).trim(),line.substring(p+1));
        }
        return result;
    }
    static byte[] ssid(Map<String,String> fields) throws Exception {
        String hex=fields.get("ssid2");
        if(hex!=null) {
            if(!hex.matches("(?i)([0-9a-f]{2}){1,32}"))return null;
            byte[] bytes=new byte[hex.length()/2];
            for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
            return bytes;
        }
        String name=fields.get("ssid");return name==null?null:name.getBytes("UTF-8");
    }
    static String value(Object config,String field) {
        try{Object v=config.getClass().getField(field).get(config);return v instanceof String?(String)v:null;}
        catch(Exception absent){return null;}
    }
    static String equal(byte[] actual,String expected) throws Exception {
        return actual==null||expected==null?"UNKNOWN":Boolean.toString(Arrays.equals(actual,expected.getBytes("UTF-8")));
    }
    private static final LinkedHashMap<String,String> keyCache=new LinkedHashMap<String,String>();
    static byte[] derive(String password,byte[] salt,int iterations,int bytes) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,iterations,bytes*8);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();}
        finally{spec.clearPassword();}
    }
    static synchronized String derivedKey(String expected,byte[] name) throws Exception {
        // RAM-only bounded cache: no credential, PSK or credential fingerprint is written.
        String key=expected+"\u0000"+Arrays.toString(name),cached=keyCache.get(key);
        if(cached!=null)return cached;
        byte[] bytes=derive(expected,name,4096,32);StringBuilder out=new StringBuilder();
        for(byte b:bytes)out.append(String.format("%02x",b&255));Arrays.fill(bytes,(byte)0);
        if(keyCache.size()>=4)keyCache.clear();
        cached=out.toString();keyCache.put(key,cached);return cached;
    }
    static String credentialMatch(Map<String,String> fields,String expected) throws Exception {
        if(expected==null)return "UNKNOWN_missing_expected";
        String pass=fields.get("wpa_passphrase"),psk=fields.get("wpa_psk");
        if(pass!=null && psk!=null)return "UNKNOWN_multiple_credentials";
        if(pass!=null)return Boolean.toString(pass.equals(expected));
        if(psk!=null && psk.matches("(?i)[0-9a-f]{64}")) {
            if(expected.matches("(?i)[0-9a-f]{64}"))return Boolean.toString(psk.equalsIgnoreCase(expected));
            byte[] name=ssid(fields);
            if(name!=null && expected.matches("[ -~]{8,63}"))return Boolean.toString(psk.equalsIgnoreCase(derivedKey(expected,name)));
        }
        return "UNKNOWN_missing_or_different_representation";
    }
    public static String compare(String raw,Object config,String interfaceMac) throws Exception {
        Map<String,String> fields=parse(raw);byte[] actual=ssid(fields);
        StringBuilder out=new StringBuilder("AP_COMPARE_SOURCE=pinned_GPM_getWiFiInfo_Connectivity_fields_NOT_wire_capture\n");
        out.append("ACTUAL_SSID_SHA256=").append(actual==null?"UNKNOWN":Main.sha(actual)).append(" bytes=").append(actual==null?-1:actual.length).append('\n');
        out.append("GPM_EXPECTED_SSID_MATCH=").append(equal(actual,value(config,"ConnectivitySSID"))).append('\n');
        out.append("ORDINARY_SSID_MATCH=").append(equal(actual,value(config,"SSID"))).append('\n');
        out.append("GPM_EXPECTED_CREDENTIAL_MATCH=").append(credentialMatch(fields,value(config,"ConnectivityPSK"))).append('\n');
        out.append("ORDINARY_CREDENTIAL_MATCH=").append(credentialMatch(fields,value(config,"preSharedKey"))).append('\n');
        String bssid=fields.get("bssid");
        if(bssid!=null && interfaceMac!=null && bssid.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))
            out.append("HOSTAPD_BSSID_VS_INTERFACE_MATCH=").append(bssid.equalsIgnoreCase(interfaceMac.trim())).append('\n');
        else out.append("HOSTAPD_BSSID_VS_INTERFACE_MATCH=UNKNOWN_no_explicit_bssid\n");
        try {out.append("GPM_EXPECTED_AUTH_TYPE=").append(((Number)config.getClass().getMethod("getAuthType").invoke(config)).intValue()).append('\n');}
        catch(Exception absent){out.append("GPM_EXPECTED_AUTH_TYPE=UNKNOWN\n");}
        out.append("GPM_CACHE_AND_BLUETOOTH_WIRE_VALUES=NOT_OBSERVED\n");
        return out.toString();
    }
    public static String publicInfo(String raw) throws Exception {
        Map<String,String> fields=parse(raw);byte[] bytes=ssid(fields);
        String name=bytes==null?"확인 불가":new String(bytes,"UTF-8").replaceAll("[\\p{Cntrl}]","?");
        String channel=fields.get("channel");if(channel==null||!channel.matches("[0-9]{1,3}"))channel="?";
        return "AP_NAME="+name+"\nAP_CHANNEL="+channel;
    }
}
