package sn.messagerieae;

import javafx.application.Application;
import javafx.stage.Stage;
import sn.messagerieae.client.ClientSocket;
import sn.messagerieae.client.SceneManager;

public class MainApp extends Application {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5555;

    @Override
    public void start(Stage primaryStage) {
        SceneManager.setStage(primaryStage);
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(500);
        try {
            ClientSocket.getInstance().connect(SERVER_HOST, SERVER_PORT);
        } catch (Exception e) {
            System.err.println("Impossible de se connecter : " + e.getMessage());
        }
        SceneManager.switchTo("login.fxml", "Messagerie — Connexion");
    }

    @Override
    public void stop() {
        ClientSocket client = ClientSocket.getInstance();
        if (client.isConnected()) {
            client.sendLogout();
            client.disconnect();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
