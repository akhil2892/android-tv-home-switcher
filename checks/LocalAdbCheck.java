package dev.local.tvhomeselector;

import java.io.*;
import java.math.BigInteger;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

public final class LocalAdbCheck {
    static int passed;
    static void check(boolean result,String name){if(!result)throw new AssertionError(name);passed++;}
    interface Attempt{void run()throws Exception;}
    static void rejects(Attempt attempt,String name)throws Exception{try{attempt.run();throw new AssertionError(name);}catch(IOException|IllegalArgumentException expected){passed++;}}
    static byte[] frame(int id,byte[] payload)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(id);out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array());out.write(payload);return out.toByteArray();
    }
    static final String STOCK="com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity",CUSTOM="com.gathin.tvhome/com.gathin.tvhome.MainActivity";
    static final SwitchEngine.Profile PROFILE=new SwitchEngine.Profile("recorded-firmware","0",STOCK,"com.android.tv.settings/com.sony.dtv.settings.MainSettings","com.sony.dtv.tvx/.inputselect.InputSelectionActivity",new HashSet<>(Arrays.asList("original.disabled")));
    static final class Fake implements SwitchEngine.Shell{
        boolean stockDisabled=true,wrongIdentity,wrongUser,missingTarget,wrongResolution,missingControls,failPreference;
        int mutations;String selected=CUSTOM;
        @Override public AdbWire.ShellResult run(String command){
            String text="";int exit=0;
            if(command.equals(SwitchEngine.command("id","-u")))text="2000";
            else if(command.contains("'ro.build.fingerprint'"))text=wrongIdentity?"other-firmware":"recorded-firmware";
            else if(command.contains("'get-current-user'"))text=wrongUser?"10":"0";
            else if(command.contains("'resolve-activity'"))text=wrongResolution?STOCK:selected;
            else if(command.contains("'android.settings.SETTINGS'"))text=missingControls?"":PROFILE.settings;
            else if(command.contains("'com.android.tv.action.VIEW_INPUTS'"))text=PROFILE.inputs;
            else if(command.contains("'query-activities'"))text=missingTarget?STOCK:STOCK+"\n"+CUSTOM;
            else if(command.contains("'list' 'packages' '-d'"))text="package:original.disabled\n"+(stockDisabled?"package:"+PROFILE.stockPackage():"");
            else if(command.contains("'default-state'")){mutations++;stockDisabled=false;text="Package X new state: default";}
            else if(command.contains("'disable-user'")){mutations++;stockDisabled=true;text="Package X new state: disabled-user";}
            else if(command.contains("'set-home-activity'")){mutations++;if(failPreference){exit=1;text="Error: refused";}else{selected=command.contains(CUSTOM)?CUSTOM:STOCK;text="Success";}}
            else if(command.contains("'am' 'start'"))text="Status: ok";
            else throw new AssertionError("Unexpected command: "+command);
            return new AdbWire.ShellResult(text,"",exit);
        }
    }
    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();AdbWire.write(out,AdbWire.WRTE,2,1,new byte[]{0,1,-1});byte[] packet=out.toByteArray();
        InputStream fragmented=new FilterInputStream(new ByteArrayInputStream(packet)){@Override public int read(byte[] b,int offset,int length)throws IOException{return super.read(b,offset,Math.min(1,length));}};
        check(Arrays.equals(AdbWire.read(fragmented).data,new byte[]{0,1,-1}),"fragmented transport");
        byte[] bad=packet.clone();bad[20]^=1;rejects(()->AdbWire.read(new ByteArrayInputStream(bad)),"invalid magic");
        byte[] sum=packet.clone();sum[24]^=1;rejects(()->AdbWire.read(new ByteArrayInputStream(sum)),"invalid checksum");
        byte[] huge=packet.clone();ByteBuffer.wrap(huge).order(ByteOrder.LITTLE_ENDIAN).putInt(12,AdbWire.LIMIT+1);rejects(()->AdbWire.read(new ByteArrayInputStream(huge)),"payload limit");
        rejects(()->AdbWire.read(new ByteArrayInputStream(Arrays.copyOf(packet,25))),"truncated transport");
        ByteArrayOutputStream shell=new ByteArrayOutputStream();shell.write(frame(1,AdbWire.utf8("okay")));shell.write(frame(2,AdbWire.utf8("warning")));shell.write(frame(3,new byte[]{7}));
        AdbWire.ShellResult result=AdbWire.shellResult(shell.toByteArray());check(result.stdout.equals("okay")&&result.stderr.equals("warning")&&result.exit==7,"separate shell streams and nonzero exit");
        rejects(()->AdbWire.shellResult(frame(1,new byte[]{1})),"missing exit is not success");
        rejects(()->AdbWire.shellResult(frame(9,new byte[0])),"invalid shell frame");
        rejects(()->AdbWire.shellResult(new byte[]{1,-1,-1,-1,127}),"shell allocation limit");
        check(AdbWire.shellResult(frame(3,new byte[]{0,0,0,0})).exit==0,"four byte exit");
        check(AdbWire.supportsShell(new AdbWire.Packet(AdbWire.CNXN,0x01000001,256*1024,AdbWire.utf8("device::features=cmd,shell_v2,stat_v2;\0"))),"peer maximum version advertisement");
        check(AdbWire.supportsShell(new AdbWire.Packet(AdbWire.CNXN,0x01000000,4096,AdbWire.utf8("device::features=shell_v2;\0"))),"original checksum protocol");
        check(!AdbWire.supportsShell(new AdbWire.Packet(AdbWire.CNXN,0x01000001,4096,AdbWire.utf8("device::features=cmd;\0"))),"shell-v2 feature required");
        ByteArrayOutputStream peer=new ByteArrayOutputStream();
        for(int local=1;local<=2;local++){
            if(local==2){AdbWire.write(peer,AdbWire.CLSE,11,1,new byte[0]);AdbWire.write(peer,AdbWire.OKAY,11,1,new byte[0]);}
            AdbWire.write(peer,AdbWire.OKAY,10+local,local,new byte[0]);
            ByteArrayOutputStream reply=new ByteArrayOutputStream();reply.write(frame(1,AdbWire.utf8("command"+local)));reply.write(frame(3,new byte[]{0}));
            byte[] data=reply.toByteArray();AdbWire.write(peer,AdbWire.WRTE,10+local,local,Arrays.copyOf(data,3));
            AdbWire.write(peer,AdbWire.WRTE,10+local,local,Arrays.copyOfRange(data,3,data.length));
            AdbWire.write(peer,AdbWire.CLSE,10+local,local,new byte[0]);
        }
        ByteArrayOutputStream sent=new ByteArrayOutputStream();
        try(LocalAdb client=new LocalAdb(new ByteArrayInputStream(peer.toByteArray()),sent)){
            check(client.run("id -u").stdout.equals("command1"),"shell frame split across transport packets");
            check(client.run("getprop ro.build.fingerprint").stdout.equals("command2"),"consecutive commands ignore packets for closed stream");
        }
        ByteArrayInputStream sentInput=new ByteArrayInputStream(sent.toByteArray());boolean closeAck=false;
        while(sentInput.available()>0){if(AdbWire.read(sentInput).command==AdbWire.CLSE)closeAck=true;}
        check(!closeAck,"CLOSE does not generate a duplicate acknowledgement");
        Path directory=Files.createTempDirectory(Paths.get(args[0]),"adb-identity-check-");
        AdbIdentity identity=AdbIdentity.load(directory.toFile()),again=AdbIdentity.load(directory.toFile());
        check(Arrays.equals(identity.publicMessage(),again.publicMessage()),"identity persists");
        java.security.interfaces.RSAPrivateKey original=(java.security.interfaces.RSAPrivateKey)identity.privateKey;
        java.security.interfaces.RSAPrivateKey nonCrt=new java.security.interfaces.RSAPrivateKey(){
            public BigInteger getModulus(){return original.getModulus();}
            public BigInteger getPrivateExponent(){return original.getPrivateExponent();}
            public String getAlgorithm(){return "RSA";}
            public String getFormat(){return original.getFormat();}
            public byte[] getEncoded(){return original.getEncoded();}
        };
        check(Arrays.equals(identity.publicMessage(),AdbIdentity.fromPrivate(nonCrt).publicMessage()),"Android non-CRT reload preserves public identity");
        byte[] message=AdbWire.utf8("independent signature check"),token=MessageDigest.getInstance("SHA-1").digest(message);
        Signature verifier=Signature.getInstance("SHA1withRSA");verifier.initVerify(identity.publicKey);verifier.update(message);
        check(verifier.verify(identity.sign(token)),"AUTH signs prehashed token without double hash");
        String pub=new String(identity.publicMessage(),java.nio.charset.StandardCharsets.UTF_8).split(" ")[0];byte[] key=Base64.getDecoder().decode(pub);
        ByteBuffer words=ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN);check(words.getInt()==64&&key.length==524,"ADB public key word count");
        long inverse=words.getInt()&0xffffffffL;BigInteger modulus=identity.publicKey.getModulus(),word=BigInteger.ONE.shiftLeft(32);
        check(modulus.multiply(BigInteger.valueOf(inverse)).mod(word).equals(word.subtract(BigInteger.ONE)),"Montgomery inverse");
        byte[] mod=new byte[256];words.get(mod);for(int i=0;i<128;i++){byte b=mod[i];mod[i]=mod[255-i];mod[255-i]=b;}
        check(new BigInteger(1,mod).equals(modulus),"public modulus byte order");
        byte[] rr=new byte[256];words.get(rr);for(int i=0;i<128;i++){byte b=rr[i];rr[i]=rr[255-i];rr[255-i]=b;}
        check(new BigInteger(1,rr).equals(BigInteger.ONE.shiftLeft(4096).mod(modulus))&&words.getInt()==65537,"Montgomery RR and exponent");
        Fake fake=new Fake();new SwitchEngine(PROFILE,fake,s->{}).switchTo(STOCK);check(!fake.stockDisabled&&fake.selected.equals(STOCK),"stock switch restores package and default");
        new SwitchEngine(PROFILE,fake,s->{}).switchTo(CUSTOM);check(fake.stockDisabled&&fake.selected.equals(CUSTOM),"custom switch changes package and default");
        Fake mismatch=new Fake();mismatch.wrongIdentity=true;rejects(()->new SwitchEngine(PROFILE,mismatch,s->{}).switchTo(STOCK),"firmware mismatch");check(mismatch.mutations==0,"identity gate precedes mutations");
        Fake user=new Fake();user.wrongUser=true;rejects(()->new SwitchEngine(PROFILE,user,s->{}).switchTo(STOCK),"user mismatch");check(user.mutations==0,"user gate precedes mutations");
        Fake missing=new Fake();missing.missingTarget=true;rejects(()->new SwitchEngine(PROFILE,missing,s->{}).switchTo(CUSTOM),"removed launcher");check(missing.mutations==0,"missing candidate gate precedes mutations");
        Fake failure=new Fake();failure.failPreference=true;SwitchEngine engine=new SwitchEngine(PROFILE,failure,s->{});rejects(()->engine.switchTo(STOCK),"failed preference is not success");check(engine.mutationBegan,"failed mutation requests rollback");failure.failPreference=false;new SwitchEngine(PROFILE,failure,s->{}).restoreStock();check(failure.selected.equals(STOCK)&&!failure.stockDisabled,"recovery after failure");
        Fake resolution=new Fake();resolution.wrongResolution=true;rejects(()->new SwitchEngine(PROFILE,resolution,s->{}).switchTo(CUSTOM),"wrong effective resolution");
        Fake controls=new Fake();controls.missingControls=true;rejects(()->new SwitchEngine(PROFILE,controls,s->{}).switchTo(CUSTOM),"critical control missing");check(controls.mutations==0,"control gate precedes mutations");
        rejects(()->SwitchEngine.canonical("com.example.home/.Main; reboot"),"shell metacharacters rejected as component");
        check(SwitchEngine.command("a'b").equals("'a'\\''b'"),"POSIX argument quoting");
        System.out.println(passed+" local-ADB/crypto/switch checks passed. Temporary test identity stays in private work directory.");
    }
}
