package dev.local.tvhomeselector;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

/** Client only. No ADB server, discovery, remote host option or background listener. */
final class LocalAdb implements AutoCloseable, SwitchEngine.Shell {
    private final Socket socket=new Socket();
    private InputStream input; private OutputStream output; private int nextId=1;
    // In-memory peer for protocol regression checks; no production caller uses this.
    LocalAdb(InputStream input,OutputStream output){this.input=input;this.output=output;}
    LocalAdb(AdbIdentity identity,Consumer<String> progress) throws Exception {
        try {
            socket.connect(new InetSocketAddress("127.0.0.1",5555),4000);
            socket.setSoTimeout(5000); socket.setTcpNoDelay(true);
            input=socket.getInputStream();output=socket.getOutputStream();
            AdbWire.write(output,AdbWire.CNXN,0x01000000,AdbWire.LIMIT,AdbWire.utf8("host::features=shell_v2;\0"));
            boolean signed=false,published=false;
            long deadline=System.nanoTime()+120_000_000_000L;
            while(true){
                if(System.nanoTime()>deadline)throw new IOException("Debugging approval timed out. Choose Connect again.");
                AdbWire.Packet packet;
                // A timeout inside a packet is fatal: continuing could lose framing.
                socket.setSoTimeout(published ? 120000 : 10000);
                packet=AdbWire.read(input);
                if(packet.command==AdbWire.CNXN){
                    String banner=new String(packet.data,java.nio.charset.StandardCharsets.UTF_8);
                    if(!AdbWire.supportsShell(packet))
                        throw new IOException("Unsupported ADB response: version="+Integer.toHexString(packet.arg0)+", maxdata="+packet.arg1+", banner="+banner.replace("\0",""));
                    progress.accept("Local debugging authorized. Checking this TV…");
                    break;
                }
                if(packet.command!=AdbWire.AUTH || packet.arg0!=1)throw new IOException("Unexpected ADB authorization response");
                if(!signed){AdbWire.write(output,AdbWire.AUTH,2,0,identity.sign(packet.data));signed=true;}
                else if(!published){
                    progress.accept("Approve Allow USB debugging on this TV. Select Always allow. Key: "+identity.fingerprint());
                    AdbWire.write(output,AdbWire.AUTH,3,0,identity.publicMessage());published=true;
                } else throw new IOException("The TV did not accept this ADB identity. Approve its prompt and reconnect.");
            }
        } catch(Exception error){close();throw error;}
    }
    @Override public AdbWire.ShellResult run(String command) throws Exception {
        int local=nextId++,remote=0;
        socket.setSoTimeout(15000);
        AdbWire.write(output,AdbWire.OPEN,local,0,AdbWire.utf8("shell,v2,raw:"+command+"\0"));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        long deadline=System.nanoTime()+30_000_000_000L;
        while(true){
            if(System.nanoTime()>deadline)throw new IOException("Command exceeded time limit");
            AdbWire.Packet packet=AdbWire.read(input);
            // protocol.txt requires ignoring packets for streams already closed.
            if(packet.arg1>0 && packet.arg1<local)continue;
            if(packet.arg1!=local)throw new IOException("Unexpected ADB stream identifier: command="+Integer.toHexString(packet.command)+", local="+local+", peer="+packet.arg0+"/"+packet.arg1);
            if(packet.command==AdbWire.OKAY && remote==0 && packet.arg0!=0){remote=packet.arg0;continue;}
            if(packet.command==AdbWire.CLSE){
                if(remote==0 || packet.arg0!=remote)throw new IOException("ADB shell service was rejected");
                // CLOSE is not a request requiring an acknowledgement.
                return AdbWire.shellResult(bytes.toByteArray());
            }
            if(packet.command!=AdbWire.WRTE || remote==0 || packet.arg0!=remote)
                throw new IOException("Unexpected ADB shell packet");
            if(bytes.size()+packet.data.length>2*1024*1024)throw new IOException("Command output exceeds limit");
            bytes.write(packet.data);
            AdbWire.write(output,AdbWire.OKAY,local,remote,new byte[0]);
        }
    }
    @Override public void close(){try{socket.close();}catch(IOException ignored){}}
}
