package gov.usgs.cida.csw;

import gov.usgs.cida.config.DynamicReadOnlyProperties;
import gov.usgs.cida.ContextConstants;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import javax.naming.NamingException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class GeonetworkSession {
    
    private static final Logger LOG = LoggerFactory.getLogger(GeonetworkSession.class);
    private static final DynamicReadOnlyProperties props; 

    static {
        DynamicReadOnlyProperties tmpProps = null;
        try {
            tmpProps = new DynamicReadOnlyProperties().addJNDIContexts(new String[0]);
        } catch (NamingException ex) {
            LOG.error("Error initializing DynamicReadOnlyProperties", ex);
        } finally {
            props = tmpProps;
        }
    }

    // currently defaults to gdp2 geonetwork
    public final String GEONETWORK_ENDPOINT = props.getProperty(ContextConstants.CSW_URL);
    // parameterize csw endpoint for different geonetwork versions
    public final String GEONETWORK_CSW = GEONETWORK_ENDPOINT + "/srv/eng/csw";
    public final String GEONETWORK_LOGIN = GEONETWORK_ENDPOINT + "/srv/eng/xml.user.login";
    public final String GEONETWORK_LOGOUT = GEONETWORK_ENDPOINT + "/srv/eng/xml.user.logout";
    public final String GEONETWORK_USER = props.getProperty(ContextConstants.CSW_USER);
    public final String GEONETWORK_PASS = props.getProperty(ContextConstants.CSW_PASS);
    
    private CookieStore cookieJar;
    private Date selfExpireCookieDate;
    private HttpClient httpClient;
    
    public GeonetworkSession() {
        if (null == GEONETWORK_ENDPOINT ||
            null == GEONETWORK_USER ||
            null == GEONETWORK_PASS) {
            throw new RuntimeException("Geonetwork dependency not declared in JNDI context,"
                    + " please set GEONETWORK_ADDR, GEONETWORK_USER, and GEONETWORK_PASS");
        }
        this.cookieJar = new BasicCookieStore();
        this.selfExpireCookieDate = new Date();
        ThreadSafeClientConnManager clientConnMgr = new ThreadSafeClientConnManager();
        this.httpClient = new DefaultHttpClient(clientConnMgr);
    }
    
    /**
     * Calls the login for geonetwork, returns the cookie store
     * @throws URISyntaxException
     * @throws IOException 
     */
    public synchronized void login() throws URISyntaxException,
                                                   IOException {
        URI loginUri = new URI(GEONETWORK_LOGIN);
        HttpContext localContext = new BasicHttpContext();
        localContext.setAttribute(ClientContext.COOKIE_STORE, cookieJar);
        HttpUriRequest request = new HttpPost(loginUri);
        // username and password should be configured somewhere
        LOG.warn("username and password are still not parameterized");
        HttpEntity entity = new StringEntity(
                "username=" + GEONETWORK_USER + "&password=" + GEONETWORK_PASS,
                "application/x-www-form-urlencoded",
                "UTF-8");
        ((HttpEntityEnclosingRequest) request).setEntity(entity);
        HttpResponse resp = httpClient.execute(request, localContext);
        // TODO check that login worked
        InputStream is = resp.getEntity().getContent();
        String loginResponse = IOUtils.toString(is);
        LOG.debug(loginResponse);
        IOUtils.closeQuietly(is);
        
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.HOUR_OF_DAY, 1);
        selfExpireCookieDate = cal.getTime();
    }

    /**
     * Calls the logout url for geonetwork, kills the cookie store
     * @throws URISyntaxException
     * @throws IOException 
     */
    public synchronized void logout() throws URISyntaxException,
                                                    IOException {
        URI logout = new URI(GEONETWORK_LOGOUT);
        HttpContext localContext = new BasicHttpContext();
        localContext.setAttribute(ClientContext.COOKIE_STORE, cookieJar);
        HttpUriRequest request = new HttpGet(logout);
        HttpResponse resp = httpClient.execute(request, localContext);
        InputStream is = resp.getEntity().getContent();
        String loginResponse = IOUtils.toString(is);
        LOG.debug(loginResponse);
        IOUtils.closeQuietly(is);
        cookieJar.clear();
    }
    
    public synchronized boolean isExistingCookie() throws URISyntaxException, IOException {
        if (cookieJar != null) {
            Date now = new Date();
            if (now.after(selfExpireCookieDate)) {
                cookieJar.clear();
//                logout();
                return false;
            }
            return !(cookieJar.getCookies().isEmpty());
        }
        return false;
    }
    
    public void clearCookieJar() {
        this.cookieJar.clear();
    }
    
    public CookieStore getCookieJar() {
        return this.cookieJar;
    }
}
