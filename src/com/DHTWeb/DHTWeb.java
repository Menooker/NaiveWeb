package com.DHTWeb;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;

import net.tomp2p.futures.FutureDHT;
import net.tomp2p.futures.FutureBootstrap; 
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

public class DHTWeb {

	static private Peer peer;

    public DHTWeb(int peerId) throws Exception {
        peer = new PeerMaker(Number160.createHash(peerId)).setPorts(4000 ).makeAndListen();
        FutureBootstrap fb = peer.bootstrap().setBroadcast().setPorts(4000).start();
        fb.awaitUninterruptibly();
        if (fb.getBootstrapTo() != null) {
            peer.discover().setPeerAddress(fb.getBootstrapTo().iterator().next()).start().awaitUninterruptibly();
        }
    }
    public DHTWeb(String host,int peerId) throws Exception {
    	peer = new PeerMaker(Number160.createHash(peerId)).setPorts(4000 + peerId).makeAndListen();
    	InetAddress address = Inet4Address.getByName(host);
    	FutureDiscover futureDiscover = peer.discover().setInetAddress( address ).setPorts( 4000 ).start();
    	futureDiscover.awaitUninterruptibly();
    	System.out.println(futureDiscover.isSuccess());
    	FutureBootstrap futureBootstrap = peer.bootstrap().setInetAddress( address ).setPorts( 4000 ).start();
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
        FutureDHT futureDHT = peer.get(Number160.createHash(name)).start();
        futureDHT.awaitUninterruptibly();
        if (futureDHT.isSuccess()) {
            return futureDHT.getData().getObject().toString();
        }
        System.out.println(futureDHT.getFailedReason());
        return "not found";
    }

    private void store(String name, String ip) throws IOException {
        peer.put(Number160.createHash(name)).setData(new Data(ip)).start().awaitUninterruptibly();
    }

}
