package client;
// Java implementation for a client 

// Save file as Client.java 

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;

import server.Mensagem;

// Client class 
public class Client {
	private static long id = 4; //TODO: como passar um id diferente?

	public static final byte ENVIA_ARQ = (byte) 0x00;
	public static final byte RECEBE_ARQ = (byte) 0x03;
	public static final byte ENVIA_REQ = (byte) 0x04;

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
				byte modo = (byte)0x00;
				byte[] bytes = null;
				String nomeArq = "";
				switch (opcao) {
				case "upload":
					modo = ENVIA_ARQ;
					System.out.println("Escreva o nome do arquivo a ser enviado: ");
					nomeArq = scn.nextLine();
					bytes = getArq(nomeArq);
					break;
				case "download":
					modo = ENVIA_REQ;
					System.out.println("Escreva o nome do arquivo a ser baixado: ");
					nomeArq = scn.nextLine();
					bytes = new byte[0]; //TODO: verificar
					break;
				case "closed":
					System.out.println("Fechando conexao: " + s);
					s.close();
					System.out.println("Conexao fechada.");
					loop = false;
					break;
				}

				if (opcao != "closed") {
					Mensagem m = new Mensagem(modo, id, nomeArq, bytes);
					System.out.println("testttt : " + m.getHeader().headerSize());
					byte[] message = m.getMessage();

					System.out.println("dos write");
					dos.write(message);
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
	
	//============ METODOS UTILITARIOS =====================
	
	//Retorna os bytes[] de um long
	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(x);
		return buffer.array();
	}
	
	// Retorna os bytes[] de um int
	public static byte[] intToBytes(int i) {
		byte[] result = new byte[4];

		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i);

		return result;
	}
	
	//File -> byte[]
	public static byte[] readContentIntoByteArray(File file) {
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
	
	//Retorna os bytes[] do arquivo especificado
	public static byte[] getArq(String nomeArq) {
		byte[] b;

		File testeArq = new File(nomeArq);
		b = readContentIntoByteArray(testeArq);

		return b;
	}

}

//>>>>>> THREAD - Para recebimento dos arquivos
class ServerHandler extends Thread {
	final DataInputStream dis;
	final DataOutputStream dos;
	final Socket s;

	//Constantes do cabecalho (modo)
	public static final byte ENVIA_ARQ = (byte) 0x00;
	public static final byte RECEBE_ARQ = (byte) 0x03;
	public static final byte ENVIA_REQ = (byte) 0x04;

	public ServerHandler(Socket s, DataInputStream dis, DataOutputStream dos) {
		this.s = s;
		this.dis = dis;
		this.dos = dos;
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

				Mensagem msg = new Mensagem(received);
				byte mode = msg.getHeader().getMode();
//				byte[] user = msg.getHeader().getBUser();
//				byte[] bNomeArq = msg.getHeader().getBNome();
				byte[] body = msg.getBody();

				if (mode == RECEBE_ARQ) {
					writeArq(body);
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	//Cria o arquivo
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