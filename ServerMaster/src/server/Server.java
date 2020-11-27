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
		// server is listening on port 33333
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

				if(socketC != null) {
					System.out.println("A new client is connected : " + socketC);
				}
				if(socketSt != null) {
					System.out.println("A new storage is connected : " + socketSt);
				}

				// obtaining input and out streams
				DataInputStream disC = new DataInputStream(socketC.getInputStream());
				DataOutputStream dosC = new DataOutputStream(socketC.getOutputStream());

				DataInputStream disSt = new DataInputStream(socketSt.getInputStream());
				DataOutputStream dosSt = new DataOutputStream(socketSt.getOutputStream());

				System.out.println("Assigning new thread client " + nameC);
				System.out.println("Assigning new thread storage " + nameSt);
				Thread tC = new ClientHandler( disC, dosSt);
				Thread tSt = new StorageHandler( disSt, dosC);

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
	final DataOutputStream dos;
	//final Socket socket;
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
	public ClientHandler(DataInputStream dis, DataOutputStream dos) {
		//this.socket = s;
		this.dis = dis;
		this.dos = dos;
		semUser = new Semaphore(1);
		semArq = new Semaphore(1);
	}

	@Override
	public void run() {
		while (true) {
			try {

				// READING

				int lenght = dis.readInt();

				byte[] received = new byte[lenght];
				
//				// Reconstroi o int lido
//				byte[] lb = intToBytes(lenght);
//				for (int i = 0; i < Integer.BYTES; i++) {
//					received[i] = lb[i];
//				}

//				byte[] buffer = new byte[lenght - Integer.BYTES];
				//dis.readFully(buffer);
				
				dis.readFully(received);
				System.out.println("received = "+ received.length);
				
//				int c = 0;
//				for (int i = Integer.BYTES; i < lenght; i++) {
//					received[i] = buffer[c];
//					c++;
//				}
//				c = 0;
				
				Mensagem msg = new Mensagem(received);
				System.out.println("headdd = " + msg.getHeader().headerSize());
				byte mode = msg.getHeader().getMode();
				byte[] user = msg.getHeader().getBUser();
				byte[] bNomeArq = msg.getHeader().getBNome();
				byte[] body = msg.getBody();

				if (mode == RECEBE_ARQ_CLIENT) {
					// -- UPLOAD: Client (bytes arq) -> Server -> Storage
					System.out.println("Recebendo arquivo do cliente");

					Mensagem m = new Mensagem(ENVIA_ARQ_STORAGE, user, bNomeArq, body);
					byte[] message = m.getMessage();
					dos.write(message);

				} else {
					if (mode == RECEBE_REQ_CLIENT) {
						// -- DOWNLOAD: Client (nome arq) -> Server -> Storage
						System.out.println("Recebendo requisicao do cliente");

						// Se o usuario possui acesso, entao envie a requisicao ao storage
						if (userTemAcesso(bytesToLong(user), new String(body, StandardCharsets.UTF_8))) {
							Mensagem m = new Mensagem(ENVIA_REQ_STORAGE, user, bNomeArq, body);
							byte[] message = m.getMessage();
							dos.write(message);
						}
					}
				}

				//System.out.println("received message from client " + this.socket.getPort() + "! >> "
						//+ new String(received, StandardCharsets.UTF_8));

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
	     return ((bytes[0] & 0xFF) << 24) | 
	            ((bytes[1] & 0xFF) << 16) | 
	            ((bytes[2] & 0xFF) << 8 ) | 
	            ((bytes[3] & 0xFF) << 0 );
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
	//final Socket s;
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
	public StorageHandler( DataInputStream dis, DataOutputStream dos) {
		//this.s = s;
		this.dis = dis;
		this.dos = dos;
		semUser = new Semaphore(1);
		semArq = new Semaphore(1);
	}

	@Override
	public void run() {
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
				System.out.println("headddd: "+ msg.getHeader().headerSize());
				byte mode = msg.getHeader().getMode();
				byte[] user = msg.getHeader().getBUser();
				byte[] bNomeArq = msg.getHeader().getBNome();
				byte[] body = msg.getBody();

				if (mode == RECEBE_ARQ_STORAGE) {
					// -- TRANSFERENCIA: STORAGE -> Server -> Client
					System.out.println("Recebendo arquivo do storage");
					
					Mensagem m = new Mensagem(ENVIA_ARQ_CLIENT, user, bNomeArq, body);
					byte[] message = m.getMessage();
					dos.write(message);
				}

				//System.out.println("received message from client " + this.s.getPort() + "! >> "
						//+ new String(received, StandardCharsets.UTF_8));

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
	     return ((bytes[0] & 0xFF) << 24) | 
	            ((bytes[1] & 0xFF) << 16) | 
	            ((bytes[2] & 0xFF) << 8 ) | 
	            ((bytes[3] & 0xFF) << 0 );
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