package com.DHTWeb;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.Map.Entry;
import java.util.Set;

import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.ConnectionBean;
import net.tomp2p.connection.DSASignatureFactory;
import net.tomp2p.connection.PeerBean;
import net.tomp2p.connection.PeerConnection;
import net.tomp2p.connection.Responder;
import net.tomp2p.connection.SignatureFactory;
import net.tomp2p.dht.EvaluatingSchemeDHT;
import net.tomp2p.dht.FutureDigest;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageLayer;
import net.tomp2p.dht.StorageLayer.ProtectionEnable;
import net.tomp2p.dht.StorageLayer.ProtectionMode;
import net.tomp2p.dht.StorageMemory;
import net.tomp2p.dht.StorageRPC;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.futures.FuturePeerConnection;
import net.tomp2p.message.Message;
import net.tomp2p.message.Message.Type;
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
import net.tomp2p.replication.IndirectReplication;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;
import java.io.File;

import org.mapdb.DB;
import org.mapdb.DBMaker;

import net.tomp2p.connection.DSASignatureFactory;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.StorageDisk;
import net.tomp2p.dht.Storage;
import net.tomp2p.rpc.DigestResult;
import net.tomp2p.rpc.ObjectDataReply;
import net.tomp2p.rpc.RPC;

public class PeerManager {
	
	final Random rnd = new Random( (new Date()).getTime() );
	static final ProtectionEnable PROTECT=ProtectionEnable.ALL;
	static final ProtectionMode PROTECTMODE=ProtectionMode.MASTER_PUBLIC_KEY;
	public static final Number160 ROOT=Number160.createHash(10086);
	public static final Number160 ROOT_PEERS=Number160.createHash(54749110);
	public static final Number160 MASTER_PEERS=Number160.createHash("MASTER_PEERS_HASH");
	public static final Number160 MAX_BLOCK_ID=Number160.createHash(2);
	
	public static final Number160 DIR_MAGIC=Number160.ONE;	

	private static final DSASignatureFactory factory = new DSASignatureFactory();

	private File DIR;
	private PeerDHT peer;
	private RootPeer rootpeer;
	KeyPair mKey;
	KeyPair rKey;
	Key rLockKey;
	boolean isMasterNode;
	boolean isRootNode;
	KeyEvaluationScheme masterevaluationScheme;
	KeyEvaluationScheme rootevaluationScheme;
	Random rand=new Random();
	ObjectDataReply replylistener;
	IndirectReplication rep;
	
	PeerAddress lastmaster;
	PeerAddress lastroot;
	
	private int MAX_BLOCK=0;
	
	public class NotMasterNodeException extends Exception{}
	public class SendFailException extends Exception{}
	public class DataCorruptException extends Exception{}	
	
	public static class KeyReply implements Serializable
	{

		/**
		 * 
		 */
		private static final long serialVersionUID = -7699614895862776493L;
		PublicKey m;
		PublicKey r;
		
	}


	
	
	final static int KEY_LENGTH=443;
	static KeyReply CreateMyKeys(PublicKey master, PublicKey root) {
		KeyReply r=new KeyReply();
		r.m=master;
		r.r=root;
		return r;
	}
	
	static PublicKey DecodePublicKey(byte[] buf,int pos) throws NoSuchAlgorithmException, InvalidKeySpecException
	{
		byte[] m = new byte[KEY_LENGTH];
		System.arraycopy(buf, pos*KEY_LENGTH, m, 0,KEY_LENGTH);
		return GetPublicKey(m);
	}

	public static PublicKey GetPublicKey(byte[] m) throws NoSuchAlgorithmException,
			InvalidKeySpecException {
		KeyFactory keyFactory = KeyFactory.getInstance("DSA");
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(m);
		return keyFactory.generatePublic(pubKeySpec);
	}
	
	public ServerReply newServerReply()
	{
		return new ServerReply();
	}
	
	public class ServerReply implements ObjectDataReply {
		@Override
		public Object reply(PeerAddress sender, Object request) throws Exception {
			if(request.equals("Hello"))
			{
				return CreateMyKeys(mKey.getPublic(),rKey.getPublic());
			}
			else if(replylistener!=null)
				return replylistener.reply(sender, request);
			else
				return null;
		}
	}
		
	public static class PeerStorageLayer extends StorageLayer{
		public PeerStorageLayer(Storage backend) {
			super(backend);
			// TODO Auto-generated constructor stub
		}

