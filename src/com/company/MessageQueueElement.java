package com.company;

public class MessageQueueElement {
    ConnectionDetails connectionDetails;
    Object message;

    public MessageQueueElement(ConnectionDetails connectionDetails, Object message) {
        this.connectionDetails = connectionDetails;
        this.message = message;
    }

    public ConnectionDetails getConnectionDetails() {
        return connectionDetails;
    }

    public void setConnectionDetails(ConnectionDetails connectionDetails) {
        this.connectionDetails = connectionDetails;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }
}