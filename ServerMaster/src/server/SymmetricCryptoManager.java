package server;

import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public final class SymmetricCryptoManager implements Encryptor, Decryptor {
	private Key key;
	private byte[] iv;
	
	public SymmetricCryptoManager(Key key) {
		this.key = key;
		this.iv = generateIV();
	}
	
	public SymmetricCryptoManager() throws Exception {
		this(generateSymmetricKey());
	}
	
	private static Key generateSymmetricKey() throws Exception {
		KeyGenerator generator = KeyGenerator.getInstance("AES");
		SecretKey key = generator.generateKey();
		return key;
	}
	
	private static byte[] generateIV() {
		SecureRandom random = new SecureRandom();
		byte[] iv = new byte [16];
		random.nextBytes(iv);
		return iv;
	}
	
	public Key getKey() {
		return this.key;
	}
	
	public void setKey(Key key) {
		this.key = key;
	}
	
	public byte[] encryptData(byte[] data) {
	    try {
	    	Cipher cipher = Cipher.getInstance(key.getAlgorithm() + "/CBC/PKCS5Padding"); 
			cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
		    return cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public byte[] decryptData(byte[] data) {
		try {
	    	Cipher cipher = Cipher.getInstance(key.getAlgorithm() + "/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
		    return cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
