package com.company;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class MessageHandler extends Thread{
    Peer peer;
    ConnectionDetails connectionDetails;
    Queue<Object> messageQueue;
//    Queue<Integer> pieces = new ConcurrentLinkedQueue<>();
    LinkedList<Integer> pieces = new LinkedList<>();
//    Set<Integer> pieces = new HashSet<>();
    Thread unChokeThread;
    int localRequested;

    public Thread getUnChokeThread() {
        return unChokeThread;
    }

    public MessageHandler(Peer peer, ConnectionDetails connectionDetails, Queue<Object> messageQueue) {

        this.peer = peer;
        this.connectionDetails = connectionDetails;
        this.messageQueue = messageQueue;
    }


    @Override
    public void run() {
    	System.out.println("in message handler thread " + Thread.currentThread().getName()+" "+connectionDetails.hashCode()+" "+ connectionDetails.getMessageQueue().hashCode());
        while(true) {
//        	Queue<MessageQueueElement> messageQueue = this.connectionDetails.getMessageQueue();
//        	System.out.println(messageQueue.size());
//        	System.out.println(connectionDetails.getLength());
            while(messageQueue.size() != 0) {
//                Object messageQueueElement = messageQueue.poll();
////                System.out.println("Recieved Message of type" + messageQueueElement.message);
//                ConnectionDetails connectionDetails = messageQueueElement.getConnectionDetails();
                Object message = messageQueue.poll();
                System.out.print("[Request ] : ");
                if(message instanceof HandShake) {
                    System.out.println("handshake message recieved from " + ((HandShake) message).getPeerID() + message);
//                    System.out.print("[Response] : ");
                    try {
                        peer.writeToLog("[" + Instant.now()+"]: Peer [" + peer.getPeerID()+"] is connected from Peer [" + ((HandShake) message).getPeerID() + "]");
                        handleHandShake((HandShake) message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else if (message instanceof  PieceMessage) {
                    PieceMessage pieceMessage = (PieceMessage) message;
                    if(pieceMessage.getMessageType() == 7)
                        System.out.println("Actual Message of type " + pieceMessage.getMessageType() + " recieved from "+connectionDetails.getPeerId() +" and content is " + localRequested + " " + Instant.now()
                                .toString());
                    try {
                        peer.writeToLog("["+ Instant.now() + "]: Peer [" + peer.getPeerID() +"] has downloaded the piece [" + localRequested + "] from [" + connectionDetails.getPeerId() +"]. Now the number of pieces it has is ["+(peer.getPieceAtIndex().size() + 1)+"]");
                        handlePiece(pieceMessage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                else if (message instanceof ActualMessage){
                    ActualMessage actualMessage = (ActualMessage) message;
                    if(actualMessage.getMessageType() == 7)
                        System.out.println("Actual Message of type " + actualMessage.getMessageType() + " recieved from "+connectionDetails.getPeerId() +" and content is " + localRequested + " " + Instant.now()
                                .toString());
                    else
                        System.out.println("Actual Message of type " + actualMessage.getMessageType() + " recieved from "+connectionDetails.getPeerId() +" and content is " + actualMessage.getMessagePayload() + Instant.now()
                            .toString());
//                    System.out.print("[Response] : ");
                    switch(actualMessage.getMessageType()) {
                        case 0:
                            try {
                                peer.writeToLog("["+ Instant.now() + "]: Peer [" + peer.getPeerID() +"] is choked by [" + connectionDetails.getPeerId() +"]");
                                handleChoke((ActualMessage) message);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 1:
                            try {
                                peer.writeToLog("["+ Instant.now() + "]: Peer [" + peer.getPeerID() +"] is unchoked by [" + connectionDetails.getPeerId() +"]");
                                handleUnchoke((ActualMessage) message);
                            } catch (IOException | InterruptedException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 2:
                            try {
                                peer.writeToLog("["+ Instant.now() + "]: Peer [" + peer.getPeerID() +"] received the ‘interested’ message from [" + connectionDetails.getPeerId() +"]");
                                handleInterested((ActualMessage) message);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 3:
                            try {
                                peer.writeToLog("["+ Instant.now() + "]: Peer [" + peer.getPeerID() +"] received the ‘not interested’ message from [" + connectionDetails.getPeerId() +"]");
                                handleNotInterested((ActualMessage) message);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 4:
                            try {
                                peer.writeToLog("["+ Instant.now() + "]: Peer [" + peer.getPeerID() +"] received the ‘have’ message from [" + connectionDetails.getPeerId() +"] for the piece [" + ((ActualMessage) message).getMessagePayload() +"]");
                                handleHave((ActualMessage) message);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 5:
                            try {
                                handleBitField((ActualMessage) message);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 6:
                            try {
                                handleRequest((ActualMessage) message);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 7:
                            try {
                                peer.writeToLog("["+ Instant.now() + "]: Peer [" + peer.getPeerID() +"] has downloaded the piece [" + localRequested + "] from [" + connectionDetails.getPeerId() +"]. Now the number of pieces it has is ["+(peer.getPieceAtIndex().size() + 1)+"]");
//                                handlePiece((ActualMessage) message);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                }
            }
        }
    }

    public void handleHandShake(HandShake handShake) throws IOException {
        Map<String, String> handShakeStatus = peer.getHandShakeStatus();
        Map<String, String> bitFieldStatus = peer.getBitFieldStatus();
//        ObjectOutputStream out = connectionDetails.getOut();
        boolean hasPieces = peer.hasPieces();
        // If handshake received then validate it and send handshake and bitfield.
        if(handShakeStatus.getOrDefault(connectionDetails.getPeerId(), "False").compareTo("False") == 0) {
            connectionDetails.setPeerId(handShake.getPeerID());// setting peerId here;
            peer.getPeerToConnections().put(handShake.getPeerID(), connectionDetails);
            if(handShake.getHeader().compareTo("P2PFILESHARINGPROJ") == 0) {
                connectionDetails.send(new HandShake(peer.getPeerID()));
                if(hasPieces) {
                    connectionDetails.send(new ActualMessage(5, String.valueOf(peer.getBitfield())));
                    System.out.println("Handshake validated and sending handshake and bitfield");
                    bitFieldStatus.put(connectionDetails.getPeerId(), "Sent");
                }
                else {
                    System.out.println("Handshake validated but no bitfield to send");
                }
            }
        }
        // handshake response received, validate and send bitfield if you have pieces.
        else if(handShakeStatus.getOrDefault(connectionDetails.getPeerId(), "False").compareTo("Sent") == 0) {
            if(handShake.getHeader().compareTo("P2PFILESHARINGPROJ") == 0 && handShake.getPeerID().compareTo(connectionDetails.getPeerId()) == 0) {
                if(hasPieces) {
                    connectionDetails.send(new ActualMessage(5, String.valueOf(peer.getBitfield())));
//                    out.flush();
                    bitFieldStatus.put(connectionDetails.getPeerId(), "Sent");
                    System.out.println("Handshake validated and sending bitfield");
                }
                else{
                    System.out.println("Handshake validated but no bitfield to send");
                }
            }
        }
        handShakeStatus.put(connectionDetails.getPeerId(), "True");
        peer.setHandShakeStatus(handShakeStatus);
        peer.setBitFieldStatus(bitFieldStatus);
        peer.getDownloadRate().put(connectionDetails.getPeerId(), 0);
    }

    public void handleChoke(ActualMessage message) {
        peer.getChokedFromPeers().add(connectionDetails.getPeerId());
        peer.getUnChokedFromPeers().remove(connectionDetails.getPeerId());
    }

    //in response to unchoke control is coming here and sending a message of type 6 and waiting
    // so the message handler will not read another object
    // that is why 7 is not captured
    public void handleUnchoke(ActualMessage message) throws IOException, InterruptedException {
        if(unChokeThread == null) {
            unChokeThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        // should check if piece index is not there with me
                        // should check if it was requested to any other peer
                        // send another request only after receiving
                        // loop until choked or it gets all pieces the connection peer has
                        // what if the connection peer has choked this peer or selection neighbors in which it wont send the file
                        //      so if we keep condition on file received it will forever be there so add timeout
                        // This should run on another thread ? because message handler should not wait for this to get processed
                        // so how about 1 message handler for each connection ?
                        String peerId = connectionDetails.getPeerId();
//                      ObjectOutputStream out = connectionDetails.getOut();
                        Set<Integer> requested = peer.getRequested();
//                      Set<String> choked = peer.getChoked();
//                      peer.getChokedFromPeers().remove(connectionDetails.getPeerId());
//                      peer.getUnChokedFromPeers().add(connectionDetails.getPeerId());
                        // shuffling
                        Collections.shuffle(pieces);
                        while(true) {
                            // looping until its not choked and until peer has pieces
                            while(!peer.getChokedFromPeers().contains(peerId) && !pieces.isEmpty()) { // add break on receiving all pieces
                                int piece = pieces.remove();
//                              System.out.println(piece);
                                // checking if piece is needed and not yet requested to any other peer
                                if(peer.getBitfield()[piece] == '0' && !requested.contains(piece)) {
                                    connectionDetails.send(new ActualMessage(6, Integer.toString(piece)));
                                    System.out.println("Asking for piece " + piece);
//                                  connectionDetails.getOut().flush();
                                    requested.add(piece);
                                    localRequested = piece;
                                    //waiting to receive piece
                                    while(requested.contains(piece)) {
                                        //meanwhile if choked remove the piece from requested to again request.
                                        if(peer.getChokedFromPeers().contains(peerId)) {
                                            requested.remove(piece);
                                            pieces.add(piece);
                                            break;
                                        }
                                    }
                                    //**
                                    Thread.sleep(50);
                                }
                                else{
                                    System.out.println(piece+" "+connectionDetails.getPeerId()+" "+ Instant.now());
                                }
                            }
                            while(!peer.getUnChokedFromPeers().contains(peerId)) {
                                Thread.sleep(100);
                            }
                            //*3 send not interested message
//        send(new ActualMessage(3, null));
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }


                }
            });
            unChokeThread.start();
        }
        peer.getChokedFromPeers().remove(connectionDetails.getPeerId());
        peer.getUnChokedFromPeers().add(connectionDetails.getPeerId());
    }

    public void handleInterested(ActualMessage message) {
        System.out.println("Adding to interested list");
        peer.getInterested().add(connectionDetails.getPeerId());
    }

    public void handleNotInterested(ActualMessage message) {
    //*4 update interested/not interested
        peer.getInterested().remove(connectionDetails.getPeerId());
    }

    public void handleHave(ActualMessage actualMessage) throws IOException {
        int piece = Integer.parseInt(actualMessage.getMessagePayload());
        char[] bitfield = peer.getBitfield();
        //*1 send interested message if you need that piece else not interested
        if(bitfield[piece] == '0') {
            pieces.add(piece);
            connectionDetails.send(new ActualMessage(2, null));
        }
//        else {
//            send(new ActualMessage(3, null));
//        }
        //*2 update bitfield of sent peer by setting this piece in bitfield map
        ConcurrentMap<String, String> map = peer.getMapBitField();
        String bitFieldOfReceivedPeer = map.get(connectionDetails.getPeerId());
        if(bitFieldOfReceivedPeer == null) {
            char[] defaultBitField = new char[Config.TOTALPIECES];
            Arrays.fill(defaultBitField, '0');
            bitFieldOfReceivedPeer = String.valueOf(defaultBitField);
        }
        char[] ch = bitFieldOfReceivedPeer.toCharArray();
        ch[piece] = '1';
        map.put(connectionDetails.getPeerId(), String.valueOf(ch));

    }

    public void handleBitField(ActualMessage actualMessage) throws IOException {
        Map<String, String> bitFieldStatus = peer.getBitFieldStatus();
//        ObjectOutputStream out = connectionDetails.getOut();

        // checking if i am having pieces or not and sending bitfield message if i have pieces
        if(bitFieldStatus.getOrDefault(connectionDetails.getPeerId(), "False").compareTo("False") == 0) {
            if(peer.hasPieces()) {
                System.out.println("Sending bitfield");
                connectionDetails.send(new ActualMessage(5, String.valueOf(peer.getBitfield())));
//                out.flush();
            }
            else{// As of now sending bitfield even if 00000 so as to handle null while fetching bitfield of peer
                //using getMapBitField in (handle piece to broadcast by checking)
                // and in (updating bitfield when have is received)
                System.out.println("Sending bitfield");
                connectionDetails.send(new ActualMessage(5, String.valueOf(peer.getBitfield())));
            }
        }
        String receivedBitField = actualMessage.getMessagePayload();
        String bitField = String.valueOf(peer.getBitfield());
        peer.getMapBitField().put(connectionDetails.getPeerId(), receivedBitField);

        // adding piece indexes from received bitfield to pieces in message handler only if you dont have it in your bitfield.
        for(int i = 0; i < receivedBitField.length(); i++) {
            if(receivedBitField.charAt(i) == '1' && bitField.charAt(i) == '0')
                pieces.add(i);
        }

        // checking to send interested / Not interested Message
        for(int i = 0; i < bitField.length(); i++) {
            if(receivedBitField.charAt(i) == '1' && receivedBitField.charAt(i) != bitField.charAt(i)) {
                System.out.println("Sending Interested Message");
                connectionDetails.send(new ActualMessage(2));
//                out.flush();
                return;
            }
        }
        System.out.println("Sending Not Interested Message");
        connectionDetails.send(new ActualMessage(3));
//        out.flush();
    }

    public void handleRequest(ActualMessage actualMessage) throws IOException {
        if(!peer.getChoked().contains(connectionDetails.getPeerId())) {
            int requestedPiece = Integer.parseInt(actualMessage.getMessagePayload());
            byte[] piece = peer.getPieceAtIndex().get(requestedPiece);
//            ObjectOutputStream out = connectionDetails.getOut();
//            String pieceAsString = new String(piece);
//            connectionDetails.send(new ActualMessage(7, pieceAsString), requestedPiece);
            connectionDetails.send(new PieceMessage(7, piece));
//            out.flush();
            peer.getDownloadRate().put(connectionDetails.getPeerId(), peer.getDownloadRate().getOrDefault(connectionDetails.getPeerId(), 0) + 1);
        }
    }

    public void handlePiece(PieceMessage actualMessage) throws IOException {
        // if it is still in unchoke state and peer is not selecting neighbors then only send piece
        // how to know when peer is selecting neighbors?
//        String[] splits = actualMessage.getMessagePayload().split("-");
//        System.out.println(splits[1].getBytes().length);
//        for(int i = 0; i < splits[1].getBytes().length; i++) {
//            System.out.println(splits[1].getBytes()[i]);
//        }
//        int pieceIndex = Integer.parseInt(splits[0]);
        int pieceIndex = localRequested;
        char[] ch = peer.getBitfield();
        ch[pieceIndex] = '1';
        peer.setBitfield(ch);
//        System.out.println(peer.getBitfield()[Integer.parseInt(splits[0])]+ " heeeeeeeeeeeeeeeeeeeeeee");
        peer.getPieceAtIndex().put(pieceIndex, actualMessage.getPiece());
        peer.getRequested().remove(pieceIndex);

        //broadcast have message for that piece
        for(String peerID : peer.getPeerToConnections().keySet()) {
            ConnectionDetails connectionDetails = peer.getPeerToConnections().get(peerID);
            String bitField = peer.getMapBitField().get(peerID);
            System.out.println("sending have to " + peerID +"having bitfield" + bitField );
//            if(bitField == null || bitField.charAt(pieceIndex) == '0') {
            connectionDetails.send(new ActualMessage(4, Integer.toString(pieceIndex)));
//            ObjectOutputStream out = connectionDetails.getOut();
//            out.writeObject(new ActualMessage(4, Integer.toString(pieceIndex)));
//            out.flush();
//            send(new ActualMessage(4, Integer.toString(pieceIndex)));
//                System.out.println("sending have to " + peerID);
//            }
        }


        //checking if i have complete pieces then broadcast not interested
        if(peer.getPieceAtIndex().size() == Config.TOTALPIECES) {
            for(String peerID : peer.getPeerToConnections().keySet()) {
                ConnectionDetails connectionDetails = peer.getPeerToConnections().get(peerID);
                connectionDetails.send(new ActualMessage(3, null));
            }
            peer.writeToLog("["+ Instant.now() + "]: Peer [" + peer.getPeerID() +"] has downloaded the complete file");
            return;
        }

        // checking if to send not interested message
        ConcurrentMap<String, String> map = peer.getMapBitField();
        String bitFieldOfReceivedPeer = map.get(connectionDetails.getPeerId());
        char[] myBitField = peer.getBitfield();
        boolean hasInterestingPieces = false;
        for(int i = 0; i < bitFieldOfReceivedPeer.length(); i++) {
            if(myBitField[i] == '0' && bitFieldOfReceivedPeer.charAt(i) == '1') {
                hasInterestingPieces = true;
                break;
            }
        }
        if(!hasInterestingPieces)
            connectionDetails.send(new ActualMessage(3));
    }


}
