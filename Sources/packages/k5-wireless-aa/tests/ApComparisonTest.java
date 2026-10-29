package com.saintkite.k5aa;
public final class ApComparisonTest {
    public static final class Config {
        public String SSID="ordinary",preSharedKey="ordinary-secret";
        public String ConnectivitySSID="AA network",ConnectivityPSK="never-export-me";
        public int getAuthType(){return 4;}
    }
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        Config c=new Config();
        String report=DiagnosticComparison.compare("ssid=AA network\nwpa_passphrase=never-export-me\nchannel=149\nwpa=2\n",c,null);
        check(report.contains("GPM_EXPECTED_SSID_MATCH=true") && report.contains("GPM_EXPECTED_CREDENTIAL_MATCH=true"));
        check(report.contains("ORDINARY_SSID_MATCH=false") && report.contains("ORDINARY_CREDENTIAL_MATCH=false"));
        check(!report.contains("never-export-me") && !report.contains("ordinary-secret") && !report.contains("AA network"));
        check(report.contains("NOT_wire_capture") && report.contains("GPM_CACHE_AND_BLUETOOTH_WIRE_VALUES=NOT_OBSERVED"));
        System.out.println("PASS AA Connectivity fields are compared separately and credentials never appear in diagnostics");
        report=DiagnosticComparison.compare("ssid=ordinary\nwpa_passphrase=wrong-secret\n",c,null);
        check(report.contains("GPM_EXPECTED_SSID_MATCH=false") && report.contains("GPM_EXPECTED_CREDENTIAL_MATCH=false"));
        check(report.contains("ORDINARY_SSID_MATCH=true"));
        c.ConnectivitySSID="A A";
        report=DiagnosticComparison.compare("ssid2=412041\nwpa_psk=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n",c,null);
        check(report.contains("GPM_EXPECTED_SSID_MATCH=true") && report.contains("GPM_EXPECTED_CREDENTIAL_MATCH=false"));
        System.out.println("PASS SSID mismatch and hex SSID remain distinguishable with derived-key credential comparison");
        c.ConnectivitySSID=" AA ";
        report=DiagnosticComparison.compare("ssid= AA \nwpa_passphrase=never-export-me\n",c,null);
        check(report.contains("GPM_EXPECTED_SSID_MATCH=true"));
        check(DiagnosticComparison.compare("ssid2=not-hex\n",c,null).contains("GPM_EXPECTED_SSID_MATCH=UNKNOWN"));
        check(DiagnosticComparison.compare("ssid=AA\nwpa_psk=abc\nwpa_passphrase=secret\n",c,null).contains("UNKNOWN_multiple_credentials"));
        System.out.println("PASS spaces are preserved and malformed or ambiguous configurations never report a false match");
        String info=DiagnosticComparison.publicInfo("ssid=AA network\nwpa_passphrase=never-export-me\nchannel=149\n");
        check(info.contains("AP_NAME=AA network") && info.contains("AP_CHANNEL=149") && !info.contains("never-export-me"));
        System.out.println("PASS phone AP lookup displays only the public network name and channel");
        byte[] derived=ApComparison.derive("password","salt".getBytes("US-ASCII"),4096,20);
        StringBuilder hex=new StringBuilder();for(byte v:derived)hex.append(String.format("%02x",v&255));
        check(hex.toString().equals("4b007901b765489abead49d926f721d065a429c1"));
        c.ConnectivitySSID="IEEE";c.ConnectivityPSK="password";
        String key="f42c6fc52df0ebef9ebb4b90b38a5f902e83fe1b135a70e23aed762e9710a12e";
        report=DiagnosticComparison.compare("ssid=IEEE\nwpa_psk="+key+"\n",c,null);
        check(report.contains("GPM_EXPECTED_CREDENTIAL_MATCH=true"));
        check(!report.contains(key) && !report.contains("password"));
        report=DiagnosticComparison.compare("ssid=another\nwpa_psk="+key+"\n",c,null);
        check(report.contains("GPM_EXPECTED_CREDENTIAL_MATCH=false"));
        System.out.println("PASS RFC6070 and independent WPA key vectors verify derived credentials with SSID-bound salt and no key export");
    }
}
