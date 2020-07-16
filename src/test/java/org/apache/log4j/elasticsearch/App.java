package org.apache.log4j.elasticsearch;

import org.apache.log4j.Logger;

public class App {
    public static void main(final String[] args) {
        System.out.println("Testing");

        final Logger l = Logger.getLogger("cat.base.prueba");

        final JSONEventLayout layout = new JSONEventLayout();
        layout.setUserFields("base.entorn:produccio,base.node:2,base.paquetbase:((cat|es)\\.base\\.(.*))");

        final ElasticsearchBulkAppender app = new ElasticsearchBulkAppender();
        app.setLayout(layout);
        app.setServer("elasticsearch.gestio.sys");
        app.setPort(9202);
        app.setUsername("elastic");
        app.setPassword("3ntradA9");
        app.setIndex("jboss");
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

        System.out.println("Wait 5 seconds");
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Done");
    }
}
