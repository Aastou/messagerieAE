package sn.messagerieae.client;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SceneManager {

    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);
    private static Stage primaryStage;

    public static void setStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getStage() {
        return primaryStage;
    }

    public static void switchTo(String fxmlFile, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource(
                            "/sn/messagerieae/views/" + fxmlFile));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            String css = SceneManager.class.getResource(
                    "/sn/messagerieae/styles/style.css").toExternalForm();
            scene.getStylesheets().add(css);
            primaryStage.setScene(scene);
            primaryStage.setTitle(title);
            primaryStage.show();
            logger.info("Navigation vers : {}", title);
        } catch (IOException e) {
            logger.error("Erreur chargement vue : {}", fxmlFile, e);
        }
    }

    public static <T> T switchToAndGetController(String fxmlFile, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource(
                            "/sn/messagerieae/views/" + fxmlFile));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            String css = SceneManager.class.getResource(
                    "/sn/messagerieae/styles/style.css").toExternalForm();
            scene.getStylesheets().add(css);
            primaryStage.setScene(scene);
            primaryStage.setTitle(title);
            primaryStage.show();
            return loader.getController();
        } catch (IOException e) {
            logger.error("Erreur chargement vue : {}", fxmlFile, e);
            return null;
        }
    }
}
