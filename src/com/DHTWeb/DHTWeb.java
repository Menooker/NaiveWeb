package com.DHTWeb;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.DSASignatureFactory;
import net.tomp2p.connection.DiscoverNetworks;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageLayer.ProtectionEnable;
import net.tomp2p.dht.StorageLayer.ProtectionMode;
import net.tomp2p.futures.FutureBootstrap; 
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.nat.FutureNAT;
import net.tomp2p.nat.FutureRelayNAT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.relay.tcp.TCPRelayClientConfig;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;

public class DHTWeb {

    public static void main(String[] args) throws NumberFormatException, Exception {
    	
    	PeerManager pm = null;
        if (args[0].equals("-n")) { //-s name ip key
        	pm=new PeerManager(args[1]);
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
        	pm=new PeerManager(args[1],PeerManager.ReadKey("master_"));
        }
        if(args[0].equals("-s"))
        {
        	pm=new PeerManager(PeerManager.ReadKey("master_"),
        			PeerManager.ReadKey("root_"),args[1]);     	
        }
        if(args[0].equals("-j"))
        {
        	pm=new PeerManager(args[1],PeerManager.ReadKey("master_"),
        			PeerManager.ReadKey("root_"),args[2]);     	
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
