package client;
// Java implementation for a client 
// Save file as Client.java 
  
import java.io.*; 
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Scanner; 
  
// Client class 
public class Client  
{ 
	private static long id;
	
	private static final byte UPLOAD = (byte)0x00;
	private static final byte DOWNLOAD = (byte)0x01;
	
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
	
    public static void main(String[] args) throws IOException  
    { 
    	
        try
        { 
            Scanner scn = new Scanner(System.in); 
            
            // getting localhost ip 
            
            //InetAddress ip = InetAddress.getByName("localhost"); 
            int sPort = Integer.parseInt(args[1]);
            int cPort = Integer.parseInt(args[3]);
            byte[] sip = {0,0,0,0};
            byte[] cip = {0,0,0,0};
            String[] S = args[0].replace('.', '-').split("-");
            for(int i=0; i<4; i++)
            	sip[i] = Byte.parseByte(S[i]);
            InetAddress sIP = InetAddress.getByAddress(sip);
            String[] C = args[2].replace('.', '-').split("-");
            for(int i=0; i<4; i++)
            	cip[i] = Byte.parseByte(C[i]);
            InetAddress cIP = InetAddress.getByAddress(cip);
                  
            // establish the connection 
            Socket s = new Socket(sIP, sPort, cIP, cPort); 
      
            // obtaining input and out streams 
            DataInputStream dis = new DataInputStream(s.getInputStream()); 
            DataOutputStream dos = new DataOutputStream(s.getOutputStream()); 
      
            // the following loop performs the exchange of 
            // information between client and client handler 
            while (true)  
            { 
                System.out.println(dis.readUTF()); 
                
                
                String input = scn.nextLine();
                String tosend = input;
                byte[] bytes = input.getBytes();
                
                byte[] message = makeMessage( UPLOAD , id,bytes);//TODO: Mudar primeiro argumento baseado no que o usuario escolher
                
                dos.write(message);
                //dos.writeUTF(tosend); 
                  
                // If client sends exit,close this connection  
                // and then break from the while loop 
                if(tosend.equals("Exit")) 
                { 
                    System.out.println("Closing this connection : " + s); 
                    s.close(); 
                    System.out.println("Connection closed"); 
                    break; 
                } 
                  
                // printing date or time as requested by client 
                String received = dis.readUTF(); 
                System.out.println(received); 
            } 
              
            // closing resources 
            scn.close(); 
            dis.close(); 
            dos.close(); 
        }catch(Exception e){ 
            e.printStackTrace(); 
        } 
    }
    
   
} 