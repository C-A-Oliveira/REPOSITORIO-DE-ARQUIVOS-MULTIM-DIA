package server;

import java.io.*;
import java.util.concurrent.Semaphore;

import crypto.AsymmetricCryptoManager;
import crypto.SymmetricCryptoManager;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;

public class Server {

	public static void main(String[] args) throws IOException {
		//		testAESEncryptionAndDecryption();
		
		// server is listening on port 33333 and 33335
		ServerSocket ssc = new ServerSocket(33333);
		ServerSocket sst = new ServerSocket(33335);

		// running infinite loop for getting client request
		boolean loop = true;
		while (loop) {
			Socket socketC = null;
			Socket socketSt = null;

			try {
				// socket object to receive incoming client requests
				socketC = ssc.accept();
				socketSt = sst.accept();

				byte[] ccADDR = socketC.getInetAddress().getAddress();
				byte[] stADDR = socketSt.getInetAddress().getAddress();
				String nameC = String.valueOf(ccADDR[0]) + "." + String.valueOf(ccADDR[1]) + "."
						+ String.valueOf(ccADDR[2]) + "." + String.valueOf(ccADDR[3]) + ":" + socketC.getPort();
				String nameSt = String.valueOf(stADDR[0]) + "." + String.valueOf(stADDR[1]) + "."
						+ String.valueOf(stADDR[2]) + "." + String.valueOf(stADDR[3]) + ":" + socketSt.getPort();

				if (socketC != null) {
					System.out.println("A new client is connected : " + socketC);
				}
				if (socketSt != null) {
					System.out.println("A new storage is connected : " + socketSt);
				}

				// obtaining input and out streams
				DataInputStream disC = new DataInputStream(socketC.getInputStream());
				DataOutputStream dosC = new DataOutputStream(socketC.getOutputStream());//TODO: remover

				DataInputStream disSt = new DataInputStream(socketSt.getInputStream());
				//DataOutputStream dosSt = new DataOutputStream(socketSt.getOutputStream());

				System.out.println("Assigning new thread client " + nameC);
				System.out.println("Assigning new thread storage " + nameSt);
				Thread tC = new ClientHandler(disC);
				//TODO: Corrigir, cliente deve ser definido dentro da thread, em teoria
				Thread tSt = new StorageHandler(disSt, dosC);

				// create a new thread object
				tC.setName(nameC);
				tC.start();

				tSt.setName(nameSt);
				tSt.start();

			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("socket Closed");
				socketC.close();
				socketSt.close();
				break; // TESTE
			}
		}
		ssc.close();
		sst.close();
	}

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

//ClientHandler class 
class ClientHandler extends Thread {
	final DataInputStream dis;
	Semaphore semUser;
	Semaphore semArq;
	Semaphore semPort; // Desnecessario?
	
	String ipServer = "localhost"; //TODO: corrigir, deve ser passado para o Thread.
	String portaServer = "33333"; //TODO: corrigir, deve ser passado para o Thread.

	// Constantes do cabecalho
	public static final byte RECEBE_ARQ_CLIENT = (byte) 0x00;
	public static final byte RECEBE_ARQ_STORAGE = (byte) 0x02;
	public static final byte RECEBE_REQ_CLIENT = (byte) 0x04;
	public static final byte ENVIA_ARQ_STORAGE = (byte) 0x01;
	public static final byte ENVIA_ARQ_CLIENT = (byte) 0x03;
	public static final byte ENVIA_REQ_STORAGE = (byte) 0x05;

	// Constructor
	public ClientHandler(DataInputStream dis) {
		this.dis = dis;
		semUser = new Semaphore(1);
		semArq = new Semaphore(1);
	}

//	public void conexao(String ipServer, String portServer, String ipStorage, String portStorage) {
//		try {
//			// semPort.acquire();//DESNECESSARIO?
//
//
//
//			// semPort.release(); //DESNECESSARIO?
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}