		@Override
		public Map<Number640, Enum<?>> putAll(final NavigableMap<Number640, Data> dataMap, PublicKey publicKey, boolean putIfAbsent,
		        boolean domainProtection, boolean sendSelf) {
			final Map<Number640, Enum<?>> retVal = new HashMap<Number640, Enum<?>>();
			for(Map.Entry<Number640, Data> entry: dataMap.entrySet())
			{
				retVal.put(entry.getKey(), PutStatus.FAILED);
			}
			return retVal;
		}

		
	}
	public static class MyStorage extends PeerFileStorage {
		
		public MyStorage(DB db, Number160 peerId, File path,
				SignatureFactory signatureFactory,
				int storageCheckIntervalMillis) {
			super(db, peerId, path, signatureFactory, storageCheckIntervalMillis);
		}

		@Override
		public Data put(Number640 key, Data value)
	    {
	    	if(value.hasPublicKey())
	    	{
	    		try {
					if(!value.verify(factory))
					{
						System.out.println("Putting a value with a bad key");
						return value;
					}
				} catch (InvalidKeyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return value;
				} catch (SignatureException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return value;
				}
	    	}
		      
	        return super.put(key, value);
	    }
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
	
	
	public static Storage make_storage(Number160 peerid)
	{
		File dir = new File("PeerStorage");
		if(!dir.exists())
			dir.mkdirs();
		File DIR=new File(dir,peerid.toString());
		if(!DIR.exists())
			DIR.mkdirs();	
		System.out.println(peerid);

		return new MyStorage(
				DBMaker.newFileDB(new File(DIR, "DHTWeb")).closeOnJvmShutdown().make(),
				peerid,DIR,new net.tomp2p.connection.DSASignatureFactory(),60 * 1000);
	}
	
	
	/** 
	 * Write the key pair to a file pair named name+"publickey" and name+"privatekey"
	 @param k 
	 The key
	 @param name 
	 The file name
	*/ 	
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
	
	/**
	 * Read the key pair from the file named name+"publickey"
	 * @param name
	 * The file name
	 * @return
	 * @throws Exception
	 */
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
	public static KeyPair ReadPublicKey(String name) throws Exception {
		FileInputStream out;

		out = new FileInputStream(name+"publickey");
		byte[] buf = new byte[out.available()];
		out.read(buf);
		out.close();


		KeyFactory keyFactory = KeyFactory.getInstance("DSA");
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(buf);
		PublicKey pubKey = keyFactory.generatePublic(pubKeySpec);
		
		KeyPair mykey = new KeyPair(pubKey, null);
		return mykey;

	}
	
	

	
	/**
	 * Start a new node with existing keys, no bootstrap
	 * @param mkey
	 * @param rkey
	 * @param pw
	 * @param lsr
	 * @throws Exception
	 */
    public PeerManager(KeyPair mkey,KeyPair rkey,String pw,ObjectDataReply lsr) throws Exception {
    	isMasterNode=true;
    	isRootNode=true;
       
        mKey = mkey;
        rKey = rkey;
        rLockKey=MyCipher.toKey(pw);
        replylistener=lsr;
        
        rootpeer=new RootPeer(factory,rKey,rLockKey,this);
        
        Bindings b = new Bindings();
        byte[] id=new byte[20];
        rand.nextBytes(id);
        Number160 peer2Owner = Utils.makeSHAHash(id);
        Number160 publick=Utils.makeSHAHash(mKey.getPublic().getEncoded());
        System.out.println("Root Mode");
        Peer pr=new PeerBuilder(peer2Owner).ports(4000).bindings(b).start();
        pr.objectDataReply(new ServerReply());
    	peer = new PeerBuilderDHT(pr).storage(make_storage(publick)).start();
    	peer.storageLayer().protection(  PROTECT, PROTECTMODE , PROTECT,
    			PROTECTMODE  );
    	System.out.println("My public key is "+publick);
    	System.out.println("My public peerID is "+peer.peerID());
        FutureBootstrap fb = peer.peer().bootstrap().inetAddress(InetAddress.getByName("127.0.0.1")).ports(4000).start();
        fb.awaitUninterruptibly();
        if (fb.isSuccess()) {
            peer.peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
        }
        masterevaluationScheme=new KeyEvaluationScheme(mKey.getPublic(),factory);
        rootevaluationScheme=new KeyEvaluationScheme(rKey.getPublic(),factory);
        System.out.println(pr.peerAddress());
        rep=new IndirectReplication(peer).start();
        
       	putdir(ROOT_PEERS,peer.peerID(),peer.peerAddress(),rKey);
       	putdir(MASTER_PEERS,peer.peerID(),peer.peerAddress());   
       	Integer mb=(Integer)getdir(ROOT,MAX_BLOCK_ID);
       	MAX_BLOCK=mb;
       	System.out.println("MAX_BLOCK = "+MAX_BLOCK);
    }
	
