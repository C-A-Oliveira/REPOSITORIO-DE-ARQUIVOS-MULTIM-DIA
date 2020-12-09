package storage;

import java.io.*;

import crypto.AsymmetricCryptoManager;
import crypto.SymmetricCryptoManager;
import server.Mensagem;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

public class Storage {

	//Constantes de cabecalho
	public static final byte ENVIA_ARQ_SERVER = (byte) 0x02;
	public static final byte RECEBE_ARQ_SERVER = (byte) 0x01;
	public static final byte RECEBE_REQ_SERVER = (byte) 0x05;

	public static void main(String[] args) throws IOException {
		String[] argumentos = new String[4];
		//Arquivo de configuracao (argumentos)
		BufferedReader bfr = new BufferedReader( new FileReader("storageConf.txt"));
		String line = bfr.readLine();
		bfr.close();
		line = line.replaceAll(" ", "");//Remove todos os espacos
		String[] split = new String[4];
		split = line.split(",");
		
		argumentos[0] = split[0];
		argumentos[1] = split[1];
		argumentos[2] = split[2];
		argumentos[3] = split[3];
		
		
		// running infinite loop for getting client request
		int sPort = Integer.parseInt(argumentos[1]);
		int cPort = Integer.parseInt(argumentos[3]);

		InetAddress sIP = InetAddress.getByName(argumentos[0]);
		InetAddress cIP = InetAddress.getByName(argumentos[2]);
		Socket s = new Socket(sIP, sPort, cIP, cPort);
		
//		byte[] cADDR = s.getInetAddress().getAddress();
//		String name = String.valueOf(cADDR[0]) + "." + String.valueOf(cADDR[1]) + "." + String.valueOf(cADDR[2])
//				+ "." + String.valueOf(cADDR[3]) + ":" + s.getPort();

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
						
						//TESTE
						msg.showMessage();
						
						if (mode == RECEBE_REQ_SERVER) {
							//Download
							byte[] arq = getArq(nomeArq);

							Mensagem m = new Mensagem(ENVIA_ARQ_SERVER, user, bNomeArq, arq);
							byte[] message = m.getMessage();

							dos.write(message);
						} else {
							if (mode == RECEBE_ARQ_SERVER) {
								
								//TESTE
								System.out.println("> b = "+body.length);
								
								//TESTE
								//System.out.println(">h = " + m.getHeader().getHeader().length);
								
								//Upload
								writeArq(body, nomeArq);
							}
						}
					}catch (EOFException a) {
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
		//ss.close();
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
	
	// ===================== Metodo utiliario ========================================
	
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
