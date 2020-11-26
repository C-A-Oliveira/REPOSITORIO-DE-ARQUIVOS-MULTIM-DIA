package server;

import java.io.*;
import java.text.*;
import java.util.*;

import crypto.AsymmetricCryptoManager;
import crypto.SymmetricCryptoManager;

import java.net.*;
import java.nio.ByteBuffer;
import java.security.*;


public class Server {
	
	public static void main(String[] args) throws IOException {
//		testAESEncryptionAndDecryption();
		// server is listening on port 33333
		ServerSocket ss = new ServerSocket(33333);

		// running infinite loop for getting client request
		while (true) {
			Socket s = null;

			try {
				// socket object to receive incoming client requests
				s = ss.accept();
				byte[] cADDR = s.getInetAddress().getAddress();
				String name = String.valueOf(cADDR[0]) + "." 
							+ String.valueOf(cADDR[1]) + "." 
							+ String.valueOf(cADDR[2]) + "." 
							+ String.valueOf(cADDR[3]) + ":"
							+ s.getPort();

				System.out.println("A new client is connected : " + s);

				// obtaining input and out streams
				DataInputStream dis = new DataInputStream(s.getInputStream());
				DataOutputStream dos = new DataOutputStream(s.getOutputStream());

				System.out.println("Assigning new thread client " + name);
				Thread t = new ClientHandler(s, dis, dos);

				// create a new thread object
				t.setName(name);
				t.start();

			} catch (Exception e) {
				s.close();
				e.printStackTrace();
			}
		}
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
			byte[] decryptedSymmetricKey = AsymmetricCryptoManager.decryptData(encryptedSymmetricKey, keyPair.getPrivate());
			
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

//ClientHandler class 
class ClientHandler extends Thread {
	final DataInputStream dis;
	final DataOutputStream dos;
	final Socket s;

	// Constructor
	public ClientHandler(Socket s, DataInputStream dis, DataOutputStream dos) {
		this.s = s;
		this.dis = dis;
		this.dos = dos;
	}
	
	public static byte[][] splitMessage(byte[] _msg) {
		
		int sizeHeader = 1 + Long.BYTES;
		int sizeBody = _msg.length - sizeHeader;
		byte[][] splitMsg = new byte[sizeHeader][sizeBody];
		
		byte[] header = new byte[sizeHeader];
		byte[] body = new byte[sizeBody];
		
		for(int i=0;i<header.length;i++) {
			header[i] = _msg[i];
		}
		
		int j=0;
		for(int i=header.length;i<body.length;i++) {
			body[j] = _msg[i];
			j++;
		}
		j=0;
		
		
		splitMsg[0] = header;
		splitMsg[1] = body;
		
		return splitMsg;
	}

	public static byte[] makeMessage(byte _mode, long _id, byte[] _body) {
	    	
	        byte[] header = new byte[1+Long.BYTES];
	        byte[] message = new byte[header.length + _body.length];
	        
	        //Faz o header
	        header[0] = _mode;
	        byte[] bytesId = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(_id).array();
	        int j = 0;
	        for(int i=1;i<header.length;i++) {
	        	header[i] = bytesId[j];
	        	j++;
	        }
	        
	        //Faz o Message
	        for(int i=0;i<header.length;i++) {
	        	message[i] = header[i];
	        }
	        
	        j = 0;
	        for(int i=header.length;i<_body.length;i++) {
	        	message[i] = _body[j];
	        	j++;
	        }
	        
	        //Output
	        return message;
	       	}
	
	public static byte[] makeMessage(byte _mode, byte[] _bytesId, byte[] _body) {
    	
        byte[] header = new byte[1+Long.BYTES];
        byte[] message = new byte[header.length + _body.length];
        
        //Faz o header
        header[0] = _mode;
        int j = 0;
        for(int i=1;i<header.length;i++) {
        	header[i] = _bytesId[j];
        	j++;
        }
        
        //Faz o Message
        for(int i=0;i<header.length;i++) {
        	message[i] = header[i];
        }
        
        j = 0;
        for(int i=header.length;i<_body.length;i++) {
        	message[i] = _body[j];
        	j++;
        }
        
        //Output
        return message;
       	}
	
	@Override
	public void run() {
		//String received;
		ArrayList<Byte> receivedList = new ArrayList<Byte>();
		String toreturn;
		while (true) {
			try {

				// Ask user what he wants
				dos.writeUTF("What's your message?");

				// receive the answer from client
				//received = dis.readUTF();
				
				//READING
				int read = 0;
				byte[] auxByte = new byte[1];
				while( dis.read(auxByte) != -1) {
					receivedList.add(new Byte(auxByte[0]));
				}
				byte[] received = new byte[receivedList.size()];
				for(int i =0;i<receivedList.size();i++) {
					received[i] = receivedList.get(i).byteValue();
				}
				
				//DIVIDINDO
				byte[][] split = splitMessage(received);
			
				byte[] header = split[0];
				byte[] body = split[1];
				
				byte mode = header[0];
				byte[] user = new byte[header.length-1];
				System.arraycopy(header, 1, user, 0, header.length-1);
				
				//TODO: verificar se user tem acesso
				
				if(header[0] == (byte)0x00) {
					//UPLOAD
					
					byte[] message = makeMessage( (byte)0x02, user, body);
					
					
					
				}else {
					if(header[0] == (byte)0x01) {
						//DOWNLOAD
						
					}
				}
				
				

//				if (received.equals("Exit")) {
//					System.out.println("Client " + this.s + " sends exit...");
//					System.out.println("Closing this connection.");
//					this.s.close();
//					System.out.println("Connection closed");
//					break;
//				}

				System.out.println("received message from client" + this.s.getPort() + "! >> " + received.toString());
				dos.writeUTF("server response: received message from client! >> " + received.toString());
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
	}
}