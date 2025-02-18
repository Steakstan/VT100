module org.msv.vt100 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires jsch;
    requires org.slf4j;
    requires ch.qos.logback.core;
    requires org.fxmisc.richtext;
    requires org.apache.poi.ooxml;
    requires ch.qos.logback.classic;
    requires com.google.gson;
    requires java.prefs;


    opens org.msv.vt100 to javafx.fxml;
    exports org.msv.vt100;
    exports org.msv.vt100.ssh;
    opens org.msv.vt100.ssh to javafx.fxml;
    exports org.msv.vt100.ansiisequences;
    opens org.msv.vt100.ansiisequences to javafx.fxml;
    exports org.msv.vt100.core;
    opens org.msv.vt100.core to javafx.fxml;
}