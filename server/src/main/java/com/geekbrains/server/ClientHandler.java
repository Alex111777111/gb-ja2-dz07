package com.geekbrains.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private String nickname;
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    public String getNickname() {
        return nickname;
    }

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            new Thread(() -> {
                try {
                    while (true) {
                        String msg = in.readUTF();
                        // /auth login1 pass1
                        if (msg.startsWith("/auth ")) {
                            String[] tokens = msg.split("\\s");
                            String nick = server.getAuthService().getNicknameByLoginAndPassword(tokens[1], tokens[2]);
                            if (nick != null && !server.isNickBusy(nick)) {
                                sendMsg("/authok " + nick);
                                server.subscribe(this);
                                nickname = nick;
                                break;
                            }
                        }
                    }
                    while (true) {
                        String msg = in.readUTF();
                        if (msg.equals("/end")) {
                            break;
                        } else if (msg.equals("/online")) {
                            sendMsg("server: " + server.getOnlineUsers());
                        } else if (msg.trim().startsWith("/w")) {
                            String[] strings = msg.trim().split(" ");
                            if (!checkFormatPrivateMsg(strings)) {
                                continue;
                            }
                            String recipientNick = strings[1];
                            int index = msg.indexOf(recipientNick) + recipientNick.length() + 1;
                            String cleanMsg = msg.substring(index);

                            server.sendPrivateMsg(recipientNick, nickname, cleanMsg);
                            sendMsg("msg to " + recipientNick + ": " + cleanMsg);
                        } else {
                            server.broadcastMsg(nickname + ": " + msg);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    ClientHandler.this.disconnect();
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkFormatPrivateMsg(String[] strings) {
        final String SERVER_ERR = "server: *error* ";
        if (strings == null || strings.length == 0) {
            sendMsg(SERVER_ERR + "msg is empty!");
            return false;
        } else if (strings.length == 1) {
            sendMsg(SERVER_ERR + "no nick!");
            return false;
        } else if (strings.length == 2) {
            sendMsg(SERVER_ERR + "empty message!");
            return false;
        } else {
            String nickname = strings[1];
            if (!server.isNickBusy(nickname)) {
                sendMsg(SERVER_ERR + "the user [" + nickname + "] is offline or not found in db! Please, check the nick you send to.");
                return false;
            }
        }
        return true;
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        server.unsubscribe(this);
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
