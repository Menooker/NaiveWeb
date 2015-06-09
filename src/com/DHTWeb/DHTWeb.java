package com.DHTWeb;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;

public class DHTWeb {

	static PeerManager pm;
	
	
	public static class ReplyListener implements ObjectDataReply
	{

		@Override
		public Object reply(PeerAddress arg0, Object arg1) throws Exception {
			System.out.println(arg0);
			return null;
		}
		
	}
	static ReplyListener listener=new ReplyListener();
	
	private static class ExitHandler extends Thread {
		public ExitHandler() {
			super("Exit Handler");
		}

		public void run() {
			pm.exit();
		}
	}
	   
    public static void main(String[] args) throws NumberFormatException, Exception {
 
    	Runtime.getRuntime().addShutdownHook(new ExitHandler());

    	pm= null;
        if (args[0].equals("-n")) { //-s name ip key
        	pm=new PeerManager(args[1],listener);
        	PeerManager.WriteKey(pm.getMasterKey(), "master_");
        	PeerManager.WriteKey(pm.getRootKey(), "root_");
      
        	pm.createrootdir("data",Number160.createHash("DIR_DATA"));
        	pm.createrootdir("img",Number160.createHash("DIR_IMG"));
            pm.putdir(pm.dirid("data/testhtml"), "testhtml", "<html><body><h1>Hello World</h1><br>This is our DHTWeb homepage<br><img src=../data/testjpeg /></body></html>") ;
            InputStream fis = null;  
            fis = new FileInputStream(new File("LHDN.png"));  
            byte[] buff = new byte[fis.available()];  
            fis.read(buff);
            pm.putdir(pm.dirid("data/testjpeg"), "testjpeg", buff) ;
            fis.close();
        }
        if (args[0].equals("-c")) {
        	pm=new PeerManager(args[1],1);
            //peer.shutdown();
        }
        if(args[0].equals("-w")) //-w host name ip key
        {
        	pm=new PeerManager(args[1],PeerManager.ReadKey("master_"),listener);
        }
        if(args[0].equals("-s"))
        {
        	pm=new PeerManager(PeerManager.ReadKey("master_"),
        			PeerManager.ReadKey("root_"),args[1],listener);     	
        }
        if(args[0].equals("-j"))
        {
        	pm=new PeerManager(args[1],PeerManager.ReadKey("master_"),
        			PeerManager.ReadKey("root_"),args[2],listener);     	
        }       
        
        
        if(args[args.length-1].equals("cmd"))
        {
        	System.out.println("Command Mode");
        	FileSystemShell.loop(pm);
        }
        else if(args[args.length-1].equals("svr"))
        {
        	System.out.println("Local server Mode");
            while(pm!=null)
            {

            	int port = 6789;
        		// Establish the listen socket.
        		ServerSocket welcomeSocket = new ServerSocket(port);
        		// Process HTTP service requests in an infinite loop.
        		while (true) {
        			// Listen for a TCP connection request.
        			Socket connectionSocket = welcomeSocket.accept();
        			// Listen for a TCP connection request.
        			HttpRequest request = new HttpRequest(connectionSocket,pm);
        			// Create a new thread to process the request.
        			Thread thread = new Thread(request);
        			// Start the thread.
        			thread.start();
        		}
            }     	
        }   
    	
        
    }


}
