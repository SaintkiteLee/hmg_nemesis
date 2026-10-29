package com.saintkite.k5aa;

import java.io.IOException;
import java.util.Map;

/** Modify a caller-owned Parcelable copy; never the private baseline or the AA fields. */
public final class ApCredentials {
    static String[] checked(Object config) throws Exception {
        String name=ApComparison.value(config,"ConnectivitySSID"),secret=ApComparison.value(config,"ConnectivityPSK");
        if(name==null || name.getBytes("UTF-8").length<1 || name.getBytes("UTF-8").length>32 || name.matches("(?s).*[\\p{Cntrl}].*"))
            throw new IOException("AA network name is missing or invalid");
        if(secret==null || !secret.matches("[ -~]{8,63}"))throw new IOException("AA credential has an unsupported format");
        if(((Number)config.getClass().getMethod("getAuthType").invoke(config)).intValue()!=4)
            throw new IOException("Expected the captured WPA2 AP authentication type");
        return new String[]{name,secret};
    }
    static void align(Object copy) throws Exception {
        String[] values=checked(copy);
        if(copy.getClass().getField("SSID").getType()!=String.class || copy.getClass().getField("preSharedKey").getType()!=String.class)
            throw new IOException("AP credential fields differ from the captured framework");
        copy.getClass().getField("SSID").set(copy,values[0]);
        copy.getClass().getField("preSharedKey").set(copy,values[1]);
    }
    static boolean activeMatches(String raw,Object current) throws Exception {
        String[] values=checked(current);Map<String,String> fields=ApComparison.parse(raw);
        if(!values[0].equals(ApComparison.value(current,"SSID")) || !values[1].equals(ApComparison.value(current,"preSharedKey")))return false;
        if(!"true".equals(ApComparison.equal(ApComparison.ssid(fields),values[0])) || !"true".equals(ApComparison.credentialMatch(fields,values[1])))return false;
        if(!"wlan0".equals(fields.get("interface")) || !"2".equals(fields.get("wpa")))return false;
        String pairwise=fields.get("rsn_pairwise"),keys=fields.get("wpa_key_mgmt");
        if(pairwise==null || !pairwise.matches("(?:\\S+\\s+)*CCMP(?:\\s+\\S+)*"))return false;
        if(keys!=null && !"WPA-PSK".equals(keys.trim()))return false;
        return true;
    }
}
