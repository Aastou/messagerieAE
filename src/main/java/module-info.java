module sn.messagerieae {
    requires javafx.controls;
    requires javafx.fxml;


    opens sn.messagerieae to javafx.fxml;
    exports sn.messagerieae;
}