package com.saintkite.k5aa;
import java.util.*;
import static com.saintkite.k5aa.ApComparison.*;
public final class DiagnosticComparison {
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
