package com.DHTWeb;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.Random;

import net.tomp2p.connection.DSASignatureFactory;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
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


public class PeerManager {
	
	final Random rnd = new Random( 42L );
	static final ProtectionEnable PROTECT=ProtectionEnable.ALL;
	static final ProtectionMode PROTECTMODE=ProtectionMode.MASTER_PUBLIC_KEY;
	public static final Number160 ROOT=Number160.createHash(10086);
	public static final Number160 DIR_MAGIC=Number160.ONE;	
	
	private PeerDHT peer;
	KeyPair mKey;
	KeyPair rKey;
	boolean isMasterNode;
	boolean isRootNode;
	
	public class NotMasterNodeException extends Exception
	{
		
	}
	
	public class DataMapReader
	{
		public Map<Number640,Data> m;
		Number160 locationKey,domainKey;
		Number640 makekey(Number160 contentKey)
		{
			return new Number640(locationKey,domainKey,contentKey,Number160.ZERO);
		}
		public Data get(Number160 contentKey)
		{
			return m.get(makekey(contentKey));
		}
		public DataMapReader(Number160 lKey,Number160 dKey,Map<Number640,Data> map)
		{
			locationKey=lKey;domainKey=dKey;
			m=map;
		}
	}
	
	public static void WriteKey(KeyPair k,String name) throws IOException {
		FileOutputStream out;

		out = new FileOutputStream(name+"publickey");
		PrintStream p = new PrintStream(out);
		p.write(k.getPublic().getEncoded());
		p.close();
		out.close();

		out = new FileOutputStream(name+"privatekey");
		p = new PrintStream(out);
		p.write(k.getPrivate().getEncoded());
		p.close();
		out.close();

	}
	
	public KeyPair getMasterKey()
	{
		return mKey;
	}
	public KeyPair getRootKey()
	{
		return rKey;
	}	
	
	public static KeyPair ReadKey(String name) throws Exception {
		FileInputStream out;

		out = new FileInputStream(name+"publickey");
		byte[] buf = new byte[out.available()];
		out.read(buf);
		out.close();


		FileInputStream fileIn = new FileInputStream(name+"privatekey");
		byte[] encodedprivateKey = new byte[fileIn.available()];
		fileIn.read(encodedprivateKey);
		fileIn.close();

		KeyFactory keyFactory = KeyFactory.getInstance("DSA");
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(buf);
		PublicKey pubKey = keyFactory.generatePublic(pubKeySpec);

		PKCS8EncodedKeySpec keyspec = new PKCS8EncodedKeySpec(encodedprivateKey);
		PrivateKey priKey = keyFactory.generatePrivate(keyspec);

		
		KeyPair mykey = new KeyPair(pubKey, priKey);
		return mykey;

	}
    public PeerManager() throws Exception {
    	isMasterNode=true;
    	isRootNode=true;
        KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );
        KeyPair pair1 = gen.generateKeyPair();
        mKey=pair1;
 
        rKey = gen.generateKeyPair();
        WriteKey(rKey,"root");
        