	@Override
	public void run() {
		System.out.println("executando run da thread ClientHandler");
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

				//Mensagem
				Mensagem msg = new Mensagem(received);
				byte mode = msg.getHeader().getMode();
				byte[] bUser = msg.getHeader().getBUser();
				long user = bytesToLong(bUser);
				byte[] bNomeArq = msg.getHeader().getBNome();
				byte[] body = msg.getBody();

				//Logica
				if (mode == RECEBE_ARQ_CLIENT) {
					// -- UPLOAD: Client (bytes arq) -> Server -> Storage

					// Escolha do storage
					String[] splitEscolha = escolhaStorageUpload();
					String ipStorage = splitEscolha[0];
					String portaStorage = splitEscolha[1];
					
					DataInputStream stdis = null;
					DataOutputStream stdos = null;
					
					int sPort = Integer.parseInt(portaStorage);
					int cPort = Integer.parseInt(portaServer);

					InetAddress sIP;
					sIP = InetAddress.getByName(ipStorage);
					InetAddress cIP = InetAddress.getByName(ipServer);

					Socket s = new Socket(sIP, sPort, cIP, cPort);

					stdis = new DataInputStream(s.getInputStream());
					stdos = new DataOutputStream(s.getOutputStream());

					Mensagem m = new Mensagem(ENVIA_ARQ_STORAGE, bUser, bNomeArq, body);
					byte[] message = m.getMessage();

					// TESTE
					// System.out.println(">h = " + m.getHeader().getHeader().length);
					m.showMessage();


					System.out.println("writing arq to storage: " + stdos.toString());
					stdos.write(message);
				} else {
					if (mode == RECEBE_REQ_CLIENT) {
						// -- DOWNLOAD: Client (nome arq) -> Server -> Storage
						// Se o usuario possui acesso, entao envie a requisicao ao storage
						if (userTemAcesso(user, new String(body, StandardCharsets.UTF_8))) {
							Mensagem m = new Mensagem(ENVIA_REQ_STORAGE, bUser, bNomeArq, body);
							byte[] message = m.getMessage();

							// Escolha do storage
							String[] splitEscolha = escolhaStorageDownload();
							String ipStorage = splitEscolha[0];
							String portaStorage = splitEscolha[1];
							DataInputStream stdis = null;
							DataOutputStream stdos = null;
							
							int sPort = Integer.parseInt(portaStorage);
							int cPort = Integer.parseInt(portaServer);

							InetAddress sIP = InetAddress.getByName(ipStorage);
							InetAddress cIP = InetAddress.getByName(ipServer);

							Socket s = new Socket(sIP, sPort, cIP, cPort);

							stdis = new DataInputStream(s.getInputStream());
							stdos = new DataOutputStream(s.getOutputStream());

							System.out.println("writing req to storage: " + stdos.toString());
							stdos.write(message);
						}
					}
				}

				// System.out.println("received message from client " + this.socket.getPort() +
				// "! >> "
				// + new String(received, StandardCharsets.UTF_8));

			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

//		try {
//			// closing resources
//			this.dis.close();
//			this.dos.close();
//
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}// Fim do metodo run

	// Qual storage tem espaço?
	public String[] escolhaStorageUpload() {
		// TODO: implementar escolha
		String ipStorage = "localhost";
		String portStorage = "33336";

		String[] resultado = new String[2];
		resultado[0] = ipStorage;
		resultado[1] = portStorage;
		return resultado;
	}

	// Qual storage tem o arquivo? Envie a REQUISICAO para ele
	public String[] escolhaStorageDownload() {
		// TODO: implementar escolha
		String ipStorage = "localhost";
		String portStorage = "33336";

		String[] resultado = new String[2];
		resultado[0] = ipStorage;
		resultado[1] = portStorage;
		return resultado;
	}

	// ==================== USER TEM ACESSO? ======================
	public boolean userTemAcesso(long user, String arg) {
		// TODO: remover comentario do semaphore
//		try {
//			semUser.acquire();
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
		boolean ok = true; // TODO: implement, so pra teste

		// throw new UnsupportedOperationException("todo: Not implemented yet");
		// semUser.release();
		return ok;
	}

	// ========== METODOS UTILITARIOS =================

	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(x);
		return buffer.array();
	}

	public static byte[] intToBytes(int i) {
		byte[] result = new byte[4];

		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i);

		return result;
	}

	public static int bytesToInt(byte[] bytes) {
		return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8)
				| ((bytes[3] & 0xFF) << 0);
	}

	public static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getLong();
	}
}

//StorageHandler class 
class StorageHandler extends Thread {
	final DataInputStream dis;
	final DataOutputStream dos;
	// final Socket s;
	Semaphore semUser;
	Semaphore semArq;

	// Constantes do cabecalho
	public static final byte RECEBE_ARQ_CLIENT = (byte) 0x00;
	public static final byte RECEBE_ARQ_STORAGE = (byte) 0x02;
	public static final byte RECEBE_REQ_CLIENT = (byte) 0x04;
	public static final byte ENVIA_ARQ_STORAGE = (byte) 0x01;
	public static final byte ENVIA_ARQ_CLIENT = (byte) 0x03;
	public static final byte ENVIA_REQ_STORAGE = (byte) 0x05;

	// Constructor
	public StorageHandler(DataInputStream dis, DataOutputStream dos) {
		// this.s = s;
		this.dis = dis;
		this.dos = dos;
		semUser = new Semaphore(1);
		semArq = new Semaphore(1);
	}

	@Override
	public void run() {
		System.out.println("executando run da thread StorageHandler");
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

				if (mode == RECEBE_ARQ_STORAGE) {
					// -- TRANSFERENCIA: STORAGE -> Server -> Client

					Mensagem m = new Mensagem(ENVIA_ARQ_CLIENT, user, bNomeArq, body);
					byte[] message = m.getMessage();
					System.out.println("writing to cliente: " + dos.toString());
					dos.write(message);
				}

				// System.out.println("received message from client " + this.s.getPort() + "! >>
				// "
				// + new String(received, StandardCharsets.UTF_8));

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

//		try {
//			// closing resources
//			this.dis.close();
//			this.dos.close();
//
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}// Fim do metodo run

	// ==================== USER TEM ACESSO? ======================
	public boolean userTemAcesso(long user, String arg) {
		// TODO: remover comentario do semaphore
//		try {
//			semUser.acquire();
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
		boolean ok = true; // TODO: implement, so pra teste

		// throw new UnsupportedOperationException("todo: Not implemented yet");
		// semUser.release();
		return ok;
	}

	// ========== METODOS UTILITARIOS =================

	public static int bytesToInt(byte[] bytes) {
		return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8)
				| ((bytes[3] & 0xFF) << 0);
	}

	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(x);
		return buffer.array();
	}

	public static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getLong();
	}

	public static byte[] intToBytes(int i) {
		byte[] result = new byte[4];

		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i);

		return result;
	}

}