package org.elkoserver.util.test;

import org.elkoserver.foundation.json.Cryptor;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import java.io.IOException;
import java.security.SecureRandom;

public class CryptoTest {
    private static SecureRandom theRandom = new SecureRandom();

    private static void usage() {
        System.out.println("usage: java org.elkoserver.util.test.CryptoTest [key KEY] [plaintext PLAINTEXT] [cyphertext CYPHERTEXT] [nonce TIMEOUT USERNAME] [help]");
        System.exit(0);
    }

    private static final int CHAR_BASE = 0x20;
    private static final int CHAR_TOP  = 0x7e;
    private static final int CHAR_RANGE = CHAR_TOP - CHAR_BASE;
    private static final int NONCE_LENGTH = 20;

    private static String makeNonce(String timeout, String userName)
    {
        JSONLiteral nonce = new JSONLiteral();
        StringBuilder idstr = new StringBuilder(NONCE_LENGTH);
        for (int i = 0; i < NONCE_LENGTH; ++i) {
            idstr.append((char) (CHAR_BASE + theRandom.nextInt(CHAR_RANGE)));
        }
        nonce.addParameter("nonce", idstr.toString());
        nonce.addParameter("expire", System.currentTimeMillis() / 1000 + Integer.parseInt(timeout));
        JSONLiteral user = new JSONLiteral("user", EncodeControl.forClient);
        user.addParameter("name", userName);
        user.finish();
        nonce.addParameter("user", user);
        nonce.finish();
        return nonce.sendableString();
    }

    public static void main(String[] args) {
        String keyStr = null;
        String plainText = "The crow flies at midnight";
        String cypherText = null;

        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("key")) {
                keyStr = args[++i];
            } else if (args[i].equals("plaintext")) {
                plainText = args[++i];
            } else if (args[i].equals("cyphertext")) {
                cypherText = args[++i];
            } else if (args[i].equals("nonce")) {
                String timeout = args[++i];
                String user = args[++i];
                plainText = makeNonce(timeout, user);
            } else if (args[i].equals("help")) {
                usage();
            } else {
                System.out.println("don't recognize arg #" + i + " '" +
                                   args[i] + "'");
                usage();
            }
        }

        if (keyStr == null) {
            keyStr = Cryptor.generateKey();
        } 

        Cryptor cryptor = null;
        try {
            cryptor = new Cryptor(keyStr);
        } catch (IOException e) {
            System.out.println("problem initializing Cryptor: " + e);
            System.exit(1);
        }

        if (cypherText != null) {
            try {
                plainText = cryptor.decrypt(cypherText);
            } catch (IOException e) {
                System.out.println("problem decrypting cyphertext: " + e);
                System.exit(2);
            }
        } else {
            cypherText = cryptor.encrypt(plainText);
        }
        
        System.out.println("Cyphertext: " + cypherText);
        System.out.println("Plaintext: " + plainText);
        System.out.println("Key: " + keyStr);
        System.out.println();
    }
}

