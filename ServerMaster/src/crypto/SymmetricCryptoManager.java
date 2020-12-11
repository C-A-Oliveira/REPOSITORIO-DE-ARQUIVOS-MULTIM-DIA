package crypto;

import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

public final class SymmetricCryptoManager {
	private Key key;
	
	public SymmetricCryptoManager(byte[] encodedKey) {
		this(new SecretKeySpec(encodedKey, "DES"));
	}
	
	public SymmetricCryptoManager(Key key) {
		this.key = key;
	}
	
	public SymmetricCryptoManager() throws Exception {
		this(generateSymmetricKey());
	}
	
	private static Key generateSymmetricKey() throws Exception {
		KeyGenerator generator = KeyGenerator.getInstance("DES");
		generator.init(128);
		return generator.generateKey();
	}
	
	public Key getKey() {
		return this.key;
	}
	
	public void setKey(Key key) {
		this.key = key;
	}
	
	public byte[] encryptData(byte[] data) {
	    try {
	    	Cipher cipher = Cipher.getInstance(key.getAlgorithm() + "/ECB/PKCS5Padding"); 
			cipher.init(Cipher.ENCRYPT_MODE, key);
		    return cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public byte[] decryptData(byte[] data) {
		try {
	    	Cipher cipher = Cipher.getInstance(key.getAlgorithm() + "/ECB/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, key);
		    return cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
