package servlet;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.xml.sax.SAXException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@WebServlet(
        name = "DownloadServlet",
        urlPatterns = {"/download"}
)

public class MoodleDownloadServlet extends HttpServlet {
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if(!MoodleCredentials.isAuthorized(request)){
                response.sendError(401, "");
                return;
            }
        } catch (SAXException | ParserConfigurationException e) {
            response.sendError(500, "Authorization failed");
            return;
        }
        // Download servlet should just get the direct link to the resource and relay the resulting inputStream
        // back to the client
        var downloadResponse  = Jsoup.connect(URLDecoder.decode(request.getHeader("downloadLink"), StandardCharsets.UTF_8.toString()))
                .cookies(MoodleCredentials.sessionCookies)
                .method(Connection.Method.GET)
                .ignoreContentType(true)
                .timeout(20000)
                .maxBodySize(0)
                .execute();
        System.out.println("!!CONTENT LENGTH: "+downloadResponse.header("Content-Length"));
        String filename = downloadResponse.header("content-disposition");
        filename = filename.substring(filename.indexOf("\"")+1, filename.lastIndexOf("\""));
        response.setHeader("Name", filename);
        OutputStream out = response.getOutputStream();
        InputStream is = downloadResponse.bodyStream();
        // write the buffer out to the client requesting the file.
        is.transferTo(out);
        is.close();
        out.close();
    }
}
