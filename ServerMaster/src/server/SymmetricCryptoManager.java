package server;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class SymmetricCryptoManager implements Encryptor, Decriptor {
	
	private SecretKey key;
	
	public SymmetricCryptoManager(SecretKey key) {
		this.key = key;
	}
	
	public SymmetricCryptoManager() throws NoSuchAlgorithmException {
		SecureRandom randomSeed = new SecureRandom();
		KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
		keyGenerator.init(256, randomSeed);
		this.key = keyGenerator.generateKey();
	}
	
	public byte[] encryptData(byte[] data) {
	    try {
	    	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, key);
		    return cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public byte[] decryptData(byte[] data) {
		try {
	    	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, key);
		    return cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
