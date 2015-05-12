package com.DHTWeb;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Collection;

import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.DiscoverNetworks;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.FutureBootstrap; 
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.nat.FutureNAT;
import net.tomp2p.nat.FutureRelayNAT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.relay.tcp.TCPRelayClientConfig;
import net.tomp2p.storage.Data;

public class DHTWeb {

	static private PeerDHT peer;

    public DHTWeb(int peerId) throws Exception {
    	peer = new PeerBuilderDHT(new PeerBuilder(Number160.createHash(peerId)).ports(4000 ).start()).start();
        FutureBootstrap fb = peer.peer().bootstrap().inetAddress(InetAddress.getByName("127.0.0.1")).ports(4001).start();
        fb.awaitUninterruptibly();
    	System.out.println("KKKK");
        if (fb.isSuccess()) {
            peer.peer().discover().peerAddress(fb.bootstrapTo().iterator().next()).start().awaitUninterruptibly();
        }
    }
    public DHTWeb(String host,int peerId) throws Exception {
    	Bindings b = new Bindings().listenAny();
    	peer= new PeerBuilderDHT(new PeerBuilder(new Number160(peerId)).ports(4000+peerId).bindings(b).behindFirewall().start()).start();
		System.out.println("Client started and Listening to: " + DiscoverNetworks.discoverInterfaces(b));
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
		
		
    }
    public static void main(String[] args) throws NumberFormatException, Exception {
    	DHTWeb dns;
        if (args[0].equals("-s")) {
        	dns= new DHTWeb(Integer.parseInt(args[1]));
            dns.store(args[2], args[3]);
        }
        if (args[0].equals("-c")) {
        	dns= new DHTWeb(args[1],Integer.parseInt(args[2]));
            System.out.println("Name:" + args[3] + " IP:" + dns.get(args[3]));
            peer.shutdown();
        }
    }

	private String get(String name) throws ClassNotFoundException, IOException {
		FutureGet futureGet = peer.get(Number160.createHash(name)).start();
		futureGet.awaitUninterruptibly();
		if (futureGet.isSuccess()) {
			return futureGet.dataMap().values().iterator().next().object().toString();
		}
		return "not found";
	}
    private void store(String name, String ip) throws IOException {
        peer.put(Number160.createHash(name)).data(new Data(ip)).start().awaitUninterruptibly();
    }

}
