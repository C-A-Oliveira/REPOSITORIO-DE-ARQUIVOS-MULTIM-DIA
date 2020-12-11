package server;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import crypto.AsymmetricCryptoManager;
import crypto.SymmetricCryptoManager;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;

class ServerImplementation {

	// public static final byte RECEBE_ARQ_CLIENT = (byte) 0x00;
	public static final byte RECEBE_ARQ_REP_CLIENT = (byte) 0x06;
	public static final byte RECEBE_ARQ_DIV_CLIENT = (byte) 0x07;
	public static final byte RECEBE_ARQ_STORAGE = (byte) 0x02;
	public static final byte RECEBE_REQ_CLIENT = (byte) 0x04;
	public static final byte ENVIA_ARQ_STORAGE = (byte) 0x01;
	public static final byte ENVIA_ARQ_CLIENT = (byte) 0x03;
	public static final byte ENVIA_REQ_STORAGE = (byte) 0x05;
	public static String PATH_STORAGE_01 = "db\\STORAGE_01\\";
	public static String PATH_STORAGE_02 = "db\\STORAGE_02\\";
	public static String PATH_REP = "\\REPLICADO";
	public static String PATH_PAR = "\\PARTICIONADO";

	// TODO: semaforos
	public static final String nomeArqPermissao = "arqPermissao.txt";
	public static final Semaphore semaforoPermissao = new Semaphore(1);

	public static final String nomeArqUser = "arqUser.txt";
	public static final Semaphore semaforoUser = new Semaphore(1);

	public static final String nomeArqFiles = "arqFiles.txt";

	public static final Semaphore semaforoFiles = new Semaphore(1);

	private Vector<String> users = new Vector<String>();
	private Vector<Key> usersKeys = new Vector<Key>();

	// TODO: Usar ConcurrentHashMap inves de HashTable? HashTable possui metodos
	// sincronizados
	// Mapas usados para manter os DataOutputStream de todos os storages e clients
	// para que nao haja necessidade de criar mais sockets
	public static Hashtable<String, DataOutputStream> mapDOSStorage = new Hashtable<>();
	public static Hashtable<String, DataOutputStream> mapDOSClient = new Hashtable<>();

	public static Hashtable<String, String> mapClientArq = new Hashtable<>();

	public static Hashtable<String, Hashtable<Integer, Byte[]>> mapaReconstrucaoDiv = new Hashtable<>();

	public KeyPair keyPair;

	public ServerImplementation(String[] args) throws IOException {
		this.main(args);
	}

	public void main(String[] args) throws IOException {
		ClientListener clientListener = new ClientListener();
		StorageListener storageListener = new StorageListener();

		generateKeyPair();

		listUsers();

		clientListener.start();
		clientListener.setName("ClientListenerThread");
		storageListener.start();
		storageListener.setName("StorageListenerThread");
	}

	class ClientListener extends Thread {

		public ClientListener() {

		}