	/**
	 * Creates a new root node from scratch, no bootstrap
	 * @param pw
	 * @param lsr
	 * @throws Exception
	 */
    public PeerManager(String pw,ObjectDataReply lsr,int maxblock) throws Exception {
    	isMasterNode=true;
    	isRootNode=true;
        KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );
        KeyPair pair1 = gen.generateKeyPair();
        mKey=pair1;
        rKey = gen.generateKeyPair();
        rLockKey=MyCipher.toKey(pw);
        replylistener=lsr;
        MAX_BLOCK=maxblock;
        
    	rootpeer=new RootPeer(factory,rKey,rLockKey,this);
        
        Bindings b = new Bindings();
        Number160 peer2Owner = Utils.makeSHAHash( pair1.getPublic().getEncoded() );
        Peer pr=new PeerBuilder(pair1).ports(4000).bindings(b).start();
        pr.objectDataReply(new ServerReply());
    	peer = new PeerBuilderDHT(pr).storage(make_storage(peer2Owner)).start();
    	peer.storageLayer().protection(  PROTECT, PROTECTMODE , PROTECT,
    			PROTECTMODE  );
    	System.out.println("My public key is "+peer2Owner);
    	System.out.println("My public peerID is "+peer.peerID());
        FutureBootstrap fb = peer.peer().bootstrap().inetAddress(InetAddress.getByName("127.0.0.1")).ports(4000).start();
        fb.awaitUninterruptibly();
        if (fb.isSuccess()) {
            peer.peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
        }
        masterevaluationScheme=new KeyEvaluationScheme(mKey.getPublic(),factory);
        rootevaluationScheme=new KeyEvaluationScheme(rKey.getPublic(),factory);
        rep=new IndirectReplication(peer).start();
        
       	putdir(ROOT_PEERS,peer.peerID(),peer.peerAddress(),rKey);
       	putdir(MASTER_PEERS,peer.peerID(),peer.peerAddress());  
       	putdir(ROOT,MAX_BLOCK_ID,new Integer(MAX_BLOCK),rKey);
       	System.out.println("MAX_BLOCK = "+MAX_BLOCK);
    }
    
    /**
     * Start a peer node
     * @param host
     * @param dummy
     * @throws Exception
     */
    public PeerManager(String host,int dummy) throws Exception {
    	initPeerManager(host,null,null,null,null);
    }
    
    /**
     * Start a master node
     * @param host
     * @param masterKey
     * @param lsr
     * @throws Exception
     */
    public PeerManager(String host,KeyPair masterKey,ObjectDataReply lsr) throws Exception {
    	initPeerManager(host,masterKey,null,null,lsr);
    }   
    
