package servlet;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MoodleCredentials {
    public static Map<String, String> sessionCookies;
    public static String sessionCookiesString;
    public static String sessKey;
    private static String loginUrl = "https://moodle.inholland.nl/auth/saml2/login.php?wants&idp=75681424e34ca7710fa9a3bf0b398bd2&passive=off";
    private static String succesPage = "https://moodle.inholland.nl/my/";

    public static boolean getServerCredentials() throws IOException, ParserConfigurationException, SAXException {

        File file = new File("authConfig.xml");
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(file);
        String usr = document.getElementsByTagName("Username").item(0).getTextContent();
        String pwd = document.getElementsByTagName("Password").item(0).getTextContent();

        // WebClient connects to moodle and sets the sessionCookies en sessKey Variables
        WebClient webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getCookieManager().setCookiesEnabled(true);
        webClient.getOptions().setRedirectEnabled(true);
	    webClient.getOptions().setUseInsecureSSL(true);
        HtmlPage page;
        boolean success = false;

        page = webClient.getPage(loginUrl);
        HtmlForm form = page.getForms().get(0);
        HtmlEmailInput uname =  form.getInputByName("UserName");
        HtmlPasswordInput pass =  form.getInputByName("Password");
        HtmlElement buttonElement = form.getElementsByTagName("span").get(1);
        uname.setValueAttribute(usr);
        pass.setValueAttribute(pwd);
        page = buttonElement.click();
        DomElement BypassButton = page.createElement("button");
        BypassButton.setAttribute("type", "submit");
        HtmlForm formBypass = page.getForms().get(0);
        formBypass.appendChild(BypassButton);
        page = BypassButton.click();

        if(page.getUrl().toString().equals(succesPage)) {
            // if successful, we can grab the generated sessKey from the dynamic logout link
            DomElement link = page.querySelector("a[aria-labelledby=actionmenuaction-6]");
            String sesskeyString = link.getAttribute("href").split("\\?")[1];
            sessKey = sesskeyString.split("=")[1];
            sessionCookies = new HashMap<>();
            webClient.getCookieManager().getCookies().forEach(c -> sessionCookies.put(c.getName(), c.getValue()));
            StringBuilder sbCookies = new StringBuilder("");
            MoodleCredentials.sessionCookies.forEach((k, v) -> {
                sbCookies.append(k + '=');
                sbCookies.append(v + ';');
            });
            sessionCookiesString = sbCookies.toString();
            System.out.println("Logged in, cookies and sessKey Stored");
            System.out.println(sessionCookies);
            success = true;
        }
        else {
            System.out.println("Failed login");
        }
        webClient.close();
        return success;
    }
    public static void checkCredentials(String courseId) throws ParserConfigurationException, SAXException, IOException {
        // Server does a simple check to see if the cookies and session key are still valid
        // the error can also be an enrolled error, so we check that as well (that's why we need the courseId)
        Connection.Response response = Jsoup.connect("https://moodle.inholland.nl/lib/ajax/service.php?sesskey="+ MoodleCredentials.sessKey +"&info=format_multitabs_render_section")
                .requestBody("[{\"index\": 0,\"methodname\": \"format_multitabs_render_section\",\"args\": {\"courseid\": "+ courseId +",\"sectionnr\": 4,\"moving\": 0}}]")
                .ignoreContentType(true)
                .userAgent("Mozilla")
                .cookies(MoodleCredentials.sessionCookies) // null is exception
                .timeout(3000)
                .method(Connection.Method.POST)
                .execute();
        System.out.println(response.body());
        if(response.body().contains("\"error\":true,")){
            if(response.body().contains("\"servicerequireslogin\"")){
                System.out.println("Session expired. Server reconnects...");
                if(!MoodleCredentials.getServerCredentials())
                    throw new IllegalArgumentException("Credentials are incorrect");
            }
            response = Jsoup.connect("https://moodle.inholland.nl/lib/ajax/service.php?sesskey="+ MoodleCredentials.sessKey +"&info=format_multitabs_render_section")
                    .requestBody("[{\"index\": 0,\"methodname\": \"format_multitabs_render_section\",\"args\": {\"courseid\": "+ courseId +",\"sectionnr\": 4,\"moving\": 0}}]")
                    .ignoreContentType(true)
                    .userAgent("Mozilla")
                    .cookies(MoodleCredentials.sessionCookies) // null is exception
                    .timeout(3000)
                    .method(Connection.Method.POST)
                    .execute();
            if(response.body().contains("\"requireloginerror\"")){
                try{
                    File file = new File("authConfig.xml");
                    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                            .newInstance();
                    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                    Document document = documentBuilder.parse(file);
                    Boolean enrollment = Boolean.parseBoolean(document.getElementsByTagName("AlllowEnrollment").item(0).getTextContent());
                    if(enrollment)
                        // attempt to enroll into the course
                        enroll(courseId);
                    else
                        throw new RuntimeException("Cannot enroll in course: Server has disabled automatic enrollment");
                }catch (RuntimeException e){
                    throw e;
                }
            }
            else {
                throw new RuntimeException("Unidentified Course, please create an issue on github with the course link.");
            }
        }
    }
    public static boolean isAuthorized(HttpServletRequest request) throws IOException, SAXException, ParserConfigurationException {
        // probably one of the worse ways of doing authentication. But easy.
        String pw = request.getHeader("moodleServerAuth");
        File file = new File("authConfig.xml");
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(file);
        String serverPassword = document.getElementsByTagName("ServerPassword").item(0).getTextContent();
        return serverPassword.equals(pw);
    }
    public static void enroll(String courseId){
        org.jsoup.nodes.Document doc = null;
        try {
            doc = Jsoup.connect("https://moodle.inholland.nl/course/view.php?id=" + courseId)
                    .followRedirects(true)
                    .cookies(MoodleCredentials.sessionCookies)
                    .timeout(3000)
                    .get();
        } catch (IOException e) {
            throw new RuntimeException("Jsoup timed out while enrolling.");
        }
        var element = doc.selectFirst("form.mform");
        //      System.out.println(doc.selectFirst("div.continuebutton"));
//        System.out.println(element.selectFirst("input[type=password]"));
        if(doc.selectFirst("div.continuebutton") == null && element.selectFirst("input[type=password]") == null){
            try {
                Jsoup.connect("https://moodle.inholland.nl/enrol/index.php")
                        .method(Connection.Method.POST)
                        .cookies(MoodleCredentials.sessionCookies)
                        .followRedirects(true)
                        .userAgent("Mozilla")
                        .timeout(3000)
                        .data("id", doc.selectFirst("input[name=id]").val())
                        .data("instance", doc.selectFirst("input[name=instance]").val())
                        .data("sesskey", MoodleCredentials.sessKey)
                        .data(element.select("input[type=hidden]").get(3).attr("name"), element.select("input[type=hidden]").get(3).val())
                        .data("mform_isexpanded_id_selfheader", "1")
                        .data("submitbutton", "Enrol+me")
                        .post();
            } catch (IOException e) {
                throw new RuntimeException("Jsoup timed out while inserting enrolling form.");
            }
        }
        else {
            throw new RuntimeException("Cannot enroll, course unavailable or requires a key.");
        }
    }

}
