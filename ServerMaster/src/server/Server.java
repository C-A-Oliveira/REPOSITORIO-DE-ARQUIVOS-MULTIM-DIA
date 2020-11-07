package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.net.ServerSocket;

/**
 *
 * @author seaso
 */
public class Server {

	private final Socket socket;

	private Server(Socket socket) {
		this.socket = socket;
	}

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		// TODO code application logic here
		int port = 33334;
		try {
			
			ArrayList<ServerSocket> connectons = new ArrayList<ServerSocket>();
//			for (int c = 0; c < 100; c++) {
//				connectons.add(new ServerSocket(port + c));
//			}
			
			ServerSocket socket = new ServerSocket(port);
			System.out.println("Socket Criado");
			while (true) {
				System.out.println("Aguardando cliente...");
				
//				for (ServerSocket sockett : connectons) {
//					try (Socket client = sockett.accept()) {
//						try (InputStream in = client.getInputStream(); OutputStream out = client.getOutputStream();) {
//							byte[] buffer = new byte[2048];
//							int n;
//							while ((n = in.read(buffer)) > 0) {
//								for (int i = 0; i < n; i++) {
//									System.out.printf("%02d", buffer[i]);
//								}
//								System.out.println(sockett.getInetAddress().toString());
//								// ordena
//								Arrays.sort(buffer, 0, n);
//								// ecoa ordenado
//								out.write(buffer);
//
//							}
//
//						}
//					}
//					
//				}
				
				try (Socket client = socket.accept()) {
					try (InputStream in = client.getInputStream(); OutputStream out = client.getOutputStream();) {
						byte[] buffer = new byte[2048];
						int n;
						while ((n = in.read(buffer)) > 0) {
							for (int i = 0; i < n; i++) {
								System.out.printf("%02d", buffer[i]);
							}
							System.out.println("\n" + socket.getInetAddress().toString());
							// ordena
							Arrays.sort(buffer, 0, n);
							// ecoa ordenado
							out.write(buffer);

						}

					}
				}
				
			}
		} catch (IOException ex) {

			System.out.println("ERRO AO CONECTAR >> " + ex.getMessage());
		}
	}
}