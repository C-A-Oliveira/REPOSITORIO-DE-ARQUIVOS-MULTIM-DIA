package server;

import java.io.*;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;

import crypto.AsymmetricCryptoManager;
import crypto.SymmetricCryptoManager;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;

class ServerImplementation {

	public static final byte RECEBE_ARQ_CLIENT = (byte) 0x00;
	public static final byte RECEBE_ARQ_STORAGE = (byte) 0x02;
	public static final byte RECEBE_REQ_CLIENT = (byte) 0x04;
	public static final byte ENVIA_ARQ_STORAGE = (byte) 0x01;
	public static final byte ENVIA_ARQ_CLIENT = (byte) 0x03;
	public static final byte ENVIA_REQ_STORAGE = (byte) 0x05;
	
	public static final String nomeArqUser = "arqUser.txt";

	// TODO: Usar ConcurrentHashMap inves de HashTable? HashTable possui metodos sincronizados
	// Mapas usados para manter os DataOutputStream de todos os storages e clients para que nao haja necessidade de criar mais sockets
	public static Hashtable<String, DataOutputStream> mapDOSStorage = new Hashtable<>();
	public static Hashtable<String, DataOutputStream> mapDOSClient = new Hashtable<>();

	public static Hashtable<String, String> mapClientArq = new Hashtable<>();

	public ServerImplementation(String[] args) throws IOException {
		this.main(args);
	}

	public void main(String[] args) throws IOException {
		ClientListener clientListener = new ClientListener();
		StorageListener storageListener = new StorageListener();

		clientListener.start();
		storageListener.start();

	}

	class ClientListener extends Thread {
		public ClientListener() {

		}