    /**
     * Start a root node
     * @param host
     * @param masterKey
     * @param rootKey
     * @param pw
     * @param lsr
     * @throws Exception
     */
    public PeerManager(String host,KeyPair masterKey,KeyPair rootKey,String pw,ObjectDataReply lsr) throws Exception {
    	initPeerManager(host,masterKey,rootKey,pw,lsr);
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
    
    static public PeerAddress bootstrap2(PeerDHT peer,PeerAddress pa)
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
			return null;
		} else {
			System.out.println("*** SUCCESSFUL BOOTSTRAP");
			return bootstrap.bootstrapTo().iterator().next();
		}
		
    }
    
    private void initPeerManager(String host,KeyPair masterKey,KeyPair rootKey,String pw,ObjectDataReply lsr) throws Exception {
    	
        isMasterNode= (masterKey!=null);
        isRootNode= (rootKey!=null);
        if(isRootNode && !isMasterNode)
        	throw new NotMasterNodeException();
        mKey=masterKey;
        rKey=rootKey;
        Bindings b = new Bindings();
    	KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );
    	if(pw!=null)
    		rLockKey=MyCipher.toKey(pw);
    	if(isRootNode)
    		rootpeer=new RootPeer(host,factory,rKey,rLockKey,this);
    	
    	replylistener=lsr;
       	KeyPair pair1 = gen.generateKeyPair();
       	Number160 peer2Owner = Utils.makeSHAHash( pair1.getPublic().getEncoded() );
       	int port=isMasterNode?0:rnd.nextInt()%10000;
       		
       	PeerBuilderDHT builder= new PeerBuilderDHT(new PeerBuilder(pair1).bindings(b).ports(4000+port).behindFirewall().start());
       	if(isMasterNode)
       	{
       		Number160 publick=Utils.makeSHAHash(mKey.getPublic().getEncoded());
       		builder.peer().objectDataReply(new ServerReply());
       		builder.storage(make_storage(publick));
       	}
       	else
       	{
       		System.out.println("Client Mode");
       		builder.storage(new StorageMemory());
       		//builder.storageLayer(new PeerStorageLayer(builder.storage())); //fix-me : should it be used?
       	}

       	peer=builder.start();
    	System.out.println("My public key is "+peer2Owner);
    	System.out.println("My public peerID is "+peer.peerID());
        peer.storageLayer().protection(  PROTECT, PROTECTMODE , PROTECT,
    			PROTECTMODE  );
    	//System.out.println("Client started and Listening to: " + DiscoverNetworks.discoverInterfaces(b));
		System.out.println("address visible to outside is " + peer.peerAddress());

		InetAddress address = Inet4Address.getByName(host);
		int masterPort = 4000;
		PeerAddress pa = new PeerAddress(Number160.ZERO, address, masterPort, masterPort);

		System.out.println("PeerAddress: " + pa);
		PeerAddress remote=bootstrap2(peer,pa);
		Peer pr=peer.peer();
		if(!isMasterNode)
       	{
			FutureDirect fd=pr.sendDirect(remote).object("Hello").start().awaitUninterruptibly();
			if(fd.isSuccess())
			{
				KeyReply kys=(KeyReply)fd.object();
				mKey= new KeyPair(kys.m,null);
				rKey= new KeyPair(kys.r,null);
			}
			else
			{
				System.out.println(fd.failedReason());
				throw new Exception("Error getting keys");
			}
			rep=new IndirectReplication(peer).start();
       		//mKey=ReadPublicKey("master_");
       	}
		if(rKey==null)
			rKey=PeerManager.ReadPublicKey("root_");
       	masterevaluationScheme=new KeyEvaluationScheme(mKey.getPublic(),factory);
       	rootevaluationScheme=new KeyEvaluationScheme(rKey.getPublic(),factory);
       	if(isRootNode)
       	{
       		putdir(ROOT_PEERS,peer.peerID(),peer.peerAddress(),rKey);
       	}
       	if(isMasterNode)
       		putdir(MASTER_PEERS,peer.peerID(),peer.peerAddress());  
       	Integer mb=(Integer)getdir(ROOT,MAX_BLOCK_ID);
       	MAX_BLOCK=mb;
       	System.out.println("MAX_BLOCK = "+MAX_BLOCK);
    }
    
    
    static class LargeDataHead implements Serializable
    {

		/**
		 * 
		 */
		private static final long serialVersionUID = 5837642652194789775L;
    	int cnt;
    	int len;
    	byte[] md5hash;
    	LargeDataHead(byte[] data,long maxblock)
    	{
    		len=data.length;
    		md5hash=Utils.makeMD5Hash(data);
    		cnt=(int) ((data.length / maxblock) + (data.length % maxblock==0?0:1))+1;
    	}
    	
    }
    
    /**
     * Put a big data into DHT, protected by master key
     * @param name
     * @param d
     * @param key
     * @return
     * @throws Exception
     */
    public boolean putdirbig(Number160 name,Object d)throws Exception
    {
    	return putdirbig(name,d,mKey);
    }
    
    /**
     * Put a big data into DHT
     * @param name
     * @param d
     * @param key
     * @return
     * @throws Exception
     */
    public boolean putdirbig(Number160 name,Object d,KeyPair key)throws Exception
    {
    	if(!isMasterNode)
    		throw new NotMasterNodeException();
		byte[] buf=Utils.encodeJavaObject(d);
		
		LargeDataHead head=new LargeDataHead(buf,MAX_BLOCK);
		FuturePut[] puts=new FuturePut[head.cnt];
		puts[0] = peer.put(name).data(Number160.ZERO,(new Data(head).protectEntryNow(key,factory).sign(key.getPrivate()))).keyPair(key).domainKey(Number160.ZERO).start();
	    byte[] tosend=new byte[MAX_BLOCK];
	    DummyCryptObj sobj=new DummyCryptObj();
	    sobj.by=tosend;
	    int i;
		for(i=1;i<head.cnt-1;i++)
	    {	
	    	System.arraycopy(buf, (i-1)*MAX_BLOCK, tosend, 0, MAX_BLOCK);
	    	puts[i] = peer.put(name).data(new Number160(i),(new Data(sobj).protectEntryNow(key,factory).sign(key.getPrivate()))).keyPair(key).domainKey(Number160.ZERO).start();
	    }
		int lastpac=buf.length-(i-1)*MAX_BLOCK;
		tosend=new byte[lastpac];
		sobj.by=tosend;
		System.arraycopy(buf, (i-1)*MAX_BLOCK, tosend, 0, lastpac);
		puts[i]= peer.put(name).data(new Number160(i),(new Data(sobj).protectEntryNow(key,factory).sign(key.getPrivate()))).keyPair(key).domainKey(Number160.ZERO).start();
		
	    for(i=0;i<head.cnt;i++)
	    {
	    	puts[i].awaitUninterruptibly();
	    	if(!puts[i].isSuccess())
	    		return false;
	    }
	    return true;
    }
    
    /**
     * Get the big object in the dir name, protected by master key.
     * @param parent
     * @param name
     * @param d
     * @return
     * @throws IOException 
     * @throws ClassNotFoundException 
     * @throws DataCorruptException 
     * @throws Exception
     */
    public Object getdirbig(Number160 name) throws ClassNotFoundException, IOException, DataCorruptException
    {
    	return getdirbig(name,mKey);
    }
    
    void print(byte[] buf)
    {
    	for(int i=0;i<buf.length;i++)
    	{
    		System.out.print(buf[i]+",");
    	}
    	System.out.println();
    }
    
    /**
     * Get the big object in the dir name, protected by key.
     * @param parent
     * @param name
     * @param d
     * @return
     * @throws IOException 
     * @throws ClassNotFoundException 
     * @throws DataCorruptException 
     * @throws Exception
     */
    public Object getdirbig(Number160 name,KeyPair key) throws ClassNotFoundException, IOException, DataCorruptException
    {
		FutureGet futureGet = peer.get(name).domainKey(Number160.ZERO).all().start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			Map<Number640,Data> map=null;
			if(key==mKey)
			{
				map=getmap(futureGet,masterevaluationScheme);
			}
			else if(key==rKey)
			{
				map=getmap(futureGet,rootevaluationScheme);
			}
			DataMapReader mapper=new DataMapReader(name,Number160.ZERO,map);
			LargeDataHead head=(LargeDataHead)mapper.get(Number160.ZERO).object();
			byte[] buf=new byte[head.len];
			for(int i=1;i<head.cnt;i++)
			{
				DummyCryptObj sobj=(DummyCryptObj)mapper.get(new Number160(i)).object();
				byte[] slice=sobj.by;
				System.arraycopy(slice, 0, buf, (i-1)*MAX_BLOCK, Math.min(MAX_BLOCK,slice.length));
			}
			if(Arrays.equals(head.md5hash, Utils.makeMD5Hash(buf)))
				return Utils.decodeJavaObject(buf, 0, head.len);
			else
				throw new DataCorruptException();
		}
		return null;
    }
    
    
    /**
     * put the object in the dir, protected by root key, encrypted by root lock key.
     * @param parent
     * @param name
     * @param d
     * @return
     * @throws Exception
     */
    public boolean rootputdir(Number160 parent,String name,Object d)throws Exception
    {
    	return rootputdir(parent,Number160.createHash(name),d);
    }
  
    

    /**
     * put the object in the dir, protected by root key, encrypted by root lock key.
     * @param parent
     * @param name
     * @param d
     * @return
     * @throws Exception
     */
    public boolean rootputdir(Number160 parent,Number160 name,Object d)throws Exception
    {
    	if(!isRootNode)
    		throw new NotMasterNodeException();
		byte[] buf=MyCipher.encrypt(d, rLockKey);
		DummyCryptObj b=new DummyCryptObj();
		b.by=buf;
		FuturePut p = rootpeer.rpeer.put(parent).data(name,(new Data(b).protectEntryNow(rKey,factory).sign(rKey.getPrivate()))).keyPair(rKey).domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly(); 
	    return p.isSuccess();
    }
    
    /**
     * Get the object in the dir, protected by root key, encrypted by root lock key.
     * @param parent
     * @param name
     * @param d
     * @return
     * @throws Exception
     */
    public Object rootgetdir(Number160 parent,Number160 name)throws Exception
    {
    	if(!isRootNode)
    		throw new NotMasterNodeException();
		FutureGet futureGet = rootpeer.rpeer.get(parent).domainKey(Number160.ZERO).contentKey(name).start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			DummyCryptObj buf=(DummyCryptObj)getdata(futureGet,rootevaluationScheme).object();
			return MyCipher.decryptobj(buf.by, rLockKey);
		}
		return null;
    }
       
    /**
     * Get the object in the dir, protected by root key, encrypted by root lock key.
     * @param parent
     * @param name
     * @param d
     * @return
     * @throws Exception
     */
    public Object rootgetdir(Number160 parent,String name)throws Exception
    {
    	return rootgetdir(parent,Number160.createHash(name));
    }
    
    
    /**
     * Create a directory in a non-root directory
     * @param parent
     * @param dirname
     * Directory name in string, will be hashed into Number160
     * @param dir
     * The new directory's id
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public boolean createdir(Number160 parent,String dirname,Number160 dir)throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException
    {
    	return createdir(parent,Number160.createHash(dirname),dir);
    }

    /**
     * Create a directory in a non-root directory
     * @param parent
     * @param dirname
     * Directory name in Number160
     * @param dir
     * The new directory's id
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public boolean createdir(Number160 parent,Number160 dirname,Number160 dir)throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException
    {
    	return createdir(parent,dirname,dir,mKey);
    }
    
    /**
     * Create a directory in root directory
     * @param dirname
     * The new directory name 
     * @param dir
     * The new directory id
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public boolean createrootdir(String dirname,Number160 dir)throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException
    {
    	return createrootdir(Number160.createHash(dirname),dir);
    }


    /**
     * Create a directory in root directory
     * @param dirname
     * The new directory name in Number160
     * @param dir
     * The new directory id
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public boolean createrootdir(Number160 dirname,Number160 dir)throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException
    {
    	return createdir(ROOT,dirname,dir,rKey);
    }
    
    /**
     * Create a directory with a specified key
     * @param parent
     * @param dirname
     * The new directory name
     * @param dir
     * @param k
     * The key pair that protects the directory entry
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public boolean createdir(Number160 parent,Number160 dirname,Number160 dir,KeyPair k)throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException
    {
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
    	FuturePut p;
    	k.getPublic().getEncoded();
		p = peer.put(parent).data(dirname,(new Data(dir).protectEntryNow(k,factory).sign(k))).keyPair(k).sign().domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();
	    if(!p.isSuccess())
	    	return false;
		p = peer.put(dir).data(Number160.ZERO,(new Data(DIR_MAGIC).protectEntryNow(k,factory).sign(k))).keyPair(k).sign().domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();	  
	    return p.isSuccess();
    }
    
    /**
     * Get a specified content (a directory or an object) in a directory
     * @param parent
     * The directory
     * @param name
     * The content's name, will be hashed to Number160
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public Object getdir(Number160 parent,String name) throws ClassNotFoundException, IOException {
    	return getdir(parent,Number160.createHash(name));
    }
    
    /**
     * Get a specified content (a directory or an object) in a directory
     * @param parent
     * The directory
     * @param name
     * The content's name
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
	public Object getdir(Number160 parent,Number160 name) throws ClassNotFoundException, IOException {
		FutureGet futureGet = peer.get(parent).domainKey(Number160.ZERO).contentKey(name).start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			if(parent.equals(ROOT) || parent.equals(ROOT_PEERS))
				return getdata(futureGet,rootevaluationScheme).object();
			else
				return getdata(futureGet,masterevaluationScheme).object();
		}
		return null;
	}
    
	/**
	 * Get all the contents of a directory into a map
	 * @param parent
	 * @return
	 */
    public DataMapReader readdir(Number160 parent)
    {
		FutureGet futureGet = peer.get(parent).domainKey(Number160.ZERO).all().start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			if(parent.equals(ROOT) || parent.equals(ROOT_PEERS))
				return new DataMapReader(parent,Number160.ZERO, getmap(futureGet,rootevaluationScheme));
			else
				return new DataMapReader(parent,Number160.ZERO, getmap(futureGet,masterevaluationScheme));
		}
		return null; 	
    }
    
    /**
     * Put the content to the directory "parent" with entry name "name"
     * @param parent
     * @param name
     * The entry's name, will be hashed to Number160
     * @param d
     * The object to be put
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public boolean putdir(Number160 parent,String name,Object d)throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException
    {
    	return putdir(parent,Number160.createHash(name),d);
	}
 
    
    public boolean putdir(Number160 parent,Number160 dirname,Object d)throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException
    {
    	return putdir(parent,dirname,d,mKey);
    }
    
    
    /**
     * Put the content to the directory "parent" with entry name "name" protected by k
     * @param parent
     * @param name
     * The entry's name
     * @param d
     * The object to be put
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    public boolean putdir(Number160 parent,Number160 dirname,Object d,KeyPair k)throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException
    {
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
		FuturePut p = peer.put(parent).data(dirname,(new Data(d).protectEntryNow(k,factory).sign(k.getPrivate()))).domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();   
	    return p.isSuccess();
    }
 
    
    /**
     * Delete an entry in the directory
     * @param dir
     * The directory's id
     * @param filename
     * The entry name to be deleted, will be hashed to Number160
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     */
    public boolean deldirfile(Number160 dir,String filename)throws IOException,NotMasterNodeException
    {
    	return deldirfile(dir,Number160.createHash(filename));
    }
    
    /**
     * Delete an entry in the directory
     * @param dir
     * @param filename
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     */
    public boolean deldirfile(Number160 dir,Number160 filename)throws IOException,NotMasterNodeException
    {
    	return deldirfile(dir,filename,mKey);
    }
    
    /**
     * Delete an entry in the directory protected by k
     * @param dir
     * The directory's id
     * @param filename
     * The entry name to be deleted
     * @return
     * @throws IOException
     * @throws NotMasterNodeException
     */
    public boolean deldirfile(Number160 dir,Number160 filename,KeyPair k)throws IOException,NotMasterNodeException
    {
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
		FutureRemove p = peer.remove(dir).keyPair(k).contentKey(filename).sign().keyPair(k).domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();   
	    return p.isSuccess();
    }
    
    
	/**
	 * Get an object directly at index "name"
	 * @param name
	 * The index of the object, will be hashed into Number160
	 * @return
	 * @throws IOException
	 * @throws NotMasterNodeException
	 */	 
    public Object get(String nm) throws ClassNotFoundException, IOException {
    	return get(Number160.createHash(nm));
    }
    
	/**
	 * Get an object directly at index "name"
	 * @param name
	 * The index of the object
	 * @return
	 * @throws IOException
	 * @throws NotMasterNodeException
	 */	
	public Object get(Number160 nm) throws ClassNotFoundException, IOException {
		FutureGet futureGet = peer.get(nm).domainKey(Number160.ZERO).contentKey(Number160.ONE).start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			return getdata(futureGet,masterevaluationScheme).object();
		}
		return null;
	}
	
	
	
	private static Map<Number640, Data> getmap(FutureGet futureGet,EvaluatingSchemeDHT evaluationScheme)
	{
		return evaluate(evaluationScheme,futureGet.rawData());
	}
	
    private static Data getdata(FutureGet futureGet,EvaluatingSchemeDHT evaluationScheme) {
    	Map<Number640, Data> dataMap= getmap(futureGet,evaluationScheme);
        if (dataMap.size() == 0) {
            return null;
        }
        return dataMap.values().iterator().next();
    }
    
    private static Map<Number640, Data> evaluate(EvaluatingSchemeDHT evaluationScheme,Map<PeerAddress, Map<Number640, Data>> rawKeys) {
        return evaluationScheme.evaluate2(rawKeys);
    }

	/**
	 * Remove an object directly at index "name"
	 * @param name
	 * The index of the object, which will be hashed into Number160
	 * @return
	 * @throws IOException
	 * @throws NotMasterNodeException
	 */	
	public boolean remove(String name)throws IOException,NotMasterNodeException
	{
		return remove(Number160.createHash(name));
	}

	
	/**
	 * Get the dir id of a path
	 * @param opath
	 * The directory's path, separated by '/'
	 * @return
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
    public Number160 dirid(String opath) throws ClassNotFoundException, IOException{
    	String[] path;
    	path=opath.split("/");
		Number160 id=(Number160)getdir(PeerManager.ROOT, path[0]);
		for (int i=1;i<path.length-1;i++)
		{
			id=(Number160)getdir(id,path[i]);
		}
    	return id;
    }
	
	
	
	/**
	 * Remove an object directly at index "name"
	 * @param name
	 * The index of the object
	 * @return
	 * @throws IOException
	 * @throws NotMasterNodeException
	 */	
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
	
	/**
	 * Store an object directly at index "name"
	 * @param name
	 * The index of the object, which will be hashed into Number160
	 * @param d
	 * The object to be stored
	 * @return
	 * @throws IOException
	 * @throws NotMasterNodeException
	 * @throws SignatureException 
	 * @throws InvalidKeyException 
	 */	
	public boolean store(String name, Object d) throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException {
		return store(Number160.createHash(name),d);
	}
	
	
	/**
	 * Store an object directly at index "name"
	 * @param name
	 * The index of the object
	 * @param d
	 * The object to be stored
	 * @return
	 * @throws IOException
	 * @throws NotMasterNodeException
	 * @throws SignatureException 
	 * @throws InvalidKeyException 
	 */
	public boolean store(Number160 name, Object d) throws IOException,NotMasterNodeException, InvalidKeyException, SignatureException {
    	if(!isMasterNode)
    	{
    		NotMasterNodeException e=new NotMasterNodeException();
    		throw e;
    	}
    	FuturePut p;
		p = peer.put(name).data(Number160.ONE,(new Data(d).protectEntryNow(mKey,factory).sign(mKey.getPrivate()))).keyPair(mKey).sign().domainKey(Number160.ZERO).start();
	    p.awaitUninterruptibly();
	    return p.isSuccess();
    }
	
	/**
	 * Stop the peer
	 */
	public void exit() {
		System.out.println("Exiting...");
		try {
			if (isRootNode) {
				deldirfile(ROOT_PEERS, peer.peerID(), rKey);
				rootpeer.exit();
			}
			if(isMasterNode)
			{
				deldirfile(MASTER_PEERS, peer.peerID());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotMasterNodeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		peer.shutdown().awaitUninterruptibly();
	}

	/**
	 * Call a master, passing an object
	 * @param o
	 * @return
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public Object mastercall(Object o) throws ClassNotFoundException, IOException
	{
		Object ret=null;
		if(lastmaster !=null)
		{
			try {
				ret=docall(lastmaster,o);
				return ret;
			} catch (SendFailException e) {}
		}
		lastmaster=null;
		for (Entry<Number640, Data> entry : readdir(PeerManager.MASTER_PEERS).m.entrySet()) {
			Object pa=entry.getValue().object();
			if(pa.getClass()!=PeerAddress.class)
				continue;
			try {
				ret=docall((PeerAddress)pa,o);
			} catch (SendFailException e) {
				continue;
			}	
			lastmaster=(PeerAddress)pa;
			return ret;
		}
		return ret;
	}
	
	/**
	 * Call a root node, passing an object
	 * @param o
	 * @return
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public Object rootcall(Object o) throws ClassNotFoundException, IOException
	{
		Object ret=null;
		if(lastroot !=null)
		{
			
			try {
				ret=docall(lastroot,o);
				return ret;
			} catch (SendFailException e) {}
			
		}
		lastroot=null;
		for (Entry<Number640, Data> entry : readdir(PeerManager.ROOT_PEERS).m.entrySet()) {
			Object pa=entry.getValue().object();
			if(pa.getClass()!=PeerAddress.class)
				continue;
			try {
				ret=docall((PeerAddress)pa,o);
			} catch (SendFailException e) {
				continue;
			}	
			lastroot=(PeerAddress)pa;
			return ret;				
		}
		return ret;
	}
	
	/**
	 * Returns if I am a master.
	 * @return
	 */
	public boolean MasterNode()
	{
		return isMasterNode;
	}
	
	/**
	 * Put a file from disk into DHT 
	 * @param dir
	 * The directory id
	 * @param name
	 * The file name, in string
	 * @param path
	 * The path to an existing disk file to be put
	 * @return
	 * @throws IOException
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 * @throws NotMasterNodeException
	 */
	public boolean putfile(Number160 dir,String name,String path) throws IOException, InvalidKeyException, SignatureException, NotMasterNodeException
	{
		return putfile(dir,Number160.createHash(name),path);
	}
	
	/**
	 * Put a file from disk into DHT 
	 * @param dir
	 * The directory id
	 * @param name
	 * The file name
	 * @param path
	 * The path to an existing disk file to be put
	 * @return
	 * @throws IOException
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 * @throws NotMasterNodeException
	 */
	public boolean putfile(Number160 dir,Number160 name,String path) throws IOException, InvalidKeyException, SignatureException, NotMasterNodeException
	{
    	InputStream fis = null;  
        fis = new FileInputStream(new File(path));  
        byte[] buff = new byte[fis.available()];  
        fis.read(buff);
        fis.close();
        return putdir(dir, name, buff) ;
   	}
	
	private Object docall(PeerAddress pa,Object o) throws ClassNotFoundException, IOException, SendFailException 
	{
		FutureDirect fd=peer.peer().sendDirect(pa).object(o).start().awaitUninterruptibly();
		if(fd.isSuccess())
		{
			return fd.object();
		}
		throw new SendFailException();
		
	}
	
	
	public  Map<PeerAddress,Map<Number640,Data>>  digest(Number160 dir)
	{
		FutureGet futureGet = peer.get(dir).domainKey(Number160.ZERO).all().start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			return futureGet.rawData();
		}
		return null; 	
	}
	
	@Override
	protected void finalize()
	{
		exit();
		try {
			super.finalize();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
