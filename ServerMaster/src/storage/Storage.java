package storage;

import java.io.*;

import crypto.AsymmetricCryptoManager;
import crypto.SymmetricCryptoManager;
import server.Mensagem;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Vector;

public class Storage {

	// Constantes de cabecalho
	public static final byte ENVIA_ARQ_SERVER = (byte) 0x02;
	public static final byte RECEBE_ARQ_SERVER = (byte) 0x01;
	public static final byte RECEBE_REQ_SERVER = (byte) 0x05;

	public static void main(String[] args) throws IOException {
		BufferedReader bfr = new BufferedReader(new FileReader("storageConf.txt"));
		String line;
		Vector<String> lines = new Vector<String>();
		while ((line = bfr.readLine()) != null)
			lines.add(line);
		bfr.close();

		InetAddress serverIP = InetAddress.getByName(lines.get(0));
		InetAddress storageIP_01 = InetAddress.getByName(lines.get(2));
		InetAddress storageIP_02 = InetAddress.getByName(lines.get(4));
		int serverPort = Integer.parseInt(lines.get(1));
		int storagePort_01 = Integer.parseInt(lines.get(3));
		int storagePort_02 = Integer.parseInt(lines.get(5));

		StartStorage storage01 = new StartStorage(serverIP, serverPort, storageIP_01, storagePort_01);
		storage01.start();
		StartStorage storage02 = new StartStorage(serverIP, serverPort, storageIP_02, storagePort_02);
		storage02.start();

	}

	static class StartStorage extends Thread {

		private int serverPort;
		private int storagePort;
		private InetAddress serverIP;
		private InetAddress storageIP;
		private String ROOT_PATH;

		public StartStorage() {
		}

		public StartStorage(InetAddress serverIP, int serverPort, InetAddress storageIP, int storagePort) {
			this.serverIP = serverIP;
			this.serverPort = serverPort;
			this.storageIP = storageIP;
			this.storagePort = storagePort;
		}

		@Override
		public void run() {
			// establish the connection
			Socket s;
			try {
				s = new Socket(serverIP, serverPort, storageIP, storagePort);

				this.setName("STORAGE" + storageIP.toString() + ":" + storagePort);

				System.out.println("A new server is connected : " + s);

				// obtaining input and out streams
				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				boolean loop = true;
				while (loop) {

					try {
						// READ
						while (true) {
							try {
								// READING

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

								Mensagem msg = new Mensagem(received);
								byte mode = msg.getHeader().getMode();
								byte[] user = msg.getHeader().getBUser();
								byte[] bNomeArq = msg.getHeader().getBNome();
								byte[] body = msg.getBody();
								String nomeArq = new String(bNomeArq, StandardCharsets.UTF_8);

								// TESTE
								msg.showMessage();

								switch (mode) {
								case RECEBE_ARQ_SERVER:
									// Download
									/*
									 * 
									 */
									byte[] arq = getArq(nomeArq);

									Mensagem m = new Mensagem(ENVIA_ARQ_SERVER, user, bNomeArq, arq);
									byte[] message = m.getMessage();

									dos.write(message);
									break;

								case RECEBE_REQ_SERVER:
									
									/* GetFileOwner
									 * > setSenderStoragePath
									 * Destination
									 * */

									// TESTE
									System.out.println("> b = " + body.length);

									// TESTE
									// System.out.println(">h = " + m.getHeader().getHeader().length);

									// Upload
									writeArq(body, nomeArq);
									break;
								default:
									break;
								}
								
							} catch (EOFException a) {
								a.printStackTrace();
								break;
							} catch (IOException e) {
								e.printStackTrace();
							}

						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				s.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			// ss.close();
		}
	}

	// ===================== METODOS de arquivo ===============================

	// retorna os bytes[] de um arquivo especificado
	private static byte[] getArq(String _nome) {
		byte[] arq = null;

		File file = new File(_nome);
		arq = readContentIntoByteArray(file);

		return arq;
	}

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
	private static void writeArq(byte[] _arq, String _nomeArq) {

		try (FileOutputStream stream = new FileOutputStream(_nomeArq)) {
			stream.write(_arq);
			System.out.println("Arquivo criado. - " + _nomeArq);
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	// ===================== Metodo utiliario
	// ========================================

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
