package storage;

import java.io.*;
import java.text.*;
import java.util.*;

import crypto.AsymmetricCryptoManager;
import crypto.SymmetricCryptoManager;

import java.net.*;
import java.security.*;

public class Storage {
	public static void main(String[] args) throws IOException {
//		testAESEncryptionAndDecryption();
		// server is listening on port 33333
		ServerSocket ss = new ServerSocket(33335);

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

	@Override
	public void run() {
		String received;
		String toreturn;
		while (true) {
			try {

				// Ask user what he wants
				dos.writeUTF("What's your message?");

				// receive the answer from client
				received = dis.readUTF();

				if (received.equals("Exit")) {
					System.out.println("Client " + this.s + " sends exit...");
					System.out.println("Closing this connection.");
					this.s.close();
					System.out.println("Connection closed");
					break;
				}

				System.out.println("received message from client" + this.s.getPort() + "! >> " + received);
				dos.writeUTF("server response: received message from client! >> " + received);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			// closing resources
			this.dis.close();
			this.dos.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}