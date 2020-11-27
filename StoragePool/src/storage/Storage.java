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
		//ServerSocket ss = new ServerSocket(33336);

		// running infinite loop for getting client request
		boolean loop = true;
		while (loop) {

			try {
				// socket object to receive incoming client requests
				int sPort = Integer.parseInt(args[1]);
				int cPort = Integer.parseInt(args[3]);
				byte[] sip = { 0, 0, 0, 0 };
				byte[] cip = { 0, 0, 0, 0 };
				String[] S = args[0].replace('.', '-').split("-");
				for (int i = 0; i < 4; i++)
					sip[i] = Byte.parseByte(S[i]);
				InetAddress sIP = InetAddress.getByAddress(sip);
				String[] C = args[2].replace('.', '-').split("-");
				for (int i = 0; i < 4; i++)
					cip[i] = Byte.parseByte(C[i]);
				InetAddress cIP = InetAddress.getByAddress(cip);
				Socket s = new Socket(sIP, sPort, cIP, cPort);
				
				byte[] cADDR = s.getInetAddress().getAddress();
				String name = String.valueOf(cADDR[0]) + "." + String.valueOf(cADDR[1]) + "." + String.valueOf(cADDR[2])
						+ "." + String.valueOf(cADDR[3]) + ":" + s.getPort();

				//System.out.println("A new client is connected : " + s);

				// obtaining input and out streams
				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				// READ
				ArrayList<Byte> receivedList = new ArrayList<Byte>();
				while (true) {
					System.out.println("inside the loop");
					try {
						// READING
						byte[] auxByte = new byte[1];

						int lenght = dis.readInt();

						byte[] received = new byte[lenght];
						// Reconstroi o int lido
						byte[] lb = intToBytes(lenght);
						for (int i = 0; i < Integer.BYTES; i++) {
							received[i] = lb[i];
						}

						byte[] buffer = new byte[lenght - Integer.BYTES];
						dis.readFully(buffer);
						int c = 0;
						for (int i = Integer.BYTES; i < lenght; i++) {
							received[i] = buffer[c];
							c++;
						}
						c = 0;
						System.out.println("_msg len = " + received.length);

						// DIVIDINDO
						byte[][] split = splitMessage(received);

						byte[] header = split[0];
						byte[] body = split[1];

						byte mode = header[Integer.BYTES];
						byte[] user = new byte[header.length - 1];
						System.arraycopy(header, 1, user, 0, header.length - 1);
						System.out.println("end of split");

						if (mode == RECEBE_REQ_SERVER) {
							System.out.println("RECEBE_REQ_SERVER");
							String nomeArq = new String(body, StandardCharsets.UTF_8);

							byte[] arq = getArq(nomeArq);

							byte[] message = makeMessage(ENVIA_ARQ_SERVER, user, arq);

							dos.write(message);
						} else {
							if (mode == RECEBE_ARQ_SERVER) {
								System.out.println("RECEBE_ARQ_SERVER");
								writeArq(body);
							}
						}

					} catch (IOException e) {
						e.printStackTrace();
					}

				}
			} catch (Exception e) {
				System.out.println(e.toString());
				e.printStackTrace();
			}
		}
		//ss.close();
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
	public static byte[] makeMessage(byte _mode, byte[] _id, byte[] _body) {

		byte[] header = new byte[Integer.BYTES + 1 + Long.BYTES];
		byte[] message = new byte[header.length + _body.length];

		// Header
		// Tamanho
		byte[] lb = intToBytes(message.length);
		for (int i = 0; i < Integer.BYTES; i++) {
			header[i] = lb[i];
		}

		// Modo
		header[Integer.BYTES] = _mode;

		// Usuario
		byte[] bytesId = _id;
		int j = 0;
		for (int i = 1 + Integer.BYTES; i < header.length; i++) {
			header[i] = bytesId[j];
			j++;
		}

		// MESSAGE
		// HEADER
		for (int i = 0; i < header.length; i++) {
			message[i] = header[i];
		}

		// BODY
		j = 0;
		for (int i = header.length; i < _body.length + header.length; i++) {
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
	
	//Metodo utiliario
	// Fonte:
	// https://stackoverflow.com/questions/1936857/convert-integer-into-byte-array-java/1936865
	// Retorna os bytes[] de um int
	public static byte[] intToBytes(int i) {
		byte[] result = new byte[4];

		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i);

		return result;
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
