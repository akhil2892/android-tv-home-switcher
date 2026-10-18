package dev.local.tvhomeselector;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Small, bounded implementation of the Android 9 ADB transport and shell-v2 framing. */
final class AdbWire {
    static final int CNXN=0x4e584e43, AUTH=0x48545541, OPEN=0x4e45504f,
            OKAY=0x59414b4f, WRTE=0x45545257, CLSE=0x45534c43;
    static final int LIMIT=256*1024;
    static final class Packet {
        final int command, arg0, arg1; final byte[] data;
        Packet(int command, int arg0, int arg1, byte[] data) {
            this.command=command; this.arg0=arg0; this.arg1=arg1; this.data=data;
        }
    }
    static byte[] exact(InputStream input, int length) throws IOException {
        if(length<0 || length>LIMIT) throw new IOException("ADB payload exceeds limit");
        byte[] result=new byte[length]; int offset=0;
        while(offset<length) {
            int read=input.read(result,offset,length-offset);
            if(read<0) throw new EOFException("ADB connection closed early");
            if(read==0) throw new IOException("ADB read made no progress");
            offset+=read;
        }
        return result;
    }
    static int checksum(byte[] data) { int sum=0; for(byte b:data)sum+=b&255; return sum; }
    static Packet read(InputStream input) throws IOException {
        ByteBuffer header=ByteBuffer.wrap(exact(input,24)).order(ByteOrder.LITTLE_ENDIAN);
        int command=header.getInt(), arg0=header.getInt(), arg1=header.getInt();
        int length=header.getInt(), sum=header.getInt(), magic=header.getInt();
        if(magic!=(command^0xffffffff))throw new IOException("Invalid ADB magic");
        if(command!=CNXN && command!=AUTH && command!=OPEN && command!=OKAY
                && command!=WRTE && command!=CLSE)throw new IOException("Unknown ADB packet");
        byte[] data=exact(input,length);
        if(checksum(data)!=sum)throw new IOException("Invalid ADB checksum");
        return new Packet(command,arg0,arg1,data);
    }
    static void write(OutputStream output,int command,int arg0,int arg1,byte[] data) throws IOException {
        if(data.length>LIMIT)throw new IOException("ADB payload exceeds limit");
        ByteBuffer header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length)
                .putInt(checksum(data)).putInt(command^0xffffffff);
        output.write(header.array()); output.write(data); output.flush();
    }
    static final class ShellResult {
        final String stdout, stderr; final int exit;
        ShellResult(String stdout,String stderr,int exit){this.stdout=stdout;this.stderr=stderr;this.exit=exit;}
    }
    static ShellResult shellResult(byte[] bytes) throws IOException {
        ByteArrayInputStream input=new ByteArrayInputStream(bytes);
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        int exit=-1;
        while(input.available()>0){
            byte[] header=exact(input,5);
            int length=ByteBuffer.wrap(header,1,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] payload=exact(input,length); int id=header[0]&255;
            if(exit!=-1)throw new IOException("Shell data follows exit status");
            if(id==1)out.write(payload); else if(id==2)err.write(payload);
            else if(id==3 && (length==1 || length==4)) {
                exit=length==1 ? payload[0]&255 : ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if(exit<0)throw new IOException("Invalid shell exit status");
            } else throw new IOException("Invalid shell-v2 frame");
        }
        if(exit<0)throw new IOException("Shell closed without exit status; success is unverified");
        return new ShellResult(out.toString("UTF-8"),err.toString("UTF-8"),exit);
    }
    static byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
    static boolean supportsShell(Packet packet){
        // AOSP send_connect advertises its maximum version, even when the host
        // requests the original checksum-bearing protocol (0x01000000).
        String banner=new String(packet.data,StandardCharsets.UTF_8).replace("\0","");
        return (packet.arg0==0x01000000 || packet.arg0==0x01000001)
                && packet.arg1>=4096 && banner.matches("(?s).*features=[^;]*\\bshell_v2\\b[^;]*;?.*");
    }
}
