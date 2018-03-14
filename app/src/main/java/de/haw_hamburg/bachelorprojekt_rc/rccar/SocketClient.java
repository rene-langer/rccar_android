package de.haw_hamburg.bachelorprojekt_rc.rccar;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SocketClient {
    private static String TAG = "TCPClient"; //For debugging, always a good idea to have defined

    boolean receiveThreadRunning = false;
    private long startTime = 0l;

    private Socket connectionSocket;

    //Runnables for sending and receiving data
    private SendRunnable sendRunnable;
    private ReceiveRunnable receiveRunnable;
    //Threads to execute the Runnables above
    private Thread sendThread;
    private Thread receiveThread;

    private MessageReceivedListener mList;

    byte[] dataToSend;
    private String severIp =   "192.168.4.1";
    private int serverPort = 9999;

    public SocketClient(){}

    public SocketClient(MessageReceivedListener listener){
        super();
        this.addMessageReceivedListener(listener);
    }

    public void addMessageReceivedListener(MessageReceivedListener listener){
        this.mList = listener;
    }

    /**
     * Returns true if TCPClient is connected, else false
     * @return Boolean
     */
    public boolean isConnected() {
        return connectionSocket != null && connectionSocket.isConnected() && !connectionSocket.isClosed();
    }

    /**
     * Open connection to server
     */
    public void Connect(String ip, int port) {
        severIp = ip;
        serverPort = port;
        dataToSend = null;
        new Thread(new ConnectRunnable()).start();
    }

    /**
     * Close connection to server
     */
    public void Disconnect() {
        stopThreads();

        try {
            connectionSocket.close();
            Log.d(TAG,"Disconnected!");
        } catch (IOException e) { }

    }

    /**
     * Send data to server
     * @param data byte array to send
     */
    public void WriteData(byte[] data) {
        Log.d(TAG, "Byte elements to send: "+ data.length);
        if (isConnected()) {
            startSending();
            sendRunnable.Send(data);
        }
    }

    /**
     * Send command to server
     * @param cmd Commands as string to send
     */
    public void WriteCommand(String cmd) {
        if (isConnected()) {
            startSending();
            sendRunnable.SendCMD(cmd.getBytes());
        }
    }

    private void stopThreads() {
        if (receiveThread != null)
            receiveThread.interrupt();
            receiveThreadRunning = false;

        if (sendThread != null)
            sendThread.interrupt();
    }

    private void startSending() {
        if(sendRunnable != null){
            try {
                sendRunnable.finalize();
            }
            catch (Throwable e){
                Log.e(TAG, "Could not finalize sendRunnable");
                e.printStackTrace();
            }
        }
        sendRunnable = new SendRunnable(connectionSocket);
        if(sendThread != null){
            sendThread.interrupt();
        }
        sendThread = new Thread(sendRunnable);
        sendThread.start();
    }

    private void startReceiving() {
        if(receiveRunnable != null){
            try {
                receiveRunnable.finalize();
            }
            catch (Throwable e){
                Log.e(TAG, "Could not finalize sendRunnable");
                e.printStackTrace();
            }
        }
        receiveRunnable = new ReceiveRunnable(connectionSocket);
        if(receiveThread != null){
            receiveThread.interrupt();
        }
        receiveThread = new Thread(receiveRunnable);
        receiveThread.start();
    }

    public class ReceiveRunnable implements Runnable {
        private Socket sock;
        private InputStream input;

        public ReceiveRunnable(Socket server) {
            sock = server;
            try {
                input = sock.getInputStream();
            } catch (Exception e) { }
        }

        public void __run__() {
            Log.d(TAG, "Receiving started");
            while (!Thread.currentThread().isInterrupted() && isConnected()) {
                if (!receiveThreadRunning)
                    receiveThreadRunning = true;

                startTime = System.currentTimeMillis();
                try {
                    byte[] data = new byte[4];
                    //Read the first integer, it defines the length of the data to expect
                    input.read(data,0,data.length);
                    int length = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();

                    //Read the second integer, it defines the type of the data to expect
                    input.read(data,0,data.length);
                    int type = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();

                    int read = 0; int downloaded = 0;

                    if (type == TCPCommands.TYPE_CMD) {
                        //We're expecting a command/message from the server (Like a list of files)

                        //Allocate byte array large enough to contain the data to come
                        data = new byte[length];
                        StringBuilder sb = new StringBuilder();
                        InputStream bis = new BufferedInputStream(input);

                        //Read until all data is read or until we have read the expected amount
                        while ((read = bis.read(data)) != -1) {
                            downloaded += read;
                            sb.append(new String(data,0, read, "UTF-8")); //Append the data to the StringBuilder
                            if (downloaded == length) //We have what we expected, break out of the loop

                                break;
                        }
                    } else if (type == TCPCommands.TYPE_FILE_CONTENT) {

                        //We're expecting a file/raw bytes from the server (Like a file)

                        //We download the data 2048 bytes at the time
                        byte[] inputData = new byte[2048];
                        InputStream bis = new BufferedInputStream(input);

                        //Read until all data is read or until we have read the expected amount
                        while ((read = bis.read(inputData)) != -1) {
                            //Buffer loop
                            downloaded += read;
                            if (downloaded == length)//We have what we expected, break out of the loop
                                break;
                        }

                    }

                    long time = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "Data received! Took: " + time + "ms and got: " + (downloaded + 8) + "bytes");

                    //Stop listening so we don't have e thread using up CPU-cycles when we're not expecting data
                    stopThreads();
                } catch (Exception e) {
                    Disconnect(); //Gets stuck in a loop if we don't call this on error!
                }
            }
            receiveThreadRunning = false;
            Log.d(TAG, "Receiving stopped");
        }

        @Override
        public void run(){
            Log.d(TAG, "Receiving started");
            while (!Thread.currentThread().isInterrupted() && isConnected()) {
                if (!receiveThreadRunning)
                    receiveThreadRunning = true;

                startTime = System.currentTimeMillis();
                try {
                    byte[] data = new byte[1];
                    input.read(data, 0, 1);
                    mList.OnByteReceived(data[0]);
                }
                catch (IOException e){
                    mList.OnConnectionError(2);
                }
            }
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
        }
    }

    public class SendRunnable implements Runnable {

        byte[] data;
        private OutputStream out;
        private boolean hasMessage = false;
        int dataType = 1;

        public SendRunnable(Socket server) {
            try {
                this.out = server.getOutputStream();
            } catch (IOException e) {
            }
        }

        /**
         * Send data as bytes to the server
         * @param bytes
         */
        public void Send(byte[] bytes) {
            this.data = bytes;
            dataType = TCPCommands.TYPE_FILE_CONTENT;
            this.hasMessage = true;
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
        }

        /**
         * Send a command/message to the server
         * @param bytes
         */
        public void SendCMD(byte[] bytes) {
            this.data = bytes;
            dataType = TCPCommands.TYPE_CMD;
            this.hasMessage = true;
        }

        @Override
        public void run() {
            Log.d(TAG, "Sending started");
            while (!Thread.currentThread().isInterrupted() && isConnected()) {
                if (this.hasMessage) {
                    startTime = System.currentTimeMillis();
                    try {
                        this.out.flush();
                        //Send the data
                        this.out.write(data, 0, data.length);
                        //Flush the stream to be sure all bytes has been written out
                        this.out.flush();
                    } catch (IOException e) { }
                    this.hasMessage = false;
                    this.data =  null;
                    long time = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "Command has been sent! Current duration: " + time + "ms");
                    if (!receiveThreadRunning)
                        startReceiving(); //Start the receiving thread if it's not already running
                }
            }
            Log.d(TAG, "Sending stopped");
        }
    }

    public class ConnectRunnable implements Runnable {

        public void run() {
            try {

                Log.d(TAG, "C: Connecting...");
                InetAddress serverAddr = InetAddress.getByName(severIp);
                startTime = System.currentTimeMillis();
                //Create a new instance of Socket
                connectionSocket = new Socket();

                //Start connecting to the server with 5000ms timeout
                //This will block the thread until a connection is established
                connectionSocket.connect(new InetSocketAddress(serverAddr, 9999), 5000);

                long time = System.currentTimeMillis() - startTime;
                Log.d(TAG, "Connected to " + connectionSocket.getInetAddress() +"; Duration: " + time + "ms");

                if (mList != null){
                    mList.OnConnectSuccess();
                }

            } catch (Exception e) {
                if (mList != null){
                    mList.OnConnectionError(1);
                }
                e.printStackTrace();
            }
            Log.d(TAG, "Connetion thread stopped");
        }
    }

}