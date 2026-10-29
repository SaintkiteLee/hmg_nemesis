package com.saintkite.k5aa;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
import java.util.UUID;

/** Read-only station queries and an independent event subscription. */
public final class HostapdControl implements Closeable {
    private final LocalSocket socket;
    private boolean attached;
    public HostapdControl(File state,String server) throws IOException {
        socket=new LocalSocket(LocalSocket.SOCKET_DGRAM);
        try {
            // hostapd may run as wifi, which cannot traverse our root-only backup
            // directory to reply. Abstract clients have no filesystem DAC path.
            socket.bind(new LocalSocketAddress("k5-aa-ctrl-"+UUID.randomUUID().toString(),LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(350);
            socket.connect(new LocalSocketAddress(server,LocalSocketAddress.Namespace.FILESYSTEM));
        } catch(IOException failed) {close();throw failed;}
        catch(RuntimeException failed) {close();throw failed;}
    }
    static boolean allowed(String command) {
        return "PING".equals(command) || "ATTACH".equals(command) || "DETACH".equals(command) || "STA-FIRST".equals(command) ||
            command.matches("STA-NEXT (?i)([0-9a-f]{2}:){5}[0-9a-f]{2}");
    }
    public String request(String command) throws IOException {
        if(!allowed(command))throw new IOException("Diagnostic command is not read-only");
        byte[] bytes=command.getBytes("UTF-8");socket.getOutputStream().write(bytes);
        for(int i=0;i<8;i++) {
            String answer=receive();if(answer.startsWith("<"))continue;
            if("ATTACH".equals(command) && "OK".equals(answer))attached=true;
            if("DETACH".equals(command) && "OK".equals(answer))attached=false;
            return answer;
        }
        throw new IOException("Hostapd reply not received among events");
    }
    public String receive() throws IOException {
        return readDatagram(socket.getInputStream());
    }
    public String receiveIfAvailable() throws IOException {
        InputStream input=socket.getInputStream();
        // KitKat maps an idle recvmsg timeout to plain IOException. Inspect the
        // queued byte count first; only this collector consumes this socket.
        if(input.available()==0)return null;
        return readDatagram(input);
    }
    private String readDatagram(InputStream input) throws IOException {
        byte[] bytes=new byte[8192];int n=input.read(bytes);
        if(n<0)throw new EOFException("Hostapd datagram read returned "+n+"; station state unknown");
        return new String(bytes,0,n,"UTF-8").trim();
    }
    public void close() {
        if(attached) {
            attached=false;
            try{socket.getOutputStream().write(new byte[]{'D','E','T','A','C','H'});}catch(IOException ignored){}
        }
        try{socket.close();}catch(IOException ignored){}
    }
}
