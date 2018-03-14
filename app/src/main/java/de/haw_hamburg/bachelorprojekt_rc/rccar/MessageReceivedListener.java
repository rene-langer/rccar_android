package de.haw_hamburg.bachelorprojekt_rc.rccar;


public interface MessageReceivedListener {

    void OnByteReceived(byte data);

    /**
     * Called when an error occur
     */
    void OnConnectionError(int type);

    /**
     * Called when socket has connected to the server
     */
    void OnConnectSuccess();
}