package client;
// Java implementation for a client 

// Save file as Client.java 

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;

// Client class 
public class Client {
	private static long id; // Acho que String nao possui tamanho confiavel, isso dificulta o uso de header

	public static final byte ENVIA_ARQ = (byte) 0x00;
	public static final byte RECEBE_ARQ = (byte) 0x03;
	public static final byte ENVIA_REQ = (byte) 0x04;

	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(x);
		return buffer.array();
	}

	// Fonte:
	// https://stackoverflow.com/questions/1936857/convert-integer-into-byte-array-java/1936865
	public static byte[] intToBytes(int i) {
		byte[] result = new byte[4];

		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i);

		return result;
	}

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
		for (int i = header.length; i < _body.length+header.length; i++) {
			message[i] = _body[j];
			j++;
		}

		// Output
		return message;
	}

	// Fonte: https://howtodoinjava.com/java/io/read-file-content-into-byte-array/
	private static byte[] readContentIntoByteArray(File file) {
		FileInputStream fileInputStream = null;
		byte[] bFile = new byte[(int) file.length()];
		try {
			// convert file into array of bytes
			if (file.exists()) {
				fileInputStream = new FileInputStream(file);
				fileInputStream.read(bFile);
				fileInputStream.close();
				// for (int i = 0; i < bFile.length; i++) {
//					System.out.print((char) bFile[i]);
				// }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bFile;
	}

	public static byte[] getArq(String nomeArq) {
		byte[] b;

		File testeArq = new File(nomeArq);
		b = readContentIntoByteArray(testeArq);

		return b;
	}

	public static void main(String[] args) throws IOException {
		try {
			Scanner scn = new Scanner(System.in);

			// getting localhost ip

			// InetAddress ip = InetAddress.getByName("localhost");
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

			// establish the connection
			Socket s = new Socket(sIP, sPort, cIP, cPort);

			// obtaining input and out streams
			DataInputStream dis = new DataInputStream(s.getInputStream());
			DataOutputStream dos = new DataOutputStream(s.getOutputStream());

			// Thread
			Thread t = new ServerHandler(s, dis, dos);
			byte[] address = sIP.getAddress();
			String name = String.valueOf(address[0]) + "." + String.valueOf(address[1]) + "."
					+ String.valueOf(address[2]) + "." + String.valueOf(address[3]) + ":" + s.getPort();
			t.setName(name);
			System.out.println("Iniciando Thread para recebimento de arquivos");
			t.start();

			boolean loop = true;
			while (loop) {

				System.out.println("===SELECAO DE OPCAO===");
				System.out.println("Digite 'upload' para enviar um arquivo.");
				System.out.println("Digite 'download' para baixar um arquivo.");
				System.out.println("Digite 'sair' para sair.");
				String opcao = scn.nextLine();
				byte modo = 0x00;
				byte[] bytes = null;

				switch (opcao) {
				case "upload":
					modo = ENVIA_ARQ;
					System.out.println("Escreva o nome do arquivo a ser enviado: ");
					String upNomeArq = scn.nextLine();
					bytes = getArq(upNomeArq);
					break;
				case "download":
					modo = ENVIA_REQ;
					System.out.println("Escreva o nome do arquivo a ser baixado: ");
					String nomeArq = scn.nextLine();
					bytes = nomeArq.getBytes(StandardCharsets.UTF_8);
					break;
				case "closed":
					System.out.println("Fechando conexao: " + s);
					s.close();
					System.out.println("Conexao fechada.");
					loop = false;
					break;
				}

				if (opcao != "closed") {
					byte[] message = makeMessage(modo, id, bytes);

					System.out.println("dos write");
					dos.write(message);

					// printing date or time as requested by client
					// String received = dis.readUTF();
					// System.out.println(received);
				}
			}
			// closing resources
			scn.close();
			dis.close();
			dos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

//Para recebimento dos arquivos
class ServerHandler extends Thread {
	final DataInputStream dis;
	final DataOutputStream dos;
	final Socket s;

	public static final byte ENVIA_ARQ = (byte) 0x00;
	public static final byte RECEBE_ARQ = (byte) 0x03;
	public static final byte ENVIA_REQ = (byte) 0x04;

	public ServerHandler(Socket s, DataInputStream dis, DataOutputStream dos) {
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

	@Override
	public void run() {
		ArrayList<Byte> receivedList = new ArrayList<Byte>();
		while (true) {
			try {
				// Ask user what he wants

				// receive the answer from client
				// received = dis.readUTF();

				// READING
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

				if (mode == RECEBE_ARQ) {
					writeArq(body);
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void writeArq(byte[] arq) {
		// TODO: Colocar nome
		try (FileOutputStream stream = new FileOutputStream("aaa")) {
			stream.write(arq);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Arquivo criado.");
	}

}