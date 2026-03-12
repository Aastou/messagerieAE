package sn.messagerieae.client;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Gestionnaire centralisé de navigation entre les vues JavaFX (FXML). */
public class SceneManager {

    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);
    private static Stage primaryStage; // Fenêtre principale partagée par toutes les vues

    /** Initialise la fenêtre principale. À appeler une seule fois au démarrage. */
    public static void setStage(Stage stage) {
        primaryStage = stage;
    }

    /** Retourne la fenêtre principale. */
    public static Stage getStage() {
        return primaryStage;
    }

    /**
     * Charge et affiche une nouvelle vue FXML dans la fenêtre principale.
     * Le fichier CSS global est appliqué automatiquement.
     *
     * @param fxmlFile nom du fichier FXML (ex. "chat.fxml")
     * @param title    titre de la fenêtre
     */
    public static void switchTo(String fxmlFile, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource(
                            "/sn/messagerieae/views/" + fxmlFile));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            // Application du style global à chaque changement de vue
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

    /**
     * Variante de {@link #switchTo} qui retourne aussi le contrôleur instancié.
     * Utile quand on a besoin d'injecter des données dans le contrôleur après navigation.
     *
     * @param <T>      type du contrôleur
     * @param fxmlFile nom du fichier FXML
     * @param title    titre de la fenêtre
     * @return le contrôleur de la vue chargée, ou null en cas d'erreur
     */
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
            return loader.getController(); // Retourne le contrôleur pour configuration post-chargement
        } catch (IOException e) {
            logger.error("Erreur chargement vue : {}", fxmlFile, e);
            return null;
        }
    }
}