    	peer = new PeerBuilderDHT(new PeerBuilder(pair1).ports(4000 ).start()).start();
    	peer.storageLayer().protection(  PROTECT, PROTECTMODE , PROTECT,
    			PROTECTMODE  );
        FutureBootstrap fb = peer.peer().bootstrap().inetAddress(InetAddress.getByName("127.0.0.1")).ports(4000).start();
        fb.awaitUninterruptibly();
        if (fb.isSuccess()) {
            peer.peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
        }

    }
    public PeerManager(String host) throws Exception {
    	initPeerManager(host,null,null);
    }
    public PeerManager(String host,KeyPair masterKey) throws Exception {
    	initPeerManager(host,masterKey,null);
    }   
    public PeerManager(String host,KeyPair masterKey,KeyPair rootKey) throws Exception {
    	initPeerManager(host,masterKey,rootKey);
    }   
    public PeerDHT peer()
    {
    	return peer;
    }
   
    void bootstrap1(PeerAddress pa)
    {
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
		FutureBootstrap futureBootstrap = peer.peer().bootstrap().peerAddress(pa).start();
		futureBootstrap.awaitUninterruptibly();
    }
    
    void bootstrap2(PeerAddress pa)
    {
    	FutureDiscover fd = peer.peer().discover().peerAddress(pa).start();
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
		}
    }
    
    private void initPeerManager(String host,KeyPair masterKey,KeyPair rootKey) throws Exception {
    	
        isMasterNode= (masterKey!=null);
        isRootNode= (rootKey!=null);
        mKey=masterKey;
        
    	KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );

       	KeyPair pair1 = gen.generateKeyPair();
       	peer= new PeerBuilderDHT(new PeerBuilder(pair1).ports(4000+rnd.nextInt()%10000).behindFirewall().start()).start();

       
        peer.storageLayer().protection(  PROTECT, PROTECTMODE , PROTECT,
    			PROTECTMODE  );
    	//System.out.println("Client started and Listening to: " + DiscoverNetworks.discoverInterfaces(b));
		System.out.println("address visible to outside is " + peer.peerAddress());

		InetAddress address = Inet4Address.getByName(host);
		int masterPort = 4000;
		PeerAddress pa = new PeerAddress(Number160.ZERO, address, masterPort, masterPort);

		System.out.println("PeerAddress: " + pa);
		bootstrap2(pa);
	
    }
    
    
    public boolean createdir(Number160 parent,String dirname,Number160 dir)throws IOException,NotMasterNodeException
    {
    	return createdir(parent,Number160.createHash(dirname),dir);
    }

    public boolean createrootdir(String dirname,Number160 dir)throws IOException,NotMasterNodeException
    {
    	return createrootdir(Number160.createHash(dirname),dir);
    }
    public boolean createdir(Number160 parent,Number160 dirname,Number160 dir)throws IOException,NotMasterNodeException
    {
    	return createdir(parent,dirname,dir,mKey);
    }

    public boolean createrootdir(Number160 dirname,Number160 dir)throws IOException,NotMasterNodeException
    {
    	return createdir(ROOT,dirname,dir,rKey);
    }
    
    public boolean createdir(Number160 parent,Number160 dirname,Number160 dir,KeyPair k)throws IOException,NotMasterNodeException
    {
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
    	FuturePut p;

		p = peer.put(parent).data(dirname,(new Data(dir).protectEntry(k))).keyPair(k).sign().domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();
	    if(!p.isSuccess())
	    	return false;
		p = peer.put(dir).data(Number160.ZERO,(new Data(DIR_MAGIC).protectEntry(k))).keyPair(k).sign().domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();	  
	    return p.isSuccess();
    }
    
    public Object getdir(Number160 parent,String dirname) throws ClassNotFoundException, IOException {
    	return getdir(parent,Number160.createHash(dirname));
    }
    
	public Object getdir(Number160 parent,Number160 dirname) throws ClassNotFoundException, IOException {
		FutureGet futureGet = peer.get(parent).domainKey(Number160.ZERO).contentKey(dirname).start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			return futureGet.data().object();
		}
		return null;
	}
    
    public DataMapReader readdir(Number160 parent)
    {
		FutureGet futureGet = peer.get(parent).domainKey(Number160.ZERO).all().start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			return new DataMapReader(parent,Number160.ZERO, futureGet.dataMap());
		}
		return null; 	
    }
    
    public boolean putdir(Number160 parent,String dirname,Object d)throws IOException,NotMasterNodeException
    {
    	return putdir(parent,Number160.createHash(dirname),d);
	}
    
    public boolean putdir(Number160 parent,Number160 dirname,Object d)throws IOException,NotMasterNodeException
    {
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
		FuturePut p = peer.put(parent).data(dirname,(new Data(d).protectEntry(mKey))).keyPair(mKey).sign().domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();   
	    return p.isSuccess();
    }
 
    public boolean deldirfile(Number160 dir,String filename)throws IOException,NotMasterNodeException
    {
    	return deldirfile(dir,Number160.createHash(filename));
    }
    public boolean deldirfile(Number160 dir,Number160 filename)throws IOException,NotMasterNodeException
    {
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
		FutureRemove p = peer.remove(dir).keyPair(mKey).contentKey(filename).sign().keyPair(mKey).domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();   
	    return p.isSuccess();
    }
    
    
    
    public Object get(String nm) throws ClassNotFoundException, IOException {
    	return get(Number160.createHash(nm));
    }
    
	public Object get(Number160 nm) throws ClassNotFoundException, IOException {
		FutureGet futureGet = peer.get(nm).domainKey(Number160.ZERO).contentKey(Number160.ONE).start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			return futureGet.data().object();
		}
		return null;
	}
	
	public boolean store(String name, Object d) throws IOException,NotMasterNodeException {
		return store(Number160.createHash(name),d);
	}
	
	public boolean remove(String name)throws IOException,NotMasterNodeException
	{
		return remove(Number160.createHash(name));
	}
	
	public boolean remove(Number160 name)throws IOException,NotMasterNodeException
	{
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
    	FutureRemove p;

		p = peer.remove(name).sign().keyPair(mKey).domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();
	    return p.isSuccess(); 	
	}
	
	public boolean store(Number160 name, Object d) throws IOException,NotMasterNodeException {
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
    	FuturePut p;

		p = peer.put(name).data(Number160.ONE,(new Data(d).protectEntry(mKey))).keyPair(mKey).sign().domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();
	    return p.isSuccess();
    }
}
