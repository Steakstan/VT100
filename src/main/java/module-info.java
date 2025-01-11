module org.msv.vt100 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires jsch;
    requires org.slf4j;
    requires ch.qos.logback.core;
    requires org.fxmisc.richtext;
    requires org.apache.poi.ooxml;
    requires java.logging;
    requires ch.qos.logback.classic;


    opens org.msv.vt100 to javafx.fxml;
    exports org.msv.vt100;
}