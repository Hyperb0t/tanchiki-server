package ru.itis.tanchiki.server;

import ru.kpfu.itis.rodsher.tanchiki.models.*;
import ru.kpfu.itis.rodsher.tanchiki.protocol.EventType;
import ru.kpfu.itis.rodsher.tanchiki.protocol.PackageParser;
import ru.kpfu.itis.rodsher.tanchiki.protocol.PackageSetter;
import ru.kpfu.itis.rodsher.tanchiki.protocol.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private Server server;
    private PrintWriter writer;
    private BufferedReader reader;
    private PackageSetter packageSetter;
    private PackageParser packageParser;
    private Player player;

    public ClientHandler(Socket clientSocket, Server server) {
        this.packageSetter = new PackageSetter();
        this.packageParser = new PackageParser();
        this.clientSocket = clientSocket;
        this.server = server;
        try {
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void run() {
        initPlayer();
        String inputLine;
        try {
            while ((inputLine = reader.readLine()) != null) {
                handleMessage(inputLine);
            }
        } catch (SocketException e) {
            if (!e.getMessage().equals("Connection reset")) {
                throw new IllegalStateException(e);
            } else {
                disconnectPlayer();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void handleMessage(String inputLine) {
        Protocol packet = packageParser.parse(inputLine);
        if (packet.getEventType().equals(EventType.TANK_MOVED)) {
            moveTankInModel(server.getGameState(), packet);
            sendEveryoneExceptThis(inputLine);
        }
        else if(packet.getEventType().equals(EventType.TANK_FIRED)) {
            sendEveryoneExceptThis(inputLine);
        }
    }

    private void moveTankInModel(GameState gameState, Protocol packet) {
        Tank ta = null;
        for (AbstractEntity en : gameState.getField().getEntities()) {
            if (en instanceof Tank && en.getX().equals(packet.getFromContent(0).getX())
                    && en.getY().equals(packet.getFromContent(0).getY())) {
                ta = (Tank) en;
                en.setX(packet.getFromContent(0).getNewX());
                en.setY(packet.getFromContent(0).getNewY());
                ta.setDirection(packet.getFromContent(0).getDirection());
            }
        }
        gameState.getField().getFloorCells()
                [Math.round(packet.getFromContent(0).getX())][Math.round(packet.getFromContent(0).getY())]
                .setEntity(null);
        gameState.getField().getFloorCells()
                [Math.round(packet.getFromContent(0).getNewX())][Math.round(packet.getFromContent(0).getNewY())]
                .setEntity(ta);
    }

    private void sendEveryoneExceptThis(String toSend) {
        for (ClientHandler client : server.getClients()) {
            if (client.equals(this)) {
                continue;
            }
            client.getWriter().println(toSend);
        }
    }

    private void initPlayer() {
        this.player = new Player("Player" + server.getClients().size());
        server.getGameState().addPlayer(this.player.getName());

        float[][] startCoords = {{0, 1}, {15, 14}, {0, 14}, {14, 0}};
        int t = server.getClients().size() % 4;
//        float startX = (server.getClients().size() == 0) ? 0 : 15;
//        float startY = (server.getClients().size() == 0) ? 1 : 14;

        String packet = packageSetter.setGameRules(server.getGameRules().getTankHp(), server.getGameRules().getWallHp(),
                server.getGameRules().getTankSpeed(), server.getGameRules().getBulletSpeed());
        writer.println(packet);
        packet = packageSetter.setGameField(server.getGameState().getField());
        writer.println(packet);
        Tank tank = new Tank(startCoords[t][0], startCoords[t][1],
                server.getGameRules().getTankHp(), Direction.UP, null, this.player);
        server.getGameState().getField().addEntity(tank);
        for (ClientHandler client : server.getClients()) {
            packet = packageSetter.setPlayerConnected(this.player.getName(), startCoords[t][0], startCoords[t][1]);
            client.getWriter().println(packet);
        }
    }

    private void disconnectPlayer() {
        for (ClientHandler client : server.getClients()) {
            if (client.equals(this)) {
                continue;
            }
            client.getWriter().println(packageSetter.setPlayerLeft(this.player.getName(),
                    server.getGameState().getField().getTankByPlayer(this.player).getX(),
                    server.getGameState().getField().getTankByPlayer(this.player).getY()));
        }
        Tank toRemove = server.getGameState().getField().getTankByPlayer(this.player);
        server.getGameState().getField().getEntities().remove(toRemove);
        server.getGameState().getField().getFloorCells()[Math.round(toRemove.getX())][Math.round(toRemove.getY())].setEntity(null);
        server.getGameState().removePlayer(this.player);
        server.getClients().remove(this);
        System.out.println(player.getName() + " disconnected");
    }

    public PrintWriter getWriter() {
        return writer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientHandler that = (ClientHandler) o;
        return Objects.equals(clientSocket, that.clientSocket) &&
                Objects.equals(server, that.server) &&
                Objects.equals(writer, that.writer) &&
                Objects.equals(reader, that.reader) &&
                Objects.equals(packageSetter, that.packageSetter) &&
                Objects.equals(packageParser, that.packageParser) &&
                Objects.equals(player, that.player);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientSocket, server, writer, reader, packageSetter, packageParser, player);
    }
}
