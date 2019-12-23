package ru.itis.tanchiki.server;

public class App {
    public static void main(String[] args) {
        Server server = new Server(4322);
        server.start();
    }
}