		@Override
		public void run() {
			try {
//				testAESEncryptionAndDecryption();

				// server is listening on port 33333
				ServerSocket ssc = new ServerSocket(33333);

				// running infinite loop for getting client request
				boolean loop = true;
				while (loop) {
					Socket socketClient = null;

					try {
						socketClient = ssc.accept();

						if (socketClient != null) {
							System.out.println("A new client is trying to connect : " + socketClient);
						}

						// obtaining input and out streams
						DataInputStream disC = new DataInputStream(socketClient.getInputStream());
						DataOutputStream dosC = new DataOutputStream(socketClient.getOutputStream());

						// Starts key exchange
						byte[] SPubK = keyPair.getPublic().getEncoded();
						dosC.writeUTF(Base64.getEncoder().encodeToString(SPubK));
						byte[] ClientEncodedKey = Base64.getDecoder().decode(disC.readUTF());
						byte[] decryptedSymmetricKey = AsymmetricCryptoManager.decryptData(ClientEncodedKey,
								keyPair.getPrivate());

						// save the decryptedSymmetricKey for the connected client
						SymmetricCryptoManager sCryptoManager = new SymmetricCryptoManager(decryptedSymmetricKey);

//						// Test receiving encrypted data
//					    byte[] testBytes = Base64.getDecoder().decode(disC.readUTF());
//					    String msg = new String(sCryptoManager.decryptData(testBytes));
//					    System.out.println(msg);

						mapDOSClient.put(getIpSocket(socketClient), dosC);

						Thread tC = new ClientHandler(socketClient, sCryptoManager);

						// VERIFICAR LISTA DE USARIOS
						// CASO NÃO ESTEJA, ADICIONAR CLIENT NA LISTA DE USUARIOS
						// CRIAR DIRETORIOS PARA O CLIENT NOS STORAGES
						String client = socketClient.getInetAddress().getHostName() + "_" + socketClient.getPort();
						listUsers();
						if (!userExists(client)) {
							addUserToList(client);
						}

						// create a new thread object
						tC.setName("CLIENT/" + client);
						tC.start();

					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("Socket Closed");
						socketClient.close();
					}
				}
				ssc.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// ClientHandler class
	class ClientHandler extends Thread {
		DataOutputStream dos;
		DataInputStream dis;
		final Socket connection;
		final SymmetricCryptoManager sessionKey;

//		final String ipServer;
//		final String portaServer;

		public String ipClient;

		// Constructor
		public ClientHandler(Socket connection, SymmetricCryptoManager key) {
			this.dis = null;
			this.dos = null;
			this.sessionKey = key;
			try {
				this.dos = new DataOutputStream(connection.getOutputStream());
				this.dis = new DataInputStream(connection.getInputStream());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			this.ipClient = connection.getInetAddress().getHostName();
			this.connection = connection;
//			this.ipServer = ipServer;
//			this.portaServer = portaServer;
		}

		@Override
		public void run() {
			boolean loop = true;
			while (loop) {
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
					String nomeArq = msg.getHeader().getNome();
					byte[] body = msg.getBody();

					String[] splitEscolha;
					String ipStorage;
					Mensagem m;
					byte[] message;
					DataOutputStream storageDataOutput = null;
					ArrayList<DataOutputStream> dosAll;
					ArrayList<String> chaves;

					// Logica
					switch (mode) {
					case RECEBE_ARQ_REP_CLIENT:
						// -- UPLOAD REPLICADO: Client (bytes arq) -> Server -> Storage

						storageDataOutput = null;
						// storageDataOutput = mapDOSStorage.get(ipStorage);
						// TODO: isso nao era ideal ter nessa parte do codigo?
						dosAll = new ArrayList<>();
						chaves = Collections.list(mapDOSStorage.keys());
						for (int i = 0; i < chaves.size(); i++) {
							dosAll.add(mapDOSStorage.get(chaves.get(i)));
						}

						bNomeArq = ("REPLICADO/" + nomeArq).getBytes(StandardCharsets.UTF_8);
						m = new Mensagem(ENVIA_ARQ_STORAGE, bUser, bNomeArq, body);
						message = m.getMessage();

						addPermissaoClient(m.getHeader().getNome(), user); // Adiciona permissao pro usuario fazer
																			// download desse arquivo
						System.out.println("--");
						m.showMessage();
						System.out.println("--");

						for (int i = 0; i < dosAll.size(); i++) {
							storageDataOutput = dosAll.get(i);
							System.out.println("writing arq to storage: " + storageDataOutput.toString());
							storageDataOutput.write(m.getMessage());
							addArqFile(m.getHeader().getNome(), "REP", chaves.get(i));
						}

						// s.close();
						break;
					case RECEBE_ARQ_DIV_CLIENT:
						// -- UPLOAD: Client (bytes arq) -> Server -> Storage

						storageDataOutput = null;
						// storageDataOutput = mapDOSStorage.get(ipStorage);
						// TODO: isso nao era ideal ter nessa parte do codigo?
						dosAll = new ArrayList<>();
						chaves = Collections.list(mapDOSStorage.keys());
						for (int i = 0; i < chaves.size(); i++) {
							dosAll.add(mapDOSStorage.get(chaves.get(i)));
						}
						int tam = dosAll.size();

						byte[] bodyDiv = new byte[body.length / tam];
						int contadorDiv = 0;
						int sizeCopy;

						bNomeArq = ("PARTICIONADO/" + nomeArq).getBytes(StandardCharsets.UTF_8);
						m = new Mensagem(ENVIA_ARQ_STORAGE, bUser, bNomeArq, new byte[0]);// Somente para uso da
																							// permissao
						addPermissaoClient(m.getHeader().getNome(), user); // Adiciona permissao pro usuario fazer
																			// download desse arquivo

						for (int i = 0; i < dosAll.size(); i++) {
							if (i == dosAll.size() - 1 && tam > 1) {
								sizeCopy = (body.length - i * body.length);
							} else {
								sizeCopy = bodyDiv.length;
							}
							System.out.println("sizeCopy = " + sizeCopy);
							bodyDiv = Arrays.copyOfRange(body, contadorDiv, sizeCopy);
							message = m.getMessage();
							m = new Mensagem(ENVIA_ARQ_STORAGE, bUser, bNomeArq, bodyDiv);
							System.out.println(">>");
							m.showMessage();
							System.out.println(">>");
							storageDataOutput = dosAll.get(i);
							System.out.println("writing arq to storage: " + storageDataOutput.toString());
							storageDataOutput.write(m.getMessage());
							contadorDiv += body.length / tam;
							addArqFile(m.getHeader().getNome(), "DIV" + i, chaves.get(i));
						}

						// s.close();
						break;
					case RECEBE_REQ_CLIENT:
						// -- DOWNLOAD: Client (nome arq) -> Server -> Storage
						// Se o usuario possui acesso, entao envie a requisicao ao storage
						if (userTemAcesso(user, new String(body, StandardCharsets.UTF_8))) {
							m = new Mensagem(ENVIA_REQ_STORAGE, bUser, bNomeArq, body);
							message = m.getMessage();

							// Escolha do storage
							splitEscolha = escolhaStorageDownload(m.getHeader().getNome());
							String[] ipsAllStorages = Collections.list(mapDOSStorage.keys()).toArray(new String[0]);
							ipStorage = splitEscolha[0];

							mapClientArq.put(m.getHeader().getNome(), this.ipClient);
							if (isDiv(m.getHeader().getNome())) {
								for (int i = 0; i < ipsAllStorages.length; i++) {
									storageDataOutput = mapDOSStorage.get(ipsAllStorages[i]);
									storageDataOutput.write(message);
								}
							} else {
								storageDataOutput = mapDOSStorage.get(ipStorage);
								storageDataOutput.write(message);
							}
							// storageDataOutput.close();
							break;
						}
					}
				} catch (SocketException se) {
					se.printStackTrace();
					try {
						this.connection.close();
						loop = false;
						break;
					} catch (IOException e) {
						e.printStackTrace();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

			} // Fim do while
		}// Fim do metodo run

		// Adiciona uma nova linha no arquivo de permissoes
		protected void addPermissaoClient(String arq, long user) {
			// try {
			// semaforoPermissao.acquire();

			File fileArq = new File(nomeArqPermissao);
			try {
				FileWriter fw = new FileWriter(fileArq, true);
				fw.append('\n'); // TODO: Isso causa dependencia de sistema?
				fw.append((arq + ";" + user));
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// semaforoPermissao.release();
			// }
//			catch (InterruptedException e1) {
//				e1.printStackTrace();
//			}
		}

		public long bytesToLong(byte[] bytes) {
			ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
			buffer.put(bytes);
			buffer.flip();// need flip
			return buffer.getLong();
		}

		public void addArqFile(String nomeArq, String _modo, String ipStorage) {
			// semaforoFiles.acquire();
			try {
				FileWriter fw = new FileWriter(new File(nomeArqFiles), true);
				fw.append('\n');
				fw.append(nomeArq + ";" + _modo + ";" + ipStorage);
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			// semaforoFiles.release();
		}

//		// Qual storage deve receber o arquivo?
//		public String[] escolhaStorageUpload() {
//
//			// TODO: IMPLEMENTAR
//			String ipStorage = "127.0.0.1";
//			String portStorage = "33336";
//
//			String[] resultado = new String[2];
//			resultado[0] = ipStorage;
//			resultado[1] = portStorage;
//			return resultado;
//
//		}

		// Qual storage tem o arquivo? Envie a REQUISICAO para ele
		public String[] escolhaStorageDownload(String arq) {
			// semaforoFiles.acquire();
			ArrayList<String> outIp = new ArrayList<String>();
			try {
				BufferedReader fr = new BufferedReader(new FileReader(nomeArqFiles));
				String line = null;

				do {
					line = fr.readLine();
					if (line == null) {
						break;
					}

					String[] split = line.split(";");
					String nomeArq = split[0];
					String modoArq = split[1];
					String ip = split[2];
					String porta = split[3];
					porta = "33336"; // TODO: remover?
					if (nomeArq == arq) // CONSIDERAR: AND modoArq = "DIV"?
					{
						outIp.add(ip + ":" + porta);
					}
				} while (line != null);
				fr.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// semaforoFiles.release();
			return outIp.toArray(new String[0]);
		}

	}

	public int countDiv(String arq) {
		int c = 0;
		ArrayList<String> outIp = new ArrayList<String>();
		try {
			BufferedReader fr = new BufferedReader(new FileReader(nomeArqFiles));
			String line = null;

			do {
				line = fr.readLine();
				if (line == null) {
					break;
				}

				String[] split = line.split(";");
				String nomeArq = split[0];
				String modoArq = split[1];
				// String ip = split[2]; //TODO: remover?
				// String porta = split[3]; //TODO: remover?
				// porta = "33336"; //TODO: remover?
				if (nomeArq == arq) {
					fr.close();
					if (modoArq.substring(0, 3) == "DIV") {
						c++;
					}
				}
			} while (line != null);
			fr.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return c;
		// semaforoFiles.release();
	}

	public boolean isDiv(String arq) {
		ArrayList<String> outIp = new ArrayList<String>();
		try {
			BufferedReader fr = new BufferedReader(new FileReader(nomeArqFiles));
			String line = null;

			do {
				line = fr.readLine();
				if (line == null) {
					break;
				}

				String[] split = line.split(";");
				String nomeArq = split[0];
				String modoArq = split[1];
				// String ip = split[2]; //TODO: remover?
				// String porta = split[3]; //TODO: remover?
				// porta = "33336"; //TODO: remover?
				if (nomeArq == arq) {
					fr.close();
					if (modoArq.substring(0, 3) == "DIV") {
						return true;
					} else {
						return false;
					}
				}
			} while (line != null);
			fr.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
		// semaforoFiles.release();
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

						mapDOSStorage.put(getIpSocket(socketSt), dosSt);

						// System.out.println("Assigning new thread storage " + nameSt);
						Thread tSt = new StorageHandler(disSt, socketSt);

						tSt.setName("STORAGE/" + nameSt);
						tSt.start();
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("Socket Closed");
						socketSt.close();
					}
				}
				sst.close();
			} catch (IOException io) {
				io.printStackTrace();
			}
		}
	}

	// StorageHandler class
	class StorageHandler extends Thread {
		final DataInputStream dis;
		final Socket s;

		// Constructor
		public StorageHandler(DataInputStream dis, Socket socketSt) {
			// this.s = s;
			this.dis = dis;
			this.s = socketSt;
		}

		@Override
		public void run() {
			System.out.println("executando run da thread StorageHandler");
			boolean loop = true;
			while (loop) {
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

						// TODO: Nao eh ideal pegar ip pelo nome do arquivo. Adicionar IP ao cabecalho
						// para evitar esse tipo de codigo?
						String ipClient = mapClientArq.get(m.getHeader().getNome());
						// String portClient = splitEscolha[1];
						DataOutputStream dos = mapDOSClient.get(ipClient);

						System.out.println("writing to cliente: " + dos.toString());
						int countDiv = countDiv(m.getHeader().getNome());
						if (countDiv > 0) {
							// Se for uma divisao

							Hashtable<Integer, Byte[]> aux = new Hashtable<>();
							aux = mapaReconstrucaoDiv.get(m.getHeader().getNome());
							aux.put(new Integer(qualDiv(m.getHeader().getNome(), getIpSocket(this.s))),
									byteArrToByteArr(m.getBody()));
							mapaReconstrucaoDiv.put(m.getHeader().getNome(), aux);

							int countKeys = Collections.list(aux.keys()).size();
							if (countKeys == countDiv(m.getHeader().getNome())) {
								// Pegou todas as divs
								ArrayList<Byte> listNewBody = new ArrayList<>();
								for (int i = 0; i < countKeys; i++) {
									for (Byte bx : aux.get(i)) {
										listNewBody.add(bx);
									}
								}
								byte[] newBody = new byte[listNewBody.size()];
								newBody = byteObjArrToByteTypeArr(listNewBody.toArray(new Byte[0]));
								m = new Mensagem(ENVIA_ARQ_CLIENT, user, bNomeArq, newBody);
								dos.write(m.getMessage());
							}

						} else {
							dos.write(m.getMessage());
						}
					}

					// System.out.println("received message from client " + this.s.getPort() + "!
					// >>"+ new String(received, StandardCharsets.UTF_8));

				} catch (SocketException se) {
					se.printStackTrace();
					try {
						this.s.close();
						loop = false;
						break;
					} catch (IOException e) {
						e.printStackTrace();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}// Fim do metodo run

		public int qualDiv(String _nome, String _ip) {
			int c = 0;
			ArrayList<String> outIp = new ArrayList<String>();
			try {
				BufferedReader fr = new BufferedReader(new FileReader(nomeArqFiles));
				String line = null;

				do {
					line = fr.readLine();
					if (line == null) {
						break;
					}

					String[] split = line.split(";");
					String nomeArq = split[0];
					String modoArq = split[1];
					String ip = split[2];

					if (_nome == nomeArq && _ip == ip) {
						return Integer.parseInt(modoArq.substring(3));
					}
				} while (line != null);
				fr.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return c;
			// semaforoFiles.release();
		}

		// byte[] -> Byte[]
		public Byte[] byteArrToByteArr(byte[] _bytes) {
			Byte[] byteObjects = new Byte[_bytes.length];
			int i = 0;
			for (byte b : _bytes)
				byteObjects[i++] = b;
			return byteObjects;
		}

		// Byte[] -> byte[]
		public byte[] byteObjArrToByteTypeArr(Byte[] byteObjects) {
			byte[] bytes = new byte[byteObjects.length];
			int j = 0;
			for (Byte b : byteObjects)
				bytes[j++] = b.byteValue();
			return bytes;
		}

//		// Qual cliente deve receber o arquivo?
//		public String[] escolhaClientDownload() {
//
//			String ipClient = "192.168.15.2";
//			String portClient = "33336";
//
//			String[] resultado = new String[2];
//			resultado[0] = ipClient;
//			resultado[1] = portClient;
//			return resultado;
//		}

	}// Fim de storage handler

	// ======================== METODOS de
	// ServerImplementation=============================
	public Boolean userTemAcesso(long user, String arq) {
		boolean ok = false;

		BufferedReader bfr;
		try {
			// semaforoPermissao.acquire();
			bfr = new BufferedReader(new FileReader(nomeArqPermissao));
			String line;

			do {
				line = bfr.readLine();
				if (line == null) {
					break;
				}

				// Assumindo que arqPermissao.txt se pareca com isso: Nome do arquivo1 ;
				// usuario1, usuario2, usuario 3
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

			// semaforoPermissao.release();

		} catch (IOException e) {
			e.printStackTrace();
		}
//		catch (InterruptedException e) {
//			e.printStackTrace();
//		}

		return ok;
	}

	public boolean userExists(String client) {
		for (String user : users)
			if (user.compareTo(client) == 0)
				return true;

		return false;
	}

	private void listUsers() throws IOException {
		BufferedReader bufferFile = new BufferedReader(new FileReader("src/server/clients.txt"));
		String line;
		while ((line = bufferFile.readLine()) != null)
			users.add(line);
		bufferFile.close();
	}

	private void addUserToList(String user) throws IOException {
		BufferedWriter file = new BufferedWriter(new FileWriter("src/server/clients.txt", true));
		if (!userExists(user)) {
			users.add(user);
		}
		file.append(user + "\n");
		file.close();

		new File(PATH_STORAGE_01 + user + PATH_REP).mkdirs();
		new File(PATH_STORAGE_01 + user + PATH_PAR).mkdirs();
		new File(PATH_STORAGE_02 + user + PATH_REP).mkdirs();
		new File(PATH_STORAGE_02 + user + PATH_PAR).mkdirs();
	}

	private void generateKeyPair() {
		keyPair = AsymmetricCryptoManager.generateKeyPair();
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

			System.out.println(encodedKey.length);
			System.out.println(decryptedSymmetricKey.length);

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

	public static String getIpSocket(Socket socket) {
		SocketAddress socketAddress = socket.getRemoteSocketAddress();

		if (socketAddress instanceof InetSocketAddress) {
			InetAddress inetAddress = ((InetSocketAddress) socketAddress).getAddress();
			int port = socket.getPort();
			if (inetAddress instanceof Inet4Address) {
				return inetAddress.toString().substring(1) + ":" + port;
			} else if (inetAddress instanceof Inet6Address) {
				return inetAddress.toString().substring(1) + ":" + port;
			} else {
				System.err.println("Nao eh IP.");
				return null;
			}
		} else {
			System.err.println("Nao eh um socket IP.");
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
