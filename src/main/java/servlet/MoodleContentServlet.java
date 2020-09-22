package servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.jasper.compiler.JspUtil;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xml.sax.SAXException;

import javax.net.ssl.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@WebServlet(
        name = "ContentServlet",
        urlPatterns = {"/content"}
)
public class MoodleContentServlet extends HttpServlet {
    // Content servlet is responsible for getting pages the client requests.

    static {
        TrustManager[] trustAllCertificates = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null; // Not relevant.
                    }
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Do nothing. Just allow them all.
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Do nothing. Just allow them all.
                    }
                }
        };

        HostnameVerifier trustAllHostnames = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true; // Just allow them all.
            }
        };

        try {
            System.setProperty("jsse.enableSNIExtension", "false");
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCertificates, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames);
        }
        catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if(!MoodleCredentials.isAuthorized(request)){
                response.sendError(401, "");
                return;
            }
        } catch (SAXException | ParserConfigurationException e) {
            response.sendError(500, "Authorization failed");
            return;
        }
        // parameter "type" must be present at all times
        // this method handles getting structure and getting content for a specific section.
        if(request.getParameter("type") == null){
            response.sendError(400, "type parameter required");
            return;
        }
        try{
            switch (request.getParameter("type")){
                case "content":
                    response.setContentType("text/html; charset=UTF-8");
                    //response.getOutputStream().write();
                    response.getWriter().println(getContent(request.getParameter("courseId"), request.getParameter("sectionId"))); // TODO: TESTING, comments weghalen if geen effect.
                    //response.getWriter().println(testingPost(request.getParameter("courseId"), request.getParameter("sectionId")));
                    break;
                case "structure":
                    response.getWriter().println(getStructureHtml(request.getParameter("courseId")));
                    break;
            }
        }catch (RuntimeException e){
            response.sendError(400, e.getMessage());
            System.out.println(e.getMessage());
        }

    }
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
        // request body has two params: type and url
        // type can be "peek" or "visit"
        Scanner s = null;
        try {
            s = new Scanner(request.getInputStream(), "UTF-8");
        } catch (IOException e) {
            response.sendError(500, "input error");
            return;
        }
        String body = s.hasNext() ? s.next() : "";
        HashMap<String, String> info = new HashMap<>();
        try{
            String[] params = body.split(";");
            info.put("type", params[0]);
            info.put("url", params[1]);
        }catch (RuntimeException e){
            response.sendError(400, "type and/or url parameters invalid.");
            return;
        }
        try{
            response.setContentType("text/html; charset=UTF-8");
            switch (info.get("type")){
                case "peek":
                    var headers =  getHeaders(info.get("url"));
                    if(headers == null){
                        response.getWriter().println("Moodle server is taking way too long to process the document (which is suspected to be a mod-book epub with a lot of videos)");
                        break;
                    }
                    StringBuilder headersString = new StringBuilder("");
                    headers.forEach((k,v)->{headersString.append(k + "~" + v + "|");});
                    response.getWriter().println(headersString);
                    break;
                case "visit":
                    if(isLegalUrl(info.get("url"))){
                        response.getWriter().println(getHtml(info.get("url")));
                    }
                    else {
                        response.sendError(400, "Nice try.");
                    }break;
            }
        }catch (RuntimeException e){
            response.sendError(500, e.getMessage());
        }
    }
    private Map<String, String> getHeaders(String url) {
        Connection.Response response = null;
        try {
            response = Jsoup.connect(url)
                    .userAgent("Mozilla")
                    .ignoreContentType(true)
                    .cookies(MoodleCredentials.sessionCookies)
                    .timeout(20000)
                    .method(Connection.Method.HEAD)
                    .execute();
        } catch (IOException e) {
            if(e instanceof SocketTimeoutException){
                System.out.println("ERROR WITH HEADERS: ");
                System.out.println(e.getClass());
                System.out.println(e.getMessage());
                System.out.println(e.getLocalizedMessage());
                e.printStackTrace();
                return null;
            }
            throw new RuntimeException("Jsoup could not connect. " + e.getMessage());
        }
        return response.headers();
    }
    private String getHtml(String url){
        try {
            URL htmlUrl = new URL(url);
            HttpURLConnection httpCon = (HttpURLConnection) htmlUrl.openConnection();
            httpCon.setRequestProperty("Cookie", MoodleCredentials.sessionCookiesString);
            httpCon.setRequestMethod("GET");
            httpCon.connect();
            Document doc = Jsoup.parse(IOUtils.toString((InputStream) httpCon.getContent(), "UTF-8"));
            doc.select("footer").remove();
            doc.select("header").remove();
            return doc.select("body").html();
        } catch (IOException e) {
            throw new RuntimeException("Could not get url html");
        }
    }

    private boolean isLegalUrl(String url){
        // some moodle urls contain account info
        // only allow certain links, and fuck the rest.
        // moodle.inholland.nl/mod/ is the only one allowed to be visited for html acquisition purposes.
        return url.startsWith("https://moodle.inholland.nl/mod/");
    }

    private String getStructureHtml(String courseId){
        if(courseId == null)
            throw new RuntimeException("courseId parameter missing");
        String courseUrl = "https://moodle.inholland.nl/course/view.php?id=" + courseId;
        try {
            MoodleCredentials.checkCredentials(courseId);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Credentials could not be verified or are incorrect. Try again or contact the owner of the server.");
        }
        Document doc = null;
        try {
            doc = Jsoup.connect(courseUrl)
                    .userAgent("Mozilla")
                    .cookies(MoodleCredentials.sessionCookies)
                    .timeout(3000)
                    .get();
        } catch (IOException e) {
            throw new RuntimeException("Jsoup couldn't connect whilst getting the structure. \n url: " + courseUrl + "\n error: " + e.getMessage());
        }
        return doc.selectFirst("div#page").html();
    }
    private Document getContent(String courseId, String sectionId){
        String doc = getSectionTest(courseId, sectionId);//getSectionInfo(courseId, sectionId);
        String errorString =  doc.substring(doc.indexOf("error"), doc.indexOf(','));
        errorString = errorString.split(":")[1];
        if(errorString.contains("true")) // if an error is present in the call
        {
            try {
                if(!MoodleCredentials.getServerCredentials())
                    throw new RuntimeException("Server credentials are invalid. Please contact the owner to correct the credentials.");

            } catch (IOException | ParserConfigurationException | SAXException e) {
                // todo: log incident here.
                throw new RuntimeException("Server credentials check failed unexpectedly.");
            }
        }
        // Clean up and return only the required html
        String html = doc.substring(doc.indexOf("<li"), doc.lastIndexOf("/li>"));
        html.replaceAll("\\\\&qout;", "");
        html = html.replaceAll("\\\\", "");
        return Jsoup.parse(html);
    }
    private String getSectionTest(String courseId, String sectionId) {

        try {
            URL url = new URL("https://moodle.inholland.nl/lib/ajax/service.php?sesskey=" + MoodleCredentials.sessKey + "&info=format_multitabs_render_section");
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setRequestProperty("Cookie", MoodleCredentials.sessionCookiesString);
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("POST");
            OutputStream os = httpCon.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
            osw.write("[{\"index\": 0,\"methodname\": \"format_multitabs_render_section\",\"args\": {\"courseid\": " + courseId + ",\"sectionnr\": " + sectionId + ",\"moving\": 0}}]");
            osw.flush();
            osw.close();
            os.close();
            httpCon.connect();
            ObjectMapper objectMapper = new ObjectMapper();
            var json =objectMapper.readTree((InputStream) httpCon.getContent());
            System.out.println(json.toPrettyString());
            return json.toPrettyString();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Moodle did not respond to the server's content request.");
        }
    }
}
