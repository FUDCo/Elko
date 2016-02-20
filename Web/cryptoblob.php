<?php

/**
 * Functions for handling Elko cryptoblobs.
 *
 * A cryptoblob takes the form of a string encrypted with AES, padded
 * according to the PKCS7 padding convention, then base64 encoded.  This
 * string is preceded by the initialization vector used for encryption, base64
 * encoded but with the invariant trailing "==" omitted.  Thus the 16-byte IV
 * is obtained by taking the first 22 characters of the crytoblob string,
 * appending "==", and then decoding as base64.  The plaintext is obtained by
 * taking the remaining characters of the cryptoblob string, decoding as
 * base64, then decrypting.  Crypto keys are handled in this API in the form
 * of base64 encoded raw 128 bit AES keys (i.e., 24 characters in encoded
 * form).
 */
class Cryptoblob {
    private $myKey;
    private $myCryptor;
    private $myIV;

    /**
     * Constructor.
     *
     * @param $keystr  The base64 encoded key for encryption and decryption
     */
    public function __construct($keystr) {
        $this->myKey = base64_decode($keystr);
        $this->myCryptor = mcrypt_module_open('rijndael-128', '', 'cbc', '');
    }

    /** Extract the initialization vector from a cryptoblob message. */
    private static function extractIV($msgstr) {
        return base64_decode(substr($msgstr, 0, 22) . '==');
    }

    /** Extract the cyphertext body from a cryptoblob message. */
    private static function extractCyphertext($msgstr) {
        return base64_decode(substr($msgstr, 22));
    }

    /** Remove the padding from the end of a decrypted string. */
    private static function depad($padded) {
        $padlen = ord($padded[strlen($padded) - 1]);
        $unpadded = substr($padded, 0, strlen($padded) - $padlen);
        return $unpadded;
    }

    /** Pad a string prior to encryption so it will be an integral number of
        blocks. */
    private static function pad($unpadded) {
        $padlen = 16 - (strlen($unpadded) % 16);
        $padded = $unpadded;
        for ($i = 0; $i < $padlen; ++$i) {
            $padded .= chr($padlen);
        }
        return $padded;
    }

    /**
     * Set up to perform a cryptographic operation (encryption or decryption).
     *
     * @param $iv  Iniialization vector.  If omitted or null, a random one will
     *    be generated (only do this for encryptions!)
     *
     * @return true if initialization fails, false if everything went OK
     */
    private function init($iv = null) {
        if ($iv == null) {
            $iv = mcrypt_create_iv(mcrypt_enc_get_iv_size($this->myCryptor));
        }    
        $this->myIV = $iv;
        return mcrypt_generic_init($this->myCryptor, $this->myKey, $iv) == -1;
    }

    /**
     * Clean up after a cryptographic operation.
     */
    private function deinit() {
        mcrypt_generic_deinit($this->myCryptor);
    }

    /**
     * Decrypt some data, assuming that the key and IV have been initialized.
     *
     * @param $cyphertext  The data to decrypt
     *
     * @return the decrypted form of $cyphertext
     */
    private function decrypt($cyphertext) {
        $plaintext = mdecrypt_generic($this->myCryptor, $cyphertext);
        return self::depad($plaintext);
    }

    /**
     * Decode and decrypt a cryptoblob message.
     *
     * @param $msgstr  The encoded cryptoblob
     *
     * @return the decoded and decrypted plaintext from $msgstr, or null if
     *    mcrypt could not be initialized.
     */
    public function decryptMessage($msgstr) {
        $iv = self::extractIV($msgstr);
        
        if ($this->init($iv)) {
            return null;
        }
        
        $cyphertext = self::extractCyphertext($msgstr);
        $plaintext = $this->decrypt($cyphertext);
        $this->deinit();
        return $plaintext;
    }

    /**
     * Encrypt some data, assuming that the key and IV have been initialized.
     *
     * @param $plaintext  The data to encrypt.
     *
     * @return the encrypted form of $plaintext
     */
    private function encrypt($plaintext) {
        return mcrypt_generic($this->myCryptor, self::pad($plaintext));
    }

    /**
     * Encrypt and encode a cryptoblob message.
     *
     * @param $plaintext  The plaintext to be encoded into the message body
     * @param $iv  Optional initialization vector.  If omitted (the normal use
     *    case), an IV will be generated at random
     *
     * @return the encrypted and encoded cryptoblob message string, or null if
     *    mcrypt could not be initialized.
     */
    public function encryptMessage($plaintext, $iv = null) {
        if ($this->init($iv)) {
            return null;
        }
        
        $cyphertext = $this->encrypt($plaintext);
        $cyphertextstr = base64_encode($cyphertext);
        
        $ivstr = substr(base64_encode($this->myIV), 0, 22);
        
        $msgstr = $ivstr . $cyphertextstr;
        $this->deinit();
        return $msgstr;
    }

    /**
     * Create and return a cryptoblob wrapping an object.
     *
     * @param $obj  The object to be encoded.
     * @param $duration  Optional duration the encoded blob will be good for,
     *    in seconds (default = 60).
     *
     * @return the given object, encoded as JSON with a nonce and expiration
     *    time, then encrypted with this cryptor's key and base64 encoded for
     *    transmission.
     */
    public function makeBlob($obj, $duration = 60) {
        $obj["nonce"] = base64_encode(mcrypt_create_iv(16));
        $obj["expire"] =  time() + $duration;
        $blobString = json_encode($obj);
        return $this->encryptMessage($blobString);
    }
}

?>
