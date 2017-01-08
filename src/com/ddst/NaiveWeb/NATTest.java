package com.ddst.NaiveWeb;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Random;

import net.tomp2p.connection.Bindings;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageLayer.ProtectionEnable;
import net.tomp2p.dht.StorageLayer.ProtectionMode;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;

public class NATTest {

	static void assertFalse(boolean t) throws Exception {
		assertTrue(!t);
	}

	static void assertTrue(boolean t) throws Exception {
		if (!t) {
			Exception e = new Exception();
			throw e;
		}
	}

	static void assertEquals(String a, String b) throws Exception {
		if (!a.equals(b)) {
			System.out.println(a);
			System.out.println(b);
			Exception e = new Exception();
			throw e;
		}
	}

	static void Test() {
		KeyPairGenerator keyGen;
		PeerDHT p1 = null, p2 = null;
		try {
			keyGen = KeyPairGenerator.getInstance("DSA");

			KeyPair keyPairPeer1 = keyGen.generateKeyPair();
			KeyPair keyPairPeer2 = keyGen.generateKeyPair();
			KeyPair keyPairData = keyGen.generateKeyPair();

			

			p1 = new PeerBuilderDHT(new PeerBuilder(Number160.createHash(1))
					.ports(4838).keyPair(keyPairPeer1).start()).start();
			
			p1.peer().bootstrap().peerAddress(p1.peerAddress()).start()
					.awaitUninterruptibly();
			p1.storageLayer().protection(ProtectionEnable.ALL, ProtectionMode.MASTER_PUBLIC_KEY, ProtectionEnable.ALL,
					ProtectionMode.MASTER_PUBLIC_KEY);
			String locationKey = "location";
			Number160 lKey = Number160.createHash(locationKey);
			String contentKey = "content";
			Number160 cKey = Number160.createHash(contentKey);

			String testData1 = "data1";
			Data data = new Data(testData1).protectEntry(keyPairData);
			// put trough peer 1 with key pair
			// -------------------------------------------------------
			FuturePut futurePut1 = p1.put(lKey).data(cKey, data)
					.keyPair(keyPairData).start();
			futurePut1.awaitUninterruptibly();
			assertTrue(futurePut1.isSuccess());

			FutureGet futureGet1a = p1.get(lKey).contentKey(cKey).start();
			futureGet1a.awaitUninterruptibly();

			// put trough peer 2 without key pair
			// ----------------------------------------------------
			for(int i=0;i<20;i++)
			{
				System.out.println(i);
				
				p2 = new PeerBuilderDHT(new PeerBuilder(Number160.createHash(2)).keyPair(keyPairPeer2).start())
				.start();

				p2.peer().bootstrap().peerAddress(p1.peerAddress()).start()
				.awaitUninterruptibly();
				
				String testData2 = "data2";
				Data data2 = new Data(testData2);
				FuturePut futurePut2 = p2.put(lKey).data(cKey, data2).keyPair(keyPairPeer2).start();
				futurePut2.awaitUninterruptibly();
				// PutStatus.FAILED_SECURITY
				assertFalse(futurePut2.isSuccess());

				FutureGet futureGet2 = p2.get(lKey).contentKey(cKey).start();
				futureGet2.awaitUninterruptibly();
				assertTrue(futureGet2.isSuccess());
				// should have been not modified
				assertEquals(testData1, (String) futureGet2.data().object());
				// put trough peer 1 without key pair
				// ----------------------------------------------------
				p2.shutdown();

			}

			
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally
		{
			if(p1!=null)
				p1.shutdown();
			if(p2!=null)
				p2.shutdown();
		}

	}

	public static void main(String[] args) throws Exception {
		Test();
		System.out.println("OK");
	}

}
