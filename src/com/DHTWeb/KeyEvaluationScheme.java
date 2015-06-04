package com.DHTWeb;



import io.netty.buffer.ByteBuf;

import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import net.tomp2p.connection.SignatureFactory;
import net.tomp2p.dht.EvaluatingSchemeDHT;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.DigestResult;
import net.tomp2p.storage.Data;

public class KeyEvaluationScheme implements EvaluatingSchemeDHT {
	PublicKey key;
	SignatureFactory factory;
	public KeyEvaluationScheme(PublicKey k,SignatureFactory sig)
	{
		super();
		key=k;
		factory=sig;
	}
	
    @Override
    public Collection<Number640> evaluate1(Map<PeerAddress, Map<Number640, Number160>> rawKeys480) {
        Set<Number640> result = new HashSet<Number640>();
        if (rawKeys480 != null) {
            for (Map<Number640, Number160> tmp : rawKeys480.values()) {
                result.addAll(tmp.keySet());
            }
        }
        return result;
    }
    
    @Override
    public Collection<Number640> evaluate6(Map<PeerAddress, Map<Number640, Byte>> rawKeys480) {
    	Map<Number640, Byte> result = new HashMap<Number640, Byte>();
        for (Map<Number640, Byte> tmp : rawKeys480.values())
            result.putAll(tmp);
        return result.keySet();
    }

    @Override
    public Map<Number640, Data> evaluate2(Map<PeerAddress, Map<Number640, Data>> rawKeys) {
        Map<Number640, Data> result = new HashMap<Number640, Data>();
        for (Map<Number640, Data> tmp : rawKeys.values())
        {
        	for(Entry<Number640, Data> entry : tmp.entrySet())
        	{
        		try {
					if(entry.getValue().publicKey().equals(key) 
							&& entry.getValue().verify(key, factory))
					{
						result.put(entry.getKey(),entry.getValue());
					}
				} catch (InvalidKeyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SignatureException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        		
        	}
        	
        }
            
        return result;
    }

    @Override
    public Object evaluate3(Map<PeerAddress, Object> rawKeys) {
        throw new UnsupportedOperationException("cannot cumulate");
    }

    @Override
    public ByteBuf evaluate4(Map<PeerAddress, ByteBuf> rawKeys) {
        throw new UnsupportedOperationException("cannot cumulate");
    }

    @Override
    public DigestResult evaluate5(Map<PeerAddress, DigestResult> rawDigest) {
        throw new UnsupportedOperationException("cannot cumulate");
    }
}