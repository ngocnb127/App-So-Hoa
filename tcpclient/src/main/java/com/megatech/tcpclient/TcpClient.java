package com.megatech.tcpclient;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.CharBuffer;
import java.util.Observable;

public class TcpClient extends Observable {
    private static final String TAG = "TcpClient";

    private String address;
    private Integer port;
    private final Integer timeout = 2000;

    private TcpClientState state = TcpClientState.DISCONNECTED;

    PrintWriter bufferOut;
    BufferedReader bufferIn;

    InputStream inputStream;

    OutputStream writer;

    private Socket socket;

    public TcpClient() {  }

    public TcpClient(String address, int port) {
        this.address = address;
        this.port = port;
    }

    protected void fireEvent(TcpEvent event) {
        setChanged();
        notifyObservers(event);
        clearChanged();
    }
    public void setPort(int port) {
        if(state == TcpClientState.CONNECTED) {
            throw new RuntimeException("Cannot change port while connected");
        }
        this.port = port;
    }
    public void connect() {
        if(state == TcpClientState.DISCONNECTED || state == TcpClientState.FAILED) {
            if(address == null || port == null) {
                throw new RuntimeException("Address or port missing");
            }
            new ConnectThread().start();
        } else {
            throw new RuntimeException("This client is already connected or connecting");
        }
    }
    public void sendMessage(String message) {
        if(state == TcpClientState.CONNECTED) {
            new SendMessageThread(message).start();
        } else {
            throw new RuntimeException("This client is not connected, and cannot send any message");
        }
    }

    public void sendMessage(char[] bytes) {
        if(state == TcpClientState.CONNECTED) {
            new SendMessageThread(bytes).start();
        } else {
            throw new RuntimeException("This client is not connected, and cannot send any message");
        }
    }

    public  boolean isConnected()
    {
        return state == TcpClientState.CONNECTED;
    }
    public void disconnect() {
        new DisconnectThread().run();
    }

    public void destroy() {
        try {
            if (socket != null)
                socket.close();
            socket = null;
        } catch (IOException ex) {
            Log.e(TAG, "Could not close socket");
        }
    }

    private class SendMessageThread extends Thread {
        private String messageLine;
        private final char[] messageChar;

        public SendMessageThread(String message) {
            this.messageLine = message ;

            this.messageChar = message.toCharArray();
        }

        public SendMessageThread(char[] bytes) {
            //this.messageLine = message ;
            this.messageChar = bytes;
        }

        @Override
        public void run() {
            if(bufferOut.checkError()) {
                try {
                    bufferOut.flush();
                    bufferOut.close();

                    bufferIn.close();
                } catch(IOException e) {
                    Log.e(TAG, "Error sending this message: " + e);
                }
            } else {

                //bufferOut.print(messageChar);
                //bufferOut.flush();
                byte[] bytes = new byte[messageChar.length];
                try {
                    for (int i=0; i<messageChar.length; i++)
                        bytes[i] = (byte)messageChar[i];
                    writer.write(bytes);
                    writer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                fireEvent(new TcpEvent(TcpEventType.MESSAGE_SENT,messageChar));
            }
        }
    }
    private class ConnectThread extends Thread {
        @Override
        public void run() {
            try {
                state = TcpClientState.CONNECTING;
                fireEvent(new TcpEvent(TcpEventType.CONNECTION_STARTED, null));

                socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddress.getByName(address), port), timeout);

                bufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                inputStream =socket.getInputStream();
                bufferIn = new BufferedReader(new InputStreamReader(inputStream));

                writer = socket.getOutputStream();
                state = TcpClientState.CONNECTED;
                fireEvent(new TcpEvent(TcpEventType.CONNECTION_ESTABLISHED, null));

                new ReceiveMessagesThread().start();

            } catch(SocketTimeoutException e) {
                fireEvent(new TcpEvent(TcpEventType.CONNECTION_FAILED, e));

                Log.e(TAG, "Socket timed out: " + e);
                state = TcpClientState.FAILED;
            } catch(IOException e) {
                fireEvent(new TcpEvent(TcpEventType.CONNECTION_FAILED, e));

                Log.e(TAG, "Could not connect to host: " + e);
                state = TcpClientState.FAILED;
            }
        }

    }
    private class ReceiveMessagesThread extends Thread {
        @Override
        public void run() {
            while(state == TcpClientState.CONNECTED) {
                try {
                    StringBuilder message = new StringBuilder();

                    //char[] buff = new char[1024];
                    int i=0;
                    while (inputStream.available()>0) {
                        message.append( (char)inputStream.read());
                        i++;
                    }



                    if(i>0) {
                        fireEvent(new TcpEvent(TcpEventType.MESSAGE_RECEIVED, message.toString().toCharArray()));
                    }
                } catch(IOException e) {
                    fireEvent(new TcpEvent(TcpEventType.CONNECTION_LOST, null));

                    try {
                        bufferOut.flush();
                        bufferOut.close();

                        bufferIn.close();
                        if (socket != null)
                            socket.close();
                    } catch (IOException er) {
                        Log.e(TAG, "Error clearing connection: " + er);
                    }

                    state = TcpClientState.DISCONNECTED;
                }
            }
        }
    }
    private class DisconnectThread extends Thread {
        @Override
        public void run() {
            try {
                bufferOut.flush();
                bufferOut.close();

                bufferIn.close();

                socket.close();
                state = TcpClientState.DISCONNECTED;
            } catch(IOException e) {
                Log.e(TAG, "Error disconnecting this client: " + e);
            }

            fireEvent(new TcpEvent(TcpEventType.DISCONNECTED, null));
        }
    }
}
