package dev.local.tvhomeselector;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Base64;
import javax.crypto.Cipher;

/** An app-private ADB identity. Never imports the laptop's key. */
final class AdbIdentity {
    final PrivateKey privateKey; final RSAPublicKey publicKey;
    AdbIdentity(PrivateKey privateKey,RSAPublicKey publicKey){this.privateKey=privateKey;this.publicKey=publicKey;}
    static AdbIdentity load(File directory) throws Exception {
        File file=new File(directory,"local-adb.pk8");
        KeyFactory factory=KeyFactory.getInstance("RSA");
        PrivateKey privateKey;
        if(file.exists()) {
            if(file.length()<100 || file.length()>8192)throw new IOException("Invalid saved ADB identity");
            byte[] bytes=new byte[(int)file.length()];
            try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(bytes);}
            privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } else {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(2048,RSAKeyGenParameterSpec.F4));
            privateKey=generator.generateKeyPair().getPrivate();
            File temporary=new File(directory,"local-adb.pk8.tmp");
            try(FileOutputStream out=new FileOutputStream(temporary)) {
                out.write(privateKey.getEncoded()); out.getFD().sync();
            }
            if(!temporary.renameTo(file))throw new IOException("Could not save ADB identity");
        }
        return fromPrivate(privateKey);
    }
    static AdbIdentity fromPrivate(PrivateKey privateKey) throws Exception {
        // Android 9 may reload an RSA key as RSAPrivateKey without exposing CRT.
        // Every identity this app creates uses the explicitly fixed F4 exponent.
        if(!(privateKey instanceof RSAPrivateKey))throw new IOException("ADB key is not RSA");
        RSAPrivateKey rsa=(RSAPrivateKey)privateKey;
        if(rsa instanceof RSAPrivateCrtKey && !RSAKeyGenParameterSpec.F4.equals(((RSAPrivateCrtKey)rsa).getPublicExponent()))
            throw new IOException("Unexpected ADB key exponent");
        RSAPublicKey publicKey=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(rsa.getModulus(),RSAKeyGenParameterSpec.F4));
        if(publicKey.getModulus().bitLength()!=2048)throw new IOException("ADB key must be RSA 2048");
        return new AdbIdentity(privateKey,publicKey);
    }
    byte[] sign(byte[] token) throws Exception {
        if(token.length!=20)throw new IOException("Unexpected ADB authentication token");
        // AUTH supplies an already hashed SHA-1 token. Do not hash it again.
        byte[] digestInfo={0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        ByteArrayOutputStream input=new ByteArrayOutputStream();input.write(digestInfo);input.write(token);
        Cipher signer=Cipher.getInstance("RSA/ECB/PKCS1Padding");
        signer.init(Cipher.ENCRYPT_MODE,privateKey);
        return signer.doFinal(input.toByteArray());
    }
    private static byte[] little(BigInteger number){
        byte[] big=number.toByteArray(),result=new byte[256];
        for(int i=0;i<result.length && i<big.length;i++)result[i]=big[big.length-1-i];
        return result;
    }
    byte[] publicMessage() {
        BigInteger modulus=publicKey.getModulus(),word=BigInteger.ONE.shiftLeft(32);
        int n0=word.subtract(modulus.mod(word).modInverse(word)).intValue();
        ByteBuffer data=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(64).putInt(n0).put(little(modulus))
                .put(little(BigInteger.ONE.shiftLeft(4096).mod(modulus)))
                .putInt(publicKey.getPublicExponent().intValue());
        return AdbWire.utf8(Base64.getEncoder().encodeToString(data.array())+" tv-home-switcher@localhost\0");
    }
    String fingerprint() throws Exception {
        byte[] pub=publicMessage();
        String encoded=new String(pub,java.nio.charset.StandardCharsets.UTF_8).split(" ")[0];
        byte[] digest=MessageDigest.getInstance("MD5").digest(Base64.getDecoder().decode(encoded));
        StringBuilder result=new StringBuilder();
        for(byte b:digest){if(result.length()>0)result.append(':');result.append(String.format(java.util.Locale.ROOT,"%02X",b&255));}
        return result.toString(); // Matches the Android 9 approval dialog; not a security hash for files.
    }
}