		@Override
		public void run() {
			try {
				// testAESEncryptionAndDecryption();

				// server is listening on port 33333 and 33335
				ServerSocket ssc = new ServerSocket(33333);

				// running infinite loop for getting client request
				boolean loop = true;
				while (loop) {
					Socket socketC = null;

					try {

						socketC = ssc.accept();

						byte[] ccADDR = socketC.getInetAddress().getAddress();

						String nameC = String.valueOf(ccADDR[0]) + "." + String.valueOf(ccADDR[1]) + "."
								+ String.valueOf(ccADDR[2]) + "." + String.valueOf(ccADDR[3]) + ":" + socketC.getPort();

						if (socketC != null) {
							System.out.println("A new client is connected : " + socketC);
						}

						// obtaining input and out streams
						DataInputStream disC = new DataInputStream(socketC.getInputStream());
						DataOutputStream dosC = new DataOutputStream(socketC.getOutputStream());// TODO: remover

						mapDOSClient.put(getIpSocket(socketC), dosC);

						System.out.println("Assigning new thread client " + nameC);

						String ipClient = socketC.getInetAddress().toString().substring(1);
						Thread tC = new ClientHandler(disC, ipClient);

						// create a new thread object
						tC.setName(nameC);
						tC.start();

					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("socket Closed");
						socketC.close();
						break; // TESTE
					}
				}
				// ssc.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	class StorageListener extends Thread {

		public StorageListener() {
		}

		@Override
		public void run() {
			try {
				ServerSocket sst = new ServerSocket(33335);

				boolean loop = true;
				while (loop) {
					Socket socketSt = null;
					try {
						socketSt = sst.accept();
						byte[] stADDR = socketSt.getInetAddress().getAddress();
						String nameSt = String.valueOf(stADDR[0]) + "." + String.valueOf(stADDR[1]) + "."
								+ String.valueOf(stADDR[2]) + "." + String.valueOf(stADDR[3]) + ":"
								+ socketSt.getPort();
						if (socketSt != null) {
							System.out.println("A new storage is connected : " + socketSt);
						}

						DataInputStream disSt = new DataInputStream(socketSt.getInputStream());
						DataOutputStream dosSt = new DataOutputStream(socketSt.getOutputStream());

						// TODO: Alterar getIpSocket para retornar IP *e* porta
						mapDOSStorage.put(getIpSocket(socketSt), dosSt);

						System.out.println("Assigning new thread storage " + nameSt);
						Thread tSt = new StorageHandler(disSt);

						tSt.setName(nameSt);
						tSt.start();
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("socket Closed");
						socketSt.close();
						break; // TESTE
					}
				}
			} catch (IOException io) {
				io.printStackTrace();
			}
		}
	}

// ClientHandler class
	class ClientHandler extends Thread {
		final DataInputStream dis;
		Semaphore semUser;
		Semaphore semArq;
		Semaphore semPort; // Desnecessario?

//		final String ipServer;
//		final String portaServer;

		public String ipClient;

		// Constructor
		public ClientHandler(DataInputStream dis, String ipClient) {
			this.dis = dis;
			semUser = new Semaphore(1);
			semArq = new Semaphore(1);
			this.ipClient = ipClient;
//			this.ipServer = ipServer;
//			this.portaServer = portaServer;
		}

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

					// Mensagem
					Mensagem msg = new Mensagem(received);
					byte mode = msg.getHeader().getMode();
					byte[] bUser = msg.getHeader().getBUser();
					long user = bytesToLong(bUser);
					byte[] bNomeArq = msg.getHeader().getBNome();
					byte[] body = msg.getBody();

					// Logica
					if (mode == RECEBE_ARQ_CLIENT) {
						// -- UPLOAD: Client (bytes arq) -> Server -> Storage

						// Escolha do storage
						// TODO: alterar escolhaStorageUpload e escolhaStorageDownload para retornar um unico string com ip e porta
						String[] splitEscolha = escolhaStorageUpload();
						String ipStorage = splitEscolha[0];
						String portaStorage = splitEscolha[1];

						DataOutputStream stdos = null;
						stdos = mapDOSStorage.get(ipStorage);

						Mensagem m = new Mensagem(ENVIA_ARQ_STORAGE, bUser, bNomeArq, body);
						byte[] message = m.getMessage();

						m.showMessage();

						System.out.println("writing arq to storage: " + stdos.toString());
						stdos.write(message);
						// s.close();
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
								// String portaStorage = splitEscolha[1];

								DataOutputStream stdos = null;
								stdos = mapDOSStorage.get(ipStorage);

								mapClientArq.put(m.getHeader().getNome(), this.ipClient);

								System.out.println("writing req to storage: " + stdos.toString());
								stdos.write(message);

							}
						}
					}
				} catch (SocketException se) {
					se.printStackTrace();
					break;
				} catch (IOException e) {
					e.printStackTrace();
				}
			} // Fim do while
		}// Fim do metodo run

		// Qual storage deve receber o arquivo?
		// TODO: alterar escolhaStorageUpload e escolhaStorageDownload para retornar um unico string com ip e porta?
		public String[] escolhaStorageUpload() {
			// TODO: implementar escolha
			String ipStorage = "192.168.15.6";
			String portStorage = "33336";

			String[] resultado = new String[2];
			resultado[0] = ipStorage;
			resultado[1] = portStorage;
			return resultado;
		}

		// Qual storage tem o arquivo? Envie a REQUISICAO para ele
		// TODO: alterar escolhaStorageUpload e escolhaStorageDownload para retornar um unico string com ip e porta?
		public String[] escolhaStorageDownload() {
			// TODO: implementar escolha
			String ipStorage = "192.168.15.6";
			String portStorage = "33336";

			String[] resultado = new String[2];
			resultado[0] = ipStorage;
			resultado[1] = portStorage;
			return resultado;
		}

	}

