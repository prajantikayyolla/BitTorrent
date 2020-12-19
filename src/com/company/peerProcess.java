package com.company;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;


class PeerInfo {
    private String peerID;
    private String hostname;
    private int port;
    private int hasFile;

    public PeerInfo(String peerID, String hostname, int port, int hasFile) {
        this.peerID = peerID;
        this.hostname = hostname;
        this.port = port;
        this.hasFile = hasFile;
    }

    public String getPeerID() {
        return peerID;
    }

    public void setPeerID(String peerID) {
        this.peerID = peerID;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getHasFile() {
        return hasFile;
    }

    public void setHasFile(int hasFile) {
        this.hasFile = hasFile;
    }
}

class DownloadRateNode {
    String peerId;
    Integer downloadRate;
    public DownloadRateNode(String peerId, Integer downloadRate) {
        this.peerId = peerId;
        this.downloadRate = downloadRate;
    }
}

class SelectionNode {
    int priority;
    int index;

    public SelectionNode(int priority, int index) {
        this.priority = priority;
        this.index = index;
    }
}

public class peerProcess {
    static Peer peer;
    static String peerID;
    public static void main(String[] args) throws Exception {
        peerID = "1001";
        if(args.length != 0)
            peerID = args[0];
        peer = new Peer(peerID, new Config());
        peer.getConfig().load();
        peer.readPeerInfo();
        pieceSelectionAlgorithm();
        if(peer.getHasFileInitially())
            fileSplitter();
//        System.out.println(peer.getPeerID() + " " + Arrays.toString(peer.getBitfield()));
//        Thread serverThread = new Thread(new Runnable() {
//            public void run() {
//                Server server = new Server(peer.getPort(), peer);
//                peer.setServer(server);
//                try {
//                	System.out.println("in server thread " + Thread.currentThread().getName());
//                    server.start();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//        System.out.println("in main " + Thread.currentThread().getName());
//        serverThread.start();
        Server server = new Server(peer.getPort(), peer);
        peer.setServer(server);
        server.start();
//        System.out.println("here");

        for(PeerInfo peerInfoServer : peer.getPeersToConnect()) {
            Socket requestSocket = null;
            try {
                requestSocket = new Socket(peerInfoServer.getHostname(), peerInfoServer.getPort());
                System.out.println("Connected to " + peerInfoServer.getHostname() + " in port" + " " + peerInfoServer.getPort());
                ObjectOutputStream out = new ObjectOutputStream(requestSocket.getOutputStream());
//                System.out.println(out.toString());
                ObjectInputStream in = new ObjectInputStream(requestSocket.getInputStream());
                HandShake handShake = new HandShake(peer.getPeerID());
                out.writeObject(handShake);
                peer.writeToLog("["+ Instant.now() + "]: Peer [" + peerID +"] makes a connection to Peer [" + peerInfoServer.getPeerID()+"]");
                System.out.println("Sending Handshake message to" + peerInfoServer.getPeerID());
//                out.flush();
                Map<String, String> handShakeStatus = peer.getHandShakeStatus();
                handShakeStatus.put(peerInfoServer.getPeerID(), "Sent");
                peer.setHandShakeStatus(handShakeStatus);
                ConnectionDetails connectionDetails = new ConnectionDetails(peerInfoServer.getPeerID(), requestSocket, out, in, peer, new ConcurrentLinkedQueue<Object>());
                peer.getConnections().add(connectionDetails);
                peer.getPeerToConnections().put(peerInfoServer.getPeerID(), connectionDetails);
//                Thread connectionThread = new Thread(new Runnable() {
//                    public void run() {
////                    	System.out.println("in server thread " + Thread.currentThread().getName());
//                        connectionDetails.start();
//                    }
//                });
//                connectionThread.start();
                connectionDetails.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        Thread messageHandlerThread = new Thread(new Runnable() {
//            MessageHandler messageHandler = new MessageHandler(peer);
//            public void run() {
//                messageHandler.start();
//            }
//        });
//        messageHandlerThread.start();




        startTimers();
//        while(server.getState()!=Thread.State.TERMINATED) {
//            Thread.sleep(1000);
//            System.out.println(server.getState());
//        }
//        Thread.sleep(1000);
        System.out.println("Completed");
        System.exit(0);


//        fileSplitter();
//        fileJoiner();


//        Thread.sleep(8000);
//        Set<Thread> threads = Thread.getAllStackTraces().keySet();
//        
//        for (Thread t : threads) {
//        	System.out.println(t.getName());
//        }
//        while (true);

    }

    public static void startTimers() throws InterruptedException {
        Thread timers = new Thread(new Runnable() {
            @Override
            public void run() {
            	System.out.println("in timers " + Thread.currentThread().getName());
                final ScheduledExecutorService preferredNeighborsScheduler = Executors.newScheduledThreadPool(1);
                final ScheduledExecutorService optimisticNeighborScheduler = Executors.newScheduledThreadPool(1);
                Runnable preferredNeighbors = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sendChoke();
                            selectPreferredNeighbors();
                            sendUnChoke();
                            StringBuilder sb = new StringBuilder("["+ Instant.now() + "]: Peer [" + peerID +"] has the preferred neighbors [");
                            for(String s : peer.getPreferredNeighbors()) {
                                sb.append(s + ", ");
                            }
                            sb.append("]");
                            peer.writeToLog(sb.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                Runnable optimisticNeighbor = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            selectOptimisticNeighbor();
                            StringBuilder sb = new StringBuilder("["+ Instant.now() + "]: Peer [" + peerID +"] has the optimistically unchoked neighbor [");
                            for(String s : peer.getOptimisticNeighbor()) {
                                sb.append(s + ", ");
                            }
                            sb.append("]");
                            peer.writeToLog(sb.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                final ScheduledFuture<?> preferredNeighborsHandle = preferredNeighborsScheduler.scheduleAtFixedRate(preferredNeighbors, 0, Config.UNCHOKINGINTERVAL, TimeUnit.SECONDS);
                final ScheduledFuture<?> optimisticNeighborHandle = optimisticNeighborScheduler.scheduleAtFixedRate(optimisticNeighbor, 1, Config.OPTIMISTICUNCHOKINGINTERVAL, TimeUnit.SECONDS);
//                preferredNeighborsScheduler.schedule(new Runnable() {
//                    public void run() { preferredNeighborsHandle.cancel(true); }
//                }, 5, TimeUnit.MINUTES);
//                optimisticNeighborScheduler.schedule(new Runnable() {
//                    public void run() { optimisticNeighborHandle.cancel(true); }
//                }, 5, TimeUnit.MINUTES);
                while(true) {
                    try {
                        Thread.sleep(3000);
                        if(isDone()) {
                            System.out.println("Stopping timers");
                            preferredNeighborsHandle.cancel(true);
                            optimisticNeighborHandle.cancel(true);
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
//                System.out.println("out of while loop");
            }
        });

        while(peer.getInterested().size() == 0) {
            Thread.sleep(1000);
        }
        timers.start();
        while(timers.isAlive()) {
            Thread.sleep(1000);
        }
//        System.out.println("timers completed as reached end");

    }

    public static void sendChoke() throws IOException {

        Set<String> unChoked = peer.getUnChoked();
        Set<String> choked = peer.getChoked();
        Map<String, ConnectionDetails> peerToConnections = peer.getPeerToConnections();
        Set<String> preferredNeighbors = peer.getPreferredNeighbors();

        for(String peerID : preferredNeighbors) {
            ConnectionDetails connectionDetails = peerToConnections.get(peerID);
            connectionDetails.send(new ActualMessage(0, null));
            choked.add(peerID);
            unChoked.remove(peerID);
        }

        preferredNeighbors.clear();
        peer.setPreferredNeighbors(preferredNeighbors);
        peer.setUnChoked(unChoked);
        peer.setChoked(choked);
    }

    public static void selectPreferredNeighbors() throws IOException {
        Set<String> choked = peer.getChoked();
        Set<String> unChoked = peer.getUnChoked();
        Set<String> interested = peer.getInterested();
        Set<String> preferredNeighbors = new HashSet<>();
        ConcurrentMap<String, Integer> downloadRate = peer.getDownloadRate();
        PriorityQueue<DownloadRateNode> pq = new PriorityQueue<DownloadRateNode>((a, b) -> b.downloadRate - a.downloadRate);

        for(String key : downloadRate.keySet()) {
            pq.add(new DownloadRateNode(key, downloadRate.get(key)));
            //**
            downloadRate.put(key, 0);
        }

        int preferredNeighborCount = Config.NUMBEROFPREFERREDNEIGHBORS;
        while(preferredNeighborCount > 0 && pq.size() != 0) {
            DownloadRateNode node = pq.poll();
            if(interested.contains(node.peerId)) {
                unChoked.add(node.peerId);
                choked.remove(node.peerId);
//                interested.remove(node.peerId);
                preferredNeighbors.add(node.peerId);
                preferredNeighborCount--;
            }
        }

        peer.setPreferredNeighbors(preferredNeighbors);
        peer.setUnChoked(unChoked);
        peer.setChoked(choked);
        peer.setInterested(interested);
//**        downloadRate.clear();
        peer.setDownloadRate(downloadRate);
    }

    public static void sendUnChoke() throws IOException {
// was using unchoked so unchoke was sent twice 1 for prefneig and opt nei so use perffered neighbors to unchoke
//        Set<String> unChoked = peer.getUnChoked();
        Map<String, ConnectionDetails> peerToConnections = peer.getPeerToConnections();

        for(String peerId : peer.getPreferredNeighbors()) {
            ConnectionDetails connectionDetails = peerToConnections.get(peerId);
            connectionDetails.send(new ActualMessage(1, null));
        }

    }

    public static void selectOptimisticNeighbor() throws IOException {

        Set<String> optimisticNeighbor = peer.getOptimisticNeighbor();
        Set<String> interested = peer.getInterested();
        Set<String> choked = peer.getChoked();
        Set<String> unChoked = peer.getUnChoked();
        List<String> interestedAndChoked = new ArrayList<>();
        Map<String, ConnectionDetails> peerToConnections = peer.getPeerToConnections();

        for(String peerId : optimisticNeighbor) {
            ConnectionDetails connectionDetails = peerToConnections.get(peerId);
            connectionDetails.send(new ActualMessage(0, null));
            optimisticNeighbor.remove(peerId);
            choked.add(peerId);
        }

        for(String peerId : interested) {
            if(choked.contains(peerId)) {
                interestedAndChoked.add(peerId);
            }
        }

        Collections.shuffle(interestedAndChoked);

        if(interestedAndChoked.size() != 0) {
            String peerId = interestedAndChoked.get(0);
            ConnectionDetails connectionDetails = peerToConnections.get(peerId);
            connectionDetails.send(new ActualMessage(1, null));
            unChoked.add(peerId);
            choked.remove(peerId);
//            interested.remove(peerId);
            optimisticNeighbor.add(peerId);
        }

        peer.setChoked(choked);
        peer.setUnChoked(unChoked);
        peer.setInterested(interested);
        peer.setOptimisticNeighbor(optimisticNeighbor);
    }

    public static void fileSplitter() throws IOException {

        File file = new File(Config.FILENAME);
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);

        byte[] contents;
        long fileLength = file.length();
        long current = 0;
        int index = 0;

        ConcurrentMap<Integer, byte[]> pieceAtIndex = new ConcurrentHashMap<>();

        while(current < fileLength){
            int size = Config.PIECESIZE;
            if(fileLength - current >= size)
                current += size;
            else{
                size = (int)(fileLength - current);
                current = fileLength;
            }
            contents = new byte[size];
            bis.read(contents, 0, size);
            pieceAtIndex.put(index, contents);
            index++;
        }

        peer.setPieceAtIndex(pieceAtIndex);
    }

    public static void fileJoiner() throws IOException {
        Map<Integer, byte[]> pieceAtIndex = peer.getPieceAtIndex();
        int pieces = Config.TOTALPIECES;
        FileOutputStream fos = new FileOutputStream(Config.FILENAME);
        for(int i = 0; i < pieces; i++) {
            fos.write(pieceAtIndex.get(i));
        }
    }

    public static void pieceSelectionAlgorithm() {

        int peerId = Integer.parseInt(peer.getPeerID());
        int totalPeers = peer.getAllPeers().size();
        int range = (int) Math.ceil(Config.TOTALPIECES / totalPeers);
        int min = Math.max(0, (peerId % 1000 - 1) * range);
        int max = Math.min(min + range, Config.TOTALPIECES);

        PriorityBlockingQueue<SelectionNode> pq = new PriorityBlockingQueue<SelectionNode>(10, (a, b) -> b.priority - a.priority);
//        PriorityQueue<SelectionNode> pq = new PriorityQueue<SelectionNode>((a, b) -> b.priority - a.priority);

        List<SelectionNode> list = new ArrayList<>();

        for(int i = 0; i < Config.TOTALPIECES; i++) {
            if(i >= min && i <= max) {
                list.add(new SelectionNode(2, i));
            }
            else {
                list.add(new SelectionNode(1, i));
            }
        }

        Collections.shuffle(list);

        for(int i = 0; i < list.size(); i++) {
            pq.add(list.get(i));
        }

        peer.setSelectionNodes(pq);

    }

    public static boolean isDone() throws InterruptedException, IOException {
        List<PeerInfo> allPeers = peer.getAllPeers();
        int count = 0;
        for(PeerInfo p : allPeers) {
            String bitField;
            if(p.getPeerID().compareTo(peerID) == 0) {
                bitField = String.valueOf(peer.getBitfield());
            }
            else {
                bitField = peer.getMapBitField().getOrDefault(p.getPeerID(), null);
            }
            if(bitField == null)
                continue;
            for(int i = 0; i < bitField.length(); i++) {
                if(bitField.charAt(i) == '0')
                    return false;
            }
            count++;
        }
        // making to 3 as of now from count == allPeers.size()
        if(count == 3) {
            Thread.sleep(3000);
            closeAll();
            createFile();
            return true;
        }
        return false;
    }

    public static void createFile() throws IOException {
    	File theDir = new File("./peer_" + peerID);
    	if (!theDir.exists()){
    	    theDir.mkdirs();
    	}
        ConcurrentMap<Integer, byte[]> pieces = peer.getPieceAtIndex();
        File file = new File("./peer_" + peerID + "/" + Config.FILENAME);
        FileOutputStream stream = new FileOutputStream(file);
        for(int i = 0; i < Config.TOTALPIECES; i++) {
            stream.write(pieces.get(i));
        }
        stream.close();
    }

    public static void closeAll() throws IOException, InterruptedException {
        ConcurrentMap<String, ConnectionDetails> connections = peer.getPeerToConnections();

        for(String peer : connections.keySet()) {
            ConnectionDetails c = connections.get(peer);
            Thread cd = (Thread) c;
            Thread mh = (Thread) c.getMessageHandler();
            Thread unChoke = (Thread) c.getMessageHandler().getUnChokeThread();
            if(unChoke != null) {
                System.out.println("Stopping unChoke thread" + unChoke.getName()+" of peer " + peer);
                unChoke.stop();
            }
            Thread.sleep(1000);
            if(mh != null) {
                System.out.println("Stopping message handler thread" + mh.getName()+" of peer " + peer);
                mh.stop();
            }

            if(cd != null) {
                System.out.println("Stopping connection thread" + cd.getName()+" of peer " + peer);
//                c.getOut().close();
//                c.getIn().close();
                cd.stop();
            }
        }
        Thread server = (Thread) peer.getServer();
        System.out.println("Stopping server thread" + server.getName()+" of peer " + peer);
        server.stop();
    }
}