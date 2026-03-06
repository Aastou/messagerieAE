module sn.messagerieae {
    requires javafx.controls;
    requires javafx.fxml;
    requires static lombok;
    requires jakarta.persistence;
    requires jbcrypt;
    requires org.slf4j;
    requires com.google.gson;


    opens sn.messagerieae to javafx.fxml;
    exports sn.messagerieae;
}