// StorageHandler class
	class StorageHandler extends Thread {
		final DataInputStream dis;
		// final Socket s;
		Semaphore semUser;
		Semaphore semArq;

		// Constructor
		public StorageHandler(DataInputStream dis) {
			// this.s = s;
			this.dis = dis;
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

						//// String[] splitEscolha = escolhaClientDownload();
						
						// TODO: Nao eh ideal, pegar ip pelo nome do arquivo??
						// TODO: adicionar ip ao cabecalho?
						String ipClient = mapClientArq.get(m.getHeader().getNome());
						// String portClient = splitEscolha[1];
						DataOutputStream dos = mapDOSClient.get(ipClient);

						System.out.println("writing to cliente: " + dos.toString());
						dos.write(message);
						
						addPermissaoClient( m.getHeader().getNome(), bytesToLong(user) ); //Adiciona permissao pro usuario fazer download desse arquivo
					}

					// System.out.println("received message from client " + this.s.getPort() + "! >>
					// "
					// + new String(received, StandardCharsets.UTF_8));

				} catch (SocketException se) {
					se.printStackTrace();
					break;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}// Fim do metodo run
		
		//Adiciona uma nova linha no arquivo de permissoes
		protected void addPermissaoClient(String arq , long user){
			//TODO: SEMAFORO?
			
			
			//Consideracao: Esse codigo tem propenção a sofrer com injecao. Acredito que nao de tempo pra corrigir isso, mas eh bom saber mesmo assim.
			// Exemplo: stringAleatorio;0'\n'arquivoExistenteQueDesejaRoubar
			//  Em teoria essa linha extra gerada nunca seria alcançada, porem caso fosse isso permitiria o acesso
			// Exemplo2: stringAleatorio;0'\n'arquivoQueEuNaoConhecoMasExisteAPossibilidadeDeExistirNoFuturo
			//  Nessa situacao um bot poderia spammar o Server com nomes aleatorios (ou gerados por uma IA) para roubar arquivos que ainda não existem no sistema no futuro.
			//  Outro exemplo pra mesma situacao, um arquivo com nome: 
			//   teste.txt;0'\n'senhas.txt;444'\n'senhas.xlsx;444'\n'foto.png
			//Talvez isso deixe depois ser um problema depois de adicionado o sistema de diretorio ou o sistema de arquivo
			
			
			File fileArq = new File(nomeArqUser);
			try {
				FileWriter fw = new FileWriter(fileArq);
				fw.append('\n'); //TODO: Isso causa dependencia de sistema?
				fw.append( (arq + ";" + user) );
				fw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

//		// Qual cliente deve receber o arquivo?
//		// TODO: alterar escolhaClientDownload para retornar um unico string com ip e
//		// porta?
//		public String[] escolhaClientDownload() {
//
//			// TODO: IMPLEMENTAR
//			String ipClient = "192.168.15.2";
//			String portClient = "33336";
//
//			String[] resultado = new String[2];
//			resultado[0] = ipClient;
//			resultado[1] = portClient;
//			return resultado;
//		}

	}// Fim de storage handler

	// ======================== METODOS de ServerImplementation=============================

	public Boolean userTemAcesso(long user, String arq) {
		// TODO: Semaphore?
		boolean ok = false;

		BufferedReader bfr;
		try {

			bfr = new BufferedReader(new FileReader("arqUser.txt"));
			String line;

			// TODO: acho que da pra melhorar esse loop, ta dificil de ler
			do {
				line = bfr.readLine();
				if (line == null) {
					break;
				}

				// Assumindo que arqUser.txt se pareca com isso: Nome do arquivo1 ; usuario1,
				// usuario2, usuario 3
				if (line.split(";")[0] == arq) {
					// Encontrou linha correspondente do arquivo

					String[] users = line.split(";")[1].split(",");
					for (int i = 0; i < users.length; i++) {
						if (Long.parseLong(users[i]) == user) {
							// Encontrou o usuario
							ok = true;
							break;
						}
					}
					break; // Nao ha mais necessidade de continuar lendo o arquivo, saia do loop
				}
			} while (line != null);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// semUser.release();
		return ok;
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

	// TODO: alterar para retornar ip *e* porta
	public static String getIpSocket(Socket socket) {
		SocketAddress socketAddress = socket.getRemoteSocketAddress();

		if (socketAddress instanceof InetSocketAddress) {
			InetAddress inetAddress = ((InetSocketAddress) socketAddress).getAddress();
			if (inetAddress instanceof Inet4Address) {
				return inetAddress.toString().substring(1);
			} else if (inetAddress instanceof Inet6Address) {
				return inetAddress.toString().substring(1);
			} else {
				System.err.println("Not an IP address.");
				return null;
			}
		} else {
			System.err.println("Not an internet protocol socket.");
			return null;
		}
	}

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

public class Server {

	public static void main(String args[]) throws IOException {
		ServerImplementation server = new ServerImplementation(args);
	}

}
