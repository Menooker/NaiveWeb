package com.DHTWeb;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
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
	final Random rnd = new Random( 42L );
	static final ProtectionEnable PROTECT=ProtectionEnable.ALL;
	static final ProtectionMode PROTECTMODE=ProtectionMode.MASTER_PUBLIC_KEY;
	private static final DSASignatureFactory factory = new DSASignatureFactory();
	
	static private PeerDHT peer;
	static Number160 peer2Owner ;
	static KeyPair key;
    public DHTWeb(int peerId) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );
        KeyPair pair1 = gen.generateKeyPair();
        peer2Owner=Utils.makeSHAHash( pair1.getPublic().getEncoded() );
        FileOutputStream out=new FileOutputStream("publickey");
        PrintStream p=new PrintStream(out);
        p.write(pair1.getPublic().getEncoded());
        p.close();out.close();
        
        out=new FileOutputStream("privatekey");
        p=new PrintStream(out);
        p.write(pair1.getPrivate().getEncoded());
        p.close();out.close();
        
        key=pair1;
        
    	peer = new PeerBuilderDHT(new PeerBuilder(pair1).ports(4000 ).start()).start();
    	peer.storageLayer().protection(  PROTECT, PROTECTMODE , PROTECT,
    			PROTECTMODE  );
        FutureBootstrap fb = peer.peer().bootstrap().inetAddress(InetAddress.getByName("127.0.0.1")).ports(4000).start();
        fb.awaitUninterruptibly();
        if (fb.isSuccess()) {
            peer.peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
        }

    }
    public DHTWeb(String host,int peerId,boolean isMaster) throws Exception {
    	//Bindings b = new Bindings().listenAny();
    	
        FileInputStream out=new FileInputStream("publickey");
        byte[] buf=new byte[out.available()];
        out.read(buf);
        out.close();  	
        peer2Owner=Utils.makeSHAHash( buf ); 
        
    	KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );
        if(!isMaster)
        {
        	KeyPair pair1 = gen.generateKeyPair();
        	peer= new PeerBuilderDHT(new PeerBuilder(pair1).ports(4000+rnd.nextInt()%10000).behindFirewall().start()).start();
        }
        else
        {
        	FileInputStream fileIn=new FileInputStream("privatekey");
        	byte[] encodedprivateKey=new byte[fileIn.available()]; 
        	fileIn.read(encodedprivateKey); 
        	fileIn.close();
        	
   
        	KeyFactory keyFactory = KeyFactory.getInstance("DSA");
        	X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(buf);
        	PublicKey pubKey = keyFactory.generatePublic(pubKeySpec);
        	
        	PKCS8EncodedKeySpec keyspec=new PKCS8EncodedKeySpec(encodedprivateKey);
        	PrivateKey priKey = keyFactory.generatePrivate(keyspec);
        	
        	
        	KeyPair pair1=gen.generateKeyPair();
        	key=new KeyPair(pubKey,priKey);
        	peer= new PeerBuilderDHT(new PeerBuilder(pair1).ports(4000+rnd.nextInt()%10000).behindFirewall().start()).start();
            
        }
        peer.storageLayer().protection(  PROTECT, PROTECTMODE , PROTECT,
    			PROTECTMODE  );

    	
    	//System.out.println("Client started and Listening to: " + DiscoverNetworks.discoverInterfaces(b));
		System.out.println("address visible to outside is " + peer.peerAddress());

		InetAddress address = Inet4Address.getByName(host);
		int masterPort = 4000;
		PeerAddress pa = new PeerAddress(Number160.ZERO, address, masterPort, masterPort);

		System.out.println("PeerAddress: " + pa);
		
		PeerNAT peerNAT = new PeerBuilderNAT(peer.peer()).start();
		FutureDiscover fd = peer.peer().discover().peerAddress(pa).start();
		FutureNAT fn = peerNAT.startSetupPortforwarding(fd);
		FutureRelayNAT frn = peerNAT.startRelay(new TCPRelayClientConfig(), fd, fn);
		
		frn.awaitUninterruptibly();
		if (fd.isSuccess()) {
			System.out.println("*** found that my outside address is " + fd.peerAddress());
		} else {
			System.out.println("*** FD failed " + fd.failedReason());
		}
		
		if (fn.isSuccess()) {
			System.out.println("*** NAT success: " + fn.peerAddress());
		} else {
			System.out.println("*** Nat failed " + fn.failedReason());
		}
		
		if (frn.isSuccess()) {
			System.out.println("*** FutureRelay success");
		} else {
			System.out.println("*** Relay failed " + frn.failedReason());
		}
		
		// Future Bootstrap - slave
		FutureBootstrap futureBootstrap = peer.peer().bootstrap().inetAddress(address).ports(masterPort).start();
		futureBootstrap.awaitUninterruptibly();
		
