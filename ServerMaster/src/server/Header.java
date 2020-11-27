package server;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Header {
	
	//  Tam / Modo / User     / Tamnome / Nomearq
	// tttt / m    / uuuuuuuu / nnnn    / <variavel>
	
	private byte[] header;
	
	//Constantes de indice
	private static final int INDEX_TAM_MESSAGE = 0;
	private static final int INDEX_MODE = 4;
	private static final int INDEX_USER = 5;
	private static final int INDEX_TAM_NOME = 13;
	private static final int INDEX_NOME = 17;
	
	//Constantes de tamanho (NOTA: o nome do arquivo nao possui tamanho constante)
	private static final int SIZE_TAM_MESSAGE = 4;
	//private static final int SIZE_MODE = 1;
	private static final int SIZE_USER = 8;
	private static final int SIZE_TAM_NOME = 4;
	
	//==================== CONSTRUTORES ======================================================
	
	public void start(byte _mode, byte[] _id, byte[] _nome, byte[] _body) {

		byte[] _header = new byte[Integer.BYTES + 1 + Long.BYTES + Integer.BYTES + _nome.length ];
		
		int k = 0;
		
		
		// Header
		// Tamanho
		byte[] lb = intToBytes(_body.length + _header.length);
		int j = 0;
		int start = k;
		for (int i = k; i < start + Integer.BYTES; i++) {
			_header[i] = lb[j];
			k++;
			j++;
		}
		j=0;
		System.out.println("1-k = " + k);

		// Modo
		_header[k++] = _mode;
		System.out.println("2-k = " + k);

		// Usuario
		byte[] bytesId = _id;
		j = 0;
		start = k;
		for (int i = k; i < start + SIZE_USER; i++) {
			_header[i] = bytesId[j];
			j++;
			k++;
		}
		System.out.println("3-k = " + k);
		
		j=0;
		
		//Tamanho Nome do arq
		byte[] bytesNome = _nome; // Nome
		byte[] nlb = intToBytes( bytesNome.length );
		start = k;
		for (int i = k; i < start + Integer.BYTES; i++) {
			_header[i] = nlb[j];
			j++;
			k++;
		}
		System.out.println("4-k = " + k);
		
		j=0;
		
		//Nome do arq
		start = k;
		for(int i=k;i< start + bytesNome.length;i++) {
			_header[i] = bytesNome[j];
			j++;
			k++;
		}
		j=0;
		System.out.println("5-k = " + k);
		
		System.out.println("K = "+ k);
		
		this.header = _header;
		
	}
	
	public Header(byte _mode, long _id, String _nome, byte[] _body) {
		start(_mode, longToBytes(_id), _nome.getBytes(StandardCharsets.UTF_8), _body);
	}
	
	public Header(byte _mode, byte[] _id, byte[] _nome, byte[] _body) {
		start(_mode, _id, _nome, _body);
	}
	
	public Header(byte[] _header) {
		this.header = _header;
	}
	
	//---------------------------------------------------------------------------------------
	
	public byte[] getHeader() {
		return this.header;
	}
	
	public int headerSize() {
		return this.header.length;
	}
	
	// TAM MESSAGE
	public byte[] getBTamMessage() {
		byte[] x = new byte[SIZE_TAM_MESSAGE];
		
		System.arraycopy(header, INDEX_TAM_MESSAGE, x, 0, x.length);
		
		return x;
	}
	public int getTamMessage() {
		return bytesToInt(getBTamMessage());
	}
	
	// MODE
	public byte getMode() {
		byte x;
		
		x = this.header[INDEX_MODE];
		
		return x;
	}
	
	// USER
	public byte[] getBUser() {
		byte[] x = new byte[SIZE_USER];
		
		System.arraycopy(header, INDEX_USER, x, 0, x.length);
		
		return x;
	}
	public long getUser() {
		return bytesToLong(getBUser());
	}
	
	// TAM NOME
	public byte[] getBTamNome() {
		byte[] x = new byte[SIZE_TAM_NOME];
		
		//TESTE
		for(int i=0; i< this.header.length ; i++) {
	         System.out.print(this.header[i] +" ");
	    }
		System.out.println();
		
		System.arraycopy(header, INDEX_TAM_NOME, x, 0, SIZE_TAM_NOME);
		
		return x;
	}
	public int getTamNome() {
		return bytesToInt(getBTamNome());
	}
	
	// NOME
	public byte[] getBNome() {
		int tamNome = bytesToInt(getBTamNome());
		
		byte[] x = new byte[tamNome];
		
		System.arraycopy(header, INDEX_NOME, x, 0, x.length);
		
		return x;
		
	}
	public String getNome() {
		return bytesToString(getBNome());
	}
	
	// ========================= METODOS UTILITARIOS ===============================================
	
	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(x);
		return buffer.array();
	}
	
	private static String bytesToString(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8);
	}
	
	private static int bytesToInt(byte[] bytes) {
	     return ((bytes[0] & 0xFF) << 24) | 
	            ((bytes[1] & 0xFF) << 16) | 
	            ((bytes[2] & 0xFF) << 8 ) | 
	            ((bytes[3] & 0xFF) << 0 );
	}
	
	private static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getLong();
	}
	
	private static byte[] intToBytes(int i) {
		byte[] result = new byte[4];

		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i);

		return result;
	}

}
