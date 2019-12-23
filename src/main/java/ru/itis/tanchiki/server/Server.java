package ru.itis.tanchiki.server;

import ru.kpfu.itis.rodsher.tanchiki.models.FieldGenerator;
import ru.kpfu.itis.rodsher.tanchiki.models.GameRules;
import ru.kpfu.itis.rodsher.tanchiki.models.GameState;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {
    private ServerSocket serverSocket;
    private GameState gameState;
    private GameRules gameRules;
    private List<ClientHandler> clients;
    private int port;

    public Server(int port) {
        gameRules = new GameRules();
        clients = new CopyOnWriteArrayList<>();
        this.port = port;
    }

    public void start() {
        initServer();
        try {
            serverSocket = new ServerSocket(port);
            while (true) {
                ClientHandler client = new ClientHandler(serverSocket.accept(), this);
                new Thread(client).start();
                clients.add(client);
            }
        }catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void initServer() {
        System.out.println("Server started on port " + port);
        gameRules = new GameRules();
        gameState = new GameState(gameRules.getTankHp(), gameRules.getWallHp(), gameRules.getFieldWidth(), gameRules.getFieldHeight());
        gameState.setField(FieldGenerator.generateStaticField(gameRules.getWallHp(), null, null));

    }

    public GameState getGameState() {
        return gameState;
    }

    public GameRules getGameRules() {
        return gameRules;
    }

    public List<ClientHandler> getClients() {
        return clients;
    }
}
