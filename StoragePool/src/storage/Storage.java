package storage;

import java.io.*;

import java.lang.UnsupportedOperationException;
import java.text.*;
import java.util.*;
import java.util.concurrent.Semaphore;

import crypto.AsymmetricCryptoManager;
import crypto.SymmetricCryptoManager;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

public class Storage {

	//Constantes de cabecalho
	public static final byte ENVIA_ARQ_SERVER = (byte) 0x02;
	public static final byte RECEBE_ARQ_SERVER = (byte) 0x01;
	public static final byte RECEBE_REQ_SERVER = (byte) 0x05;

	public static void main(String[] args) throws IOException {
//		testAESEncryptionAndDecryption();
		// server is listening on port 33333
		ServerSocket ss = new ServerSocket(33335);

		// running infinite loop for getting client request
		boolean loop = true;
		while (loop) {
			Socket s = null;

			try {
				// socket object to receive incoming client requests
				s = ss.accept();
				byte[] cADDR = s.getInetAddress().getAddress();
				String name = String.valueOf(cADDR[0]) + "." + String.valueOf(cADDR[1]) + "." + String.valueOf(cADDR[2])
						+ "." + String.valueOf(cADDR[3]) + ":" + s.getPort();

				System.out.println("A new client is connected : " + s);

				// obtaining input and out streams
				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				System.out.println("Assigning new thread client " + name);

				// READ
				ArrayList<Byte> receivedList = new ArrayList<Byte>();
				while (true) {
					try {
						byte[] auxByte = new byte[1];
						while (dis.read(auxByte) != -1) {
							receivedList.add(new Byte(auxByte[0]));
						}
						byte[] received = new byte[receivedList.size()];
						for (int i = 0; i < receivedList.size(); i++) {
							received[i] = receivedList.get(i).byteValue();
						}

						// DIVIDINDO
						byte[][] split = splitMessage(received);

						byte[] header = split[0];
						byte[] body = split[1];

						byte mode = header[0];
						byte[] user = new byte[header.length - 1];
						System.arraycopy(header, 1, user, 0, header.length - 1);

						if (mode == RECEBE_REQ_SERVER) {
							String nomeArq = new String(body, StandardCharsets.UTF_8);

							byte[] arq = getArq(nomeArq);

							byte[] message = makeMessage(ENVIA_ARQ_SERVER, user, arq);

							dos.write(message);
						} else {
							if (mode == RECEBE_ARQ_SERVER) {
								writeArq(body);
							}
						}

					} catch (IOException e) {
						e.printStackTrace();
					}

				}
			} catch (Exception e) {
				s.close();
				e.printStackTrace();
			}
		}
		ss.close();
	}

	// ============ Metodos de mensagem ============================
	// Divide mensagem em cabecalho e corpo. retorna um array de byte[] (array de
	// array)
	public static byte[][] splitMessage(byte[] _msg) {

		int sizeHeader = Integer.BYTES + 1 + Long.BYTES;
		System.out.println("teste: " + _msg.length + " - " + sizeHeader);
		int sizeBody = _msg.length - sizeHeader;
		byte[][] splitMsg = new byte[sizeHeader][sizeBody];

		byte[] header = new byte[sizeHeader];
		byte[] body = new byte[sizeBody];

		for (int i = 0; i < header.length; i++) {
			header[i] = _msg[i];
		}

		int j = 0;
		for (int i = header.length; i < body.length; i++) {
			body[j] = _msg[i];
			j++;
		}
		j = 0;

		splitMsg[0] = header;
		splitMsg[1] = body;

		return splitMsg;
	}

	// Cria a mensagem a ser enviada, com cabecalho
	public static byte[] makeMessage(byte _mode, byte[] _bytesId, byte[] _body) {

		byte[] header = new byte[1 + Long.BYTES];
		byte[] message = new byte[header.length + _body.length];

		// Faz o header
		header[0] = _mode;
		int j = 0;
		for (int i = 1; i < header.length; i++) {
			header[i] = _bytesId[j];
			j++;
		}

		// Faz o Message
		for (int i = 0; i < header.length; i++) {
			message[i] = header[i];
		}

		j = 0;
		for (int i = header.length; i < _body.length; i++) {
			message[i] = _body[j];
			j++;
		}

		// Output
		return message;
	}

	// ===================== METODOS de arquivo ===============================
	// retorna os bytes[] de um arquivo especificado
	private static byte[] getArq(String _nome) {
		byte[] arq = null;

		File file = new File("bbb");// TODO: implement, isso eh so pra teste

		arq = readContentIntoByteArray(file);

		// throw new UnsupportedOperationException("todo, not implemented yet");
		// implement

		return arq;
	}

	// Fonte: https://howtodoinjava.com/java/io/read-file-content-into-byte-array/
	// Retorna os bytes de um arquivo
	private static byte[] readContentIntoByteArray(File file) {
		FileInputStream fileInputStream = null;
		byte[] bFile = new byte[(int) file.length()];
		try {
			// convert file into array of bytes
			fileInputStream = new FileInputStream(file);
			fileInputStream.read(bFile);
			fileInputStream.close();
			// for (int i = 0; i < bFile.length; i++) {
			// System.out.print((char) bFile[i]);
			// }
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bFile;
	}

	// Cria o arquivo
	private static void writeArq(byte[] _arq) {

		// TODO: implement, so pra teste
		// SOMENTE PARA TESTE - AQUI DEVE FICAR O CODIGO PARA GRAVACAO APROPRIADO NO
		// STORAGE
		try (FileOutputStream stream = new FileOutputStream("bbb")) {
			stream.write(_arq);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Arquivo criado.");

		// return arq;
	}

	// AES
	private static void testAESEncryptionAndDecryption() {
		try {

			// Server generates key pair
			KeyPair keyPair = AsymmetricCryptoManager.generateKeyPair();

			// Client generates symmetric key
			SymmetricCryptoManager clientManager = new SymmetricCryptoManager();
			byte[] encodedKey = clientManager.getKey().getEncoded();

			// Client request server public key
			byte[] encodedPublicKey = keyPair.getPublic().getEncoded();

			// Client encode symmetric key and send key to server
			byte[] encryptedSymmetricKey = AsymmetricCryptoManager.encryptData(encodedKey, encodedPublicKey);

			// Server decode symmetric key
			byte[] decryptedSymmetricKey = AsymmetricCryptoManager.decryptData(encryptedSymmetricKey,
					keyPair.getPrivate());

			SymmetricCryptoManager serverManager = new SymmetricCryptoManager(decryptedSymmetricKey);

			String text = "this is a very long text which would cause RSA to fail";
			byte[] bytes = text.getBytes();
			byte[] encryptedBytes = clientManager.encryptData(bytes);
			byte[] decryptedBytes = serverManager.decryptData(encryptedBytes);

			String encryptedText = new String(encryptedBytes);
			String decryptedText = new String(decryptedBytes);

			System.out.println(text);
			System.out.println(encryptedText);
			System.out.println(decryptedText);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
