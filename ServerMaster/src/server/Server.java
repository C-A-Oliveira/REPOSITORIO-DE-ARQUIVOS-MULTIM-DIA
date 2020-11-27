package server;

import java.io.*;
import java.text.*;
import java.util.*;
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
				System.out.println("teste1");
				socketC = ssc.accept();
				System.out.println("teste2");
				socketSt = sst.accept();
				System.out.println("teste3");
				
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

				//TODO: simplificar esse processo...
				byte mode = header[Integer.BYTES];
				System.out.println("modo = " + mode);
				byte[] user = new byte[Long.BYTES];
				System.arraycopy(header, Integer.BYTES + 1, user, 0, user.length);
				byte[] bTamNome = new byte[Integer.BYTES]; 
				//FIX
				System.arraycopy(header, Integer.BYTES + 1 + Long.BYTES, bTamNome, 0, bTamNome.length);
				int tamNome = bytesToInt(bTamNome);
				byte[] bNomeArq = new byte[tamNome];
				System.arraycopy(header, Integer.BYTES + 1 + Long.BYTES + Integer.BYTES, bNomeArq, 0, bNomeArq.length);

				if (mode == RECEBE_ARQ_CLIENT) {
					// -- UPLOAD: Client (bytes arq) -> Server -> Storage
					System.out.println("Recebendo arquivo do cliente");

					byte[] message = makeMessage(ENVIA_ARQ_STORAGE, user, bNomeArq, body);
					dos.write(message);

				} else {
					if (mode == RECEBE_REQ_CLIENT) {
						// -- DOWNLOAD: Client (nome arq) -> Server -> Storage
						System.out.println("Recebendo requisicao do cliente");

						// Se o usuario possui acesso, entao envie a requisicao ao storage
						if (userTemAcesso(bytesToLong(user), new String(body, StandardCharsets.UTF_8))) {
							byte[] message = makeMessage(ENVIA_REQ_STORAGE, user, bNomeArq, body);
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

	// ============== MAKE MESSAGE METODOS ====================
	public static byte[] makeMessage(byte _mode, byte[] _id, byte[] _nome, byte[] _body) {

		byte[] header = new byte[Integer.BYTES + 1 + Long.BYTES + Integer.BYTES];
		byte[] message = new byte[header.length + _body.length];
		
		int k = 0;
		
		// Header
		// Tamanho
		byte[] lb = intToBytes(message.length);
		for (int i = k; i < Integer.BYTES; i++) {
			header[i] = lb[i];
			k++;
		}

		// Modo
		header[k++] = _mode;

		// Usuario
		byte[] bytesId = _id;
		int j = 0;
		for (int i = k; i < bytesId.length; i++) {
			header[i] = bytesId[j];
			j++;
			k++;
		}
		
		j=0;
		
		//Tamanho Nome do arq
		byte[] bytesNome = _nome;
		byte[] nlb = intToBytes( bytesNome.length );
		for (int i = k; i < Integer.BYTES; i++) {
			header[i] = nlb[j];
			j++;
			k++;
		}
		
		j=0;
		
		//Nome do arq
		for(int i=k;i<bytesNome.length;i++) {
			header[i] = bytesNome[j];
			j++;
			k++;
		}
		

		// MESSAGE - Concatena header e body
		// Header
		for (int i = 0; i < header.length; i++) {
			message[i] = header[i];
		}

		// Body
		j = 0;
		for (int i = header.length; i < _body.length + header.length; i++) {
			message[i] = _body[j];
			j++;
		}


		return message;
	}

	public static byte[] makeMessage(byte _mode, long _id, String _nome, byte[] _body) {

		byte[] header = new byte[Integer.BYTES + 1 + Long.BYTES + Integer.BYTES];
		byte[] message = new byte[header.length + _body.length];
		
		int k = 0;
		
		// Header
		// Tamanho
		byte[] lb = intToBytes(message.length);
		for (int i = k; i < Integer.BYTES; i++) {
			header[i] = lb[i];
			k++;
		}

		// Modo
		header[k++] = _mode;

		// Usuario
		byte[] bytesId = longToBytes(_id);
		int j = 0;
		for (int i = k; i < bytesId.length; i++) {
			header[i] = bytesId[j];
			j++;
			k++;
		}
		
		j=0;
		
		//Tamanho Nome do arq
		byte[] bytesNome = _nome.getBytes(StandardCharsets.UTF_8);
		byte[] nlb = intToBytes( bytesNome.length );
		for (int i = k; i < Integer.BYTES; i++) {
			header[i] = nlb[j];
			j++;
			k++;
		}
		
		j=0;
		
		//Nome do arq
		for(int i=k;i<bytesNome.length;i++) {
			header[i] = bytesNome[j];
			j++;
			k++;
		}
		

		// MESSAGE - Concatena header e body
		// Header
		for (int i = 0; i < header.length; i++) {
			message[i] = header[i];
		}

		// Body
		j = 0;
		for (int i = header.length; i < _body.length + header.length; i++) {
			message[i] = _body[j];
			j++;
		}


		return message;
	}

	// Retorna um array de byte[] (array de array), o primeiro eh o cabecalho e o
	// segundo eh o corpo da mensagem
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
				System.out.println("modo = " + mode);
				byte[] user = new byte[header.length - 1];
				System.arraycopy(header, 1, user, 0, header.length - 1);

				if (mode == RECEBE_ARQ_STORAGE) {
					// -- TRANSFERENCIA: STORAGE -> Server -> Client
					System.out.println("Recebendo arquivo do storage");

					byte[] message = makeMessage(ENVIA_ARQ_CLIENT, user, body);
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

	// ============== MAKE MESSAGE METODOS ====================
	public static byte[] makeMessage(byte _mode, long _id, byte[] _body) {

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
		byte[] bytesId = longToBytes(_id);
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

	// Retorna um array de byte[] (array de array), o primeiro eh o cabecalho e o
	// segundo eh o corpo da mensagem
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

	public static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getLong();
	}
}