/*		FutureDiscover fd = peer.peer().discover().peerAddress(pa).start();
		System.out.println("About to wait...");
		fd.awaitUninterruptibly();
		if (fd.isSuccess()) {
			System.out.println("*** FOUND THAT MY OUTSIDE ADDRESS IS " + fd.peerAddress());
		} else {
			System.out.println("*** FAILED " + fd.failedReason());
		}

		pa = fd.reporter();
		FutureBootstrap bootstrap = peer.peer().bootstrap().peerAddress(pa).start();
		bootstrap.awaitUninterruptibly();
		if (!bootstrap.isSuccess()) {
			System.out.println("*** COULD NOT BOOTSTRAP!");
		} else {
			System.out.println("*** SUCCESSFUL BOOTSTRAP");
		}*/

		
		
    }
    public static void main(String[] args) throws NumberFormatException, Exception {
    	PeerManager pm = null;
    	
        if (args[0].equals("-s")) { //-s name ip key
        	pm=new PeerManager();
        	PeerManager.WriteKey(pm.getMasterKey(), "master_");
        	PeerManager.WriteKey(pm.getRootKey(), "root_");
        	
        	pm.store(args[1],args[2]);
        	pm.createrootdir("data",Number160.createHash("DIR_DATA"));
        	pm.createrootdir("img",Number160.createHash("DIR_IMG"));
        }
        if (args[0].equals("-c")) {
        	pm=new PeerManager(args[1]);
            System.out.println("Name:" + args[2] + " IP:" + pm.get(args[2]));
            //peer.shutdown();
        }
        if(args[0].equals("-w")) //-w host name ip key
        {
        	pm=new PeerManager(args[1],PeerManager.ReadKey("master_"));
        	System.out.println("Name:" + args[2] + " IP:" + (String)pm.get(args[2]));
        	pm.store(args[2], args[3]);
            System.out.println("Name:" + args[2] + " IP:" + (String)pm.get(args[2]));
        }
        
        while(pm!=null)
        {
        	String cmd=getLine();
        	String[] argss=cmd.split(" ");
        	if(cmd.equals("stat"))
        	{
        		System.out.println("Ser--------");
    			for (PeerAddress pa : pm.peer().peerBean().peerMap().all()) {
    					System.out.println("peer online (TCP):" + pa);
    			}			
        	}
        	else if(argss[0].equals("mkdir"))
        	{
        		Number160 id=(Number160) pm.getdir(PeerManager.ROOT, argss[1]);
        		for (int i=2;i<argss.length-1;i++)
        		{
        			id=(Number160)pm.getdir(id,argss[i]);
        		}
        		pm.createdir(id, argss[argss.length-1], Number160.createHash(argss[argss.length-1]));
        	}
        	else if(argss[0].equals("ls"))
        	{
        		Number160 id=(Number160) pm.getdir(PeerManager.ROOT, argss[1]);
        		for (int i=2;i<argss.length;i++)
        		{
        			id=(Number160)pm.getdir(id,argss[i]);
        		}
        		for(Entry<Number640, Data> entry: pm.readdir(id).m.entrySet()){    
        		     System.out.print(entry.getKey().contentKey()+"--->");    
        		     if(entry.getValue().object().getClass()==String.class)
        		     {
        		    	 System.out.println((String)entry.getValue().object());
        		     }
        		     else if(entry.getValue().object().getClass()==Number160.class)
        		     {
        		    	 System.out.println((Number160)entry.getValue().object());
        		     }
        		     else 
        		     {
        		    	 System.out.println(entry.getValue().object());
        		     }
        		}   
        		System.out.println();
        	}
        	else if(argss[0].equals("put"))
        	{
        		Number160 id=(Number160) pm.getdir(PeerManager.ROOT, argss[1]);
        		for (int i=2;i<argss.length-2;i++)
        		{
        			id=(Number160)pm.getdir(id,argss[i]);
        		}
        		pm.putdir(id, argss[argss.length-2], argss[argss.length-1]) ;
        	}
        	else if(argss[0].equals("get"))
        	{
        		Number160 id=(Number160) pm.getdir(PeerManager.ROOT, argss[1]);
        		for (int i=2;i<argss.length-1;i++)
        		{
        			id=(Number160)pm.getdir(id,argss[i]);
        		}
        		System.out.println((String)pm.getdir(id, argss[argss.length-1])) ;
        	}
        	else if(argss[0].equals("exit"))
        	{
        		pm.peer().shutdown();
        		break;
        	}
        }
        
    }
	static String getLine() {
		System.out.print(">");
		InputStreamReader converter = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(converter);
		String inLine = "";
		try {
			inLine = in.readLine();
		} catch (Exception e) {
			System.err.println("Error reading input.");
			e.printStackTrace();
			System.exit(1);
		}
		return inLine;
	}
    private void store(String name, String ip,Number160 ck) throws IOException {//.protectDomain()
    	FuturePut p;

		p = peer.put(Number160.createHash(name)).data(ck,(new Data(ip).protectEntry(key))).sign().keyPair(key).domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();
	    if(!p.isSuccess())
	    {
	    	System.out.println(p.failedReason());
	    }
    }
	private String getall(String name) throws ClassNotFoundException, IOException {
		FutureGet futureGet = peer.get(Number160.createHash(name)).domainKey(Number160.ZERO).all().start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			Number640 k=new Number640(Number160.createHash(name),Number160.ZERO,Number160.createHash(1),Number160.ZERO);
			System.out.println(futureGet.dataMap().values().size());
			System.out.println((String)futureGet.dataMap()
					.get(k)
					.object());
			//System.out.println((String)get(Number160.createHash(1)).object());;
			//return (String)futureGet.data().object();
		}
		return "not found";
	}   
    
	private String get(String name) throws ClassNotFoundException, IOException {
		FutureGet futureGet = peer.get(Number160.createHash(name)).domainKey(Number160.ZERO).contentKey(Number160.ONE).start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			System.out.println(futureGet.dataMap().values().size());
			return (String)futureGet.data().object();
		}
		return "not found";
	}
    private void store(String name, String ip) throws IOException {//.protectDomain()
    	FuturePut p;

		p = peer.put(Number160.createHash(name)).data(Number160.ONE,(new Data(ip).protectEntry(key))).sign().keyPair(key).domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();
	    if(!p.isSuccess())
	    {
	    	System.out.println(p.failedReason());
	    }
		


    }

}
/*
public class DHTWeb
{
    
	final Random rnd = new Random( 42L );

    public static void main( String[] args )
        throws NoSuchAlgorithmException, IOException, ClassNotFoundException
    {
        exampleAllMaster();
        exampleNoneMaster();
    }
    public static void bootstrap( PeerDHT[] peers ) {
    	//make perfect bootstrap, the regular can take a while
    	for(int i=0;i<peers.length;i++) {
    		for(int j=0;j<peers.length;j++) {
    			peers[i].peerBean().peerMap().peerFound(peers[j].peerAddress(), null, null, null);
    		}
    	}
    }
    public static void exampleAllMaster()
        throws NoSuchAlgorithmException, IOException, ClassNotFoundException
    {
        KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );
        KeyPair pair1 = gen.generateKeyPair();
        KeyPair pair2 = gen.generateKeyPair();
        KeyPair pair3 = gen.generateKeyPair();
        final Number160 peer2Owner = Utils.makeSHAHash( pair2.getPublic().getEncoded() );
        PeerDHT peer1 = new PeerBuilderDHT(new PeerBuilder( pair1 ).ports( 4001 ).start()).start();
        PeerDHT peer2 = new PeerBuilderDHT(new PeerBuilder( pair2 ).ports( 4002 ).start()).start();
        PeerDHT peer3 = new PeerBuilderDHT(new PeerBuilder( pair3 ).ports( 4003 ).start()).start();
        PeerDHT[] peers = new PeerDHT[] { peer1, peer2, peer3 };
        bootstrap( peers );
        setProtection( peers, ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY );
        // peer 1 stores "test" in the domain key of owner peer 2
        FuturePut futurePut =
            peer1.put( Number160.ONE ).data( new Data( "test" ) ).domainKey( peer2Owner ).protectDomain().start();
        futurePut.awaitUninterruptibly();
        // peer 2 did not claim this domain, so we stored it
        System.out.println( "stored: " + futurePut.isSuccess() + " -> because no one claimed this domain" );
        // peer 3 want to store something
        futurePut =
            peer3.put( Number160.ONE ).data( new Data( "hello" ) ).domainKey( peer2Owner ).protectDomain().start();
        futurePut.awaitUninterruptibly();
        System.out.println( "stored: " + futurePut.isSuccess() + " -> becaues peer1 already claimed this domain" );
        // peer 2 claims this domain
        futurePut =
            peer2.put( Number160.ONE ).data( new Data( "MINE!" ) ).domainKey( peer2Owner ).protectDomain().start();
        futurePut.awaitUninterruptibly();
        System.out.println( "stored: " + futurePut.isSuccess() + " -> becaues peer2 is the owner" );
        // get the data!
        FutureGet futureGet = peer1.get( Number160.ONE ).domainKey( peer2Owner ).start();
        futureGet.awaitUninterruptibly();
        System.out.println( "we got " + futureGet.data().object() );
        shutdown( peers );
    }

    public static void exampleNoneMaster()
        throws NoSuchAlgorithmException, IOException, ClassNotFoundException
    {
        KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );
        KeyPair pair1 = gen.generateKeyPair();
        KeyPair pair2 = gen.generateKeyPair();
        KeyPair pair3 = gen.generateKeyPair();
        final Number160 peer2Owner = Utils.makeSHAHash( pair2.getPublic().getEncoded() );
        PeerDHT peer1 = new PeerBuilderDHT(new PeerBuilder( pair1 ).ports( 4001 ).start()).start();
        PeerDHT peer2 = new PeerBuilderDHT(new PeerBuilder( pair2 ).ports( 4002 ).start()).start();
        PeerDHT peer3 = new PeerBuilderDHT(new PeerBuilder( pair3 ).ports( 4003 ).start()).start();
        PeerDHT[] peers = new PeerDHT[] { peer1, peer2, peer3 };
        bootstrap( peers );
        setProtection( peers, ProtectionEnable.NONE, ProtectionMode.MASTER_PUBLIC_KEY );
        // peer 1 stores "test" in the domain key of owner peer 2
        FuturePut futurePut =
            peer1.put( Number160.ONE ).data( new Data( "test" ) ).protectDomain().domainKey( peer2Owner ).start();
        futurePut.awaitUninterruptibly();
        // peer 2 did not claim this domain, so we stored it
        System.out.println( "1stored: " + futurePut.isSuccess()
            + " -> because no one can claim domains except the owner, storage ok but no protection" );
        
        
        // peer 2 claims this domain
        futurePut =
            peer2.put( Number160.ONE ).data( new Data( "MINE!" ) ).protectDomain().domainKey( peer2Owner ).start();
        futurePut.awaitUninterruptibly();
        System.out.println( "2stored: " + futurePut.isSuccess() + " -> becaues peer2 is the owner" );
        
        
        // peer 3 want to store something
        futurePut =
            peer3.put( Number160.ONE ).data( new Data( "hello" ) ).protectDomain().domainKey( peer2Owner ).start();
        futurePut.awaitUninterruptibly();
        System.out.println( "3stored: " + futurePut.isSuccess()
            + " -> because no one can claim domains except the owner, storage ok but no protection" );

        // get the data!
        FutureGet futureGet = peer1.get( Number160.ONE ).domainKey( peer2Owner ).start();
        futureGet.awaitUninterruptibly();
        System.out.println( "we got " + futureGet.data().object() );
        futurePut = peer3.put( Number160.ONE ).domainKey( peer2Owner ).data( new Data( "hello" ) ).start();
        futurePut.awaitUninterruptibly();
        System.out.println( "stored: " + futurePut.isSuccess() + " -> because this domain is claimed by peer2" );
        shutdown( peers );
    }

    private static void shutdown( PeerDHT[] peers )
    {
        for ( PeerDHT peer : peers )
        {
            peer.shutdown();
        }
    }

    private static void setProtection( PeerDHT[] peers, ProtectionEnable protectionEnable, ProtectionMode protectionMode )
    {
        for ( PeerDHT peer : peers )
        {
            peer.storageLayer().protection( protectionEnable, protectionMode, protectionEnable,
                                                           protectionMode );
        }
    }
}//*/