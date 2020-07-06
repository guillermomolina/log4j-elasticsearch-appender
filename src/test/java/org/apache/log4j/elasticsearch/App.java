package org.apache.log4j.elasticsearch;

import org.apache.log4j.Logger;

public class App {
    public static void main(final String[] args) {
        System.out.println("Hello World!");

        final Logger l = Logger.getLogger("cat.base.prueba");

        final JSONEventLayout layout = new JSONEventLayout();
        layout.setUserFields("base.node:1,base.paquetbase:((cat|es)\\.base\\.(.*))");

        final ElasticsearchBulkAppender app = new ElasticsearchBulkAppender();
        app.setLayout(layout);
        app.activateOptions();

        l.addAppender(app);

        l.warn("first");
        l.warn("second");
        l.warn("third");
        l.warn("fourth");
        l.warn("etc");
        l.warn("another");
        l.warn("error");

        l.trace("fourth shouldn't be printed");


    }
}
