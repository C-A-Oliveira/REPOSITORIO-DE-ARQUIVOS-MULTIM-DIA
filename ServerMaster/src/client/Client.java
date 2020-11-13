package client;
// Java implementation for a client 
// Save file as Client.java 
  
import java.io.*; 
import java.net.*; 
import java.util.Scanner; 
  
// Client class 
public class Client  
{ 
	private static String id;
	
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
                  
            // establish the connection with server port 5056 
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
                dos.writeUTF(tosend); 
                  
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