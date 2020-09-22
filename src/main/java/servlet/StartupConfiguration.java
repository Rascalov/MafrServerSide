package servlet;

import org.xml.sax.SAXException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

@WebListener
public class StartupConfiguration implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        try {
            if(!MoodleCredentials.getServerCredentials()){
                System.out.println("ERROR: LOG IN FAILED, CONTACT SERVER HOST TO CHECK CREDENTIALS");
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {

    }
}
