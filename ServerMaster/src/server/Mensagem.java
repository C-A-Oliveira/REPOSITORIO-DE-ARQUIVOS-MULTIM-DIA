package server;

public class Mensagem {
	
	private Header header;
	private byte[] body;
	
	public Mensagem(byte[] _message) {
		byte[][] split = splitMessage(_message);
		this.header = new Header(split[0]);
		this.body = split[1];
	}
	
	public Mensagem(byte mode, byte[] user, byte[] nome, byte[] body) {
		this.header = new Header(mode, user, nome, body);
		this.body = body;
	}
	
	public Mensagem(byte mode, long user, String nome, byte[] body) {
		this.header = new Header(mode, user, nome, body);
		this.body = body;
	}
	
	public byte[] getBody() {
		return body;
	}
	
	public byte[] getMessage() {
		return concantenateBytes(this.header.getHeader(), this.getBody());
	}
	
	public Header getHeader() {
		return this.header;
	}
	
	public byte[][] splitMessage(byte[] _msg) {
		
		//Tamanho do nome pra poder saber o tamanho do cabecalho
		byte[] bTamNome = new byte[Header.SIZE_TAM_NOME]; 
		System.arraycopy(_msg, Header.INDEX_TAM_NOME, bTamNome, 0, bTamNome.length);
		int tamNome = bytesToInt(bTamNome);
		
		int sizeHeader = Integer.BYTES + 1 + Long.BYTES + Integer.BYTES + tamNome;
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
	
	// ====================== METODOS UTILITARIOS =================================
	
	private static byte[] concantenateBytes(byte[] array1, byte[] array2) {
		
		System.out.println("header = " + array1.length);
		System.out.println("body = " + array2.length);
		
		int aLen = array1.length;
        int bLen = array2.length;
        byte[] result = new byte[aLen + bLen];

        System.arraycopy(array1, 0, result, 0, aLen);
        System.arraycopy(array2, 0, result, aLen, bLen);
        
        return result;
	}
	
	private static int bytesToInt(byte[] bytes) {
	     return ((bytes[0] & 0xFF) << 24) | 
	            ((bytes[1] & 0xFF) << 16) | 
	            ((bytes[2] & 0xFF) << 8 ) | 
	            ((bytes[3] & 0xFF) << 0 );
	}
	

}
