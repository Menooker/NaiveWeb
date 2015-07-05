package com.DHTWeb;

import java.io.IOException;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

import com.DHTWeb.PeerManager.ServerReply;
import net.tomp2p.connection.RequestHandler;
import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.ChannelCreator;
import net.tomp2p.connection.ConnectionBean;
import net.tomp2p.connection.DSASignatureFactory;
import net.tomp2p.connection.PeerBean;
import net.tomp2p.connection.PeerConnection;
import net.tomp2p.connection.Responder;
import net.tomp2p.dht.GetBuilder;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.PutBuilder;
import net.tomp2p.dht.Storage;
import net.tomp2p.dht.StorageLayer;
import net.tomp2p.dht.StorageRPC;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.futures.FutureResponse;
import net.tomp2p.message.DataMap;
import net.tomp2p.message.KeyCollection;
import net.tomp2p.message.Message;
import net.tomp2p.message.Message.Type;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.RPC;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;



public class RootPeer {
	static public class RootGetKey implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 7804902644165902005L;
		byte[] data;
	}
	DSASignatureFactory factory;
	KeyPair rKey;
	Key rLockKey;
	PeerDHT rpeer;
	PeerManager pm;
	final Random rnd = new Random( 42L );
	
	class RootStorageRPC extends StorageRPC
	{

		public RootStorageRPC(PeerBean peerBean, ConnectionBean connectionBean,
				StorageLayer storageLayer) {
			super(peerBean, connectionBean, storageLayer);
			// TODO Auto-generated constructor stub
		}
		
		@Override
		 public FutureResponse put(final PeerAddress remotePeer, final PutBuilder putBuilder,
		            final ChannelCreator channelCreator) {
			return super.put(remotePeer, putBuilder, channelCreator);
		}
		
		@Override
	    public void handleResponse(final Message message, PeerConnection peerConnection, final boolean sign,
	            Responder responder) throws Exception {
			if (message.command() == RPC.Commands.GET.getNr() 
					|| message.command() == RPC.Commands.GET_LATEST.getNr()
					||message.command() == RPC.Commands.GET_LATEST_WITH_DIGEST.getNr()) {
				Data dt=message.dataMap(0).dataMap().get(Number640.ZERO);
	            if(dt!=null && !dt.isEmpty() && dt.object().getClass()==PeerAddress.class && dt.verify(rKey.getPublic(), factory))
	            {
	            	PeerAddress id=(PeerAddress)dt.object();           	
	            	if(peerConnection==null || id.equals(peerConnection.remotePeer())) //peerConnection=null -> local call
	            	{
	            		super.handleResponse(message, peerConnection, sign, responder);
	            		return;
	            	}
	            }
	            else
	            {
	            	System.out.println("Get request denied");
	            	responder.response(createResponseMessage(message, Type.NOT_FOUND));
	            	return;
	            }
			}
			super.handleResponse(message, peerConnection, sign, responder);
		}
		
		@Override
	    public FutureResponse get(final PeerAddress remotePeer, final GetBuilder getBuilder,
	            final ChannelCreator channelCreator) {
	    	final Type type;
	        if (getBuilder.isAscending() && getBuilder.isBloomFilterAnd()) {
	            type = Type.REQUEST_1;
	        } else if(!getBuilder.isAscending() && getBuilder.isBloomFilterAnd()){
	            type = Type.REQUEST_2;
	        } else if(getBuilder.isAscending() && !getBuilder.isBloomFilterAnd()){
	        	type = Type.REQUEST_3;
	        } else {
	        	type = Type.REQUEST_4;
	        }
	        final Message message = createMessage(remotePeer, RPC.Commands.GET.getNr(), type);

	        if (getBuilder.isSign()) {
	            message.publicKeyAndSign(getBuilder.keyPair());
	        }

	        if (getBuilder.to() != null && getBuilder.from() != null) {
	            final Collection<Number640> keys = new ArrayList<Number640>(2);
	            keys.add(getBuilder.from());
	            keys.add(getBuilder.to());
	            message.intValue(getBuilder.returnNr());
	            message.keyCollection(new KeyCollection(keys));
	        } else if (getBuilder.keys() == null) {

	            if (getBuilder.locationKey() == null || getBuilder.domainKey() == null) {
	                throw new IllegalArgumentException("Null not allowed in location or domain");
	            }
	            message.key(getBuilder.locationKey());
	            message.key(getBuilder.domainKey());

	            if (getBuilder.contentKeys() != null) {
	                message.keyCollection(new KeyCollection(getBuilder.locationKey(), getBuilder
	                        .domainKey(), getBuilder.versionKey(), getBuilder.contentKeys()));
	            } else {
	                message.intValue(getBuilder.returnNr());
	               
	                if (getBuilder.contentKeyBloomFilter() != null) {
	                     message.bloomFilter(getBuilder.contentKeyBloomFilter());
	                } else {
	                	if(getBuilder.isBloomFilterAnd()) {
	                		message.bloomFilter(FULL_FILTER);
	                	} else {
	                		message.bloomFilter(EMPTY_FILTER);
	                	}
	                }
	                
	                if (getBuilder.versionKeyBloomFilter() != null) {
	                    message.bloomFilter(getBuilder.versionKeyBloomFilter());
	                } else {
	                	if(getBuilder.isBloomFilterAnd()) {
	                		message.bloomFilter(FULL_FILTER);
	                	} else {
	                		message.bloomFilter(EMPTY_FILTER);
	                	}
	                }
	                
	                if (getBuilder.contentBloomFilter() != null) {
	                    message.bloomFilter(getBuilder.contentBloomFilter());
	                } else {
	                	if(getBuilder.isBloomFilterAnd()) {
	                		message.bloomFilter(FULL_FILTER);
	                	} else {
	                		message.bloomFilter(EMPTY_FILTER);
	                	}
	                }
	            }
	        } else {
	            message.keyCollection(new KeyCollection(getBuilder.keys()));
	        }
	        /////////////////////////////////////////////////////
	        //////////Modification starts here
	        /////////////////////////////////////////////////////
	        NavigableMap<Number640,Data> map=new TreeMap<Number640,Data>();
	        try {
				map.put(Number640.ZERO, new Data(rpeer.peerAddress()).signNow(rKey, factory));
			} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SignatureException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        DataMap dm=new DataMap(map);
	        message.setDataMap(dm);
	        /////////////////////////////////////////////////////
	        //////////Modification ends
	        /////////////////////////////////////////////////////        
	        final FutureResponse futureResponse = new FutureResponse(message);
	        final RequestHandler<FutureResponse> request = new RequestHandler<FutureResponse>(futureResponse,
	                peerBean(), connectionBean(), getBuilder);
	        if (!getBuilder.isForceUDP()) {
	            return request.sendTCP(channelCreator);
	        } else {
	            return request.sendUDP(channelCreator);
	        }
	    }
	}
	
	public void exit()
	{
		rpeer.shutdown().awaitListenersUninterruptibly();
	}
	
	public  RootPeer(DSASignatureFactory factory,KeyPair rKey,Key rLockKey,PeerManager pm) throws IOException
	{
		this.factory=factory;this.rKey=rKey;this.rLockKey=rLockKey;this.pm=pm;
        Bindings b = new Bindings();
        byte[] id=new byte[20];
        rnd.nextBytes(id);
        Number160 peer2Owner = Utils.makeSHAHash(id);
        Number160 publick=Utils.makeSHAHash(rKey.getPublic().getEncoded());
        Peer pr=new PeerBuilder(peer2Owner).ports(4005).bindings(b).start();
        pr.objectDataReply(pm.newServerReply());
        PeerBuilderDHT builder=new PeerBuilderDHT(pr);
        
        Storage storage=PeerManager.make_storage(publick);
        StorageLayer storageLayer = new StorageLayer(storage);
		storageLayer.start(pr.connectionBean().timer(), storageLayer.storageCheckIntervalMillis());
    	rpeer =builder.storage(storage).storageLayer(storageLayer)
    			.storeRPC(new RootStorageRPC(pr.peerBean(), pr.connectionBean(), storageLayer))
    			.start();
    	rpeer.storageLayer().protection(  PeerManager.PROTECT, PeerManager.PROTECTMODE , PeerManager.PROTECT,
    			PeerManager.PROTECTMODE  );
    	
        FutureBootstrap fb = rpeer.peer().bootstrap().inetAddress(InetAddress.getByName("127.0.0.1")).ports(4000).start();
        fb.awaitUninterruptibly();
        if (fb.isSuccess()) {
            rpeer.peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
        }
        System.out.println("Root private node started, "+pr.peerAddress());	
	}
	
	public RootPeer(String host,DSASignatureFactory factory,KeyPair rKey,Key rLockKey,PeerManager pm) throws NoSuchAlgorithmException, IOException
	{
		this.factory=factory;this.rKey=rKey;this.rLockKey=rLockKey;this.pm=pm;
        Bindings b = new Bindings();
    	KeyPairGenerator gen = KeyPairGenerator.getInstance( "DSA" );
       	KeyPair pair1 = gen.generateKeyPair();
       	Number160 publick=Utils.makeSHAHash(rKey.getPublic().getEncoded());
       	Peer pr=new PeerBuilder(pair1).bindings(b).ports(4005+rnd.nextInt()%1000).behindFirewall().start();//fix-me : remove test random!!!
       	pr.objectDataReply(pm.newServerReply());
       	PeerBuilderDHT builder= new PeerBuilderDHT(pr);
       	
        Storage storage=PeerManager.make_storage(publick);
        StorageLayer storageLayer = new StorageLayer(storage);
		storageLayer.start(pr.connectionBean().timer(), storageLayer.storageCheckIntervalMillis());
    	rpeer =builder.storage(storage).storageLayer(storageLayer)
    			.storeRPC(new RootStorageRPC(pr.peerBean(), pr.connectionBean(), storageLayer))
    			.start();
        rpeer.storageLayer().protection(  PeerManager.PROTECT, PeerManager.PROTECTMODE , PeerManager.PROTECT,
        		PeerManager.PROTECTMODE  );
		InetAddress address = Inet4Address.getByName(host);
		int masterPort = 4005;
		PeerAddress pa = new PeerAddress(Number160.ZERO, address, masterPort, masterPort);

		System.out.println("PeerAddress: " + pa);
		PeerManager.bootstrap2(rpeer,pa);	
	}

}
