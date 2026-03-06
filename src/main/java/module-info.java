module sn.messagerieae {
    requires javafx.controls;
    requires javafx.fxml;
    requires static lombok;
    requires jakarta.persistence;


    opens sn.messagerieae to javafx.fxml;
    exports sn.messagerieae;
}
