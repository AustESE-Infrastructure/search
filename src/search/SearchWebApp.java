/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package search;

import calliope.core.Utils;
import calliope.core.database.Connector;
import calliope.core.database.Repository;
import calliope.core.exception.CalliopeException;
import calliope.core.exception.CalliopeExceptionMessage;
import java.util.Enumeration;
import search.handler.*;
import search.exception.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import search.constants.Service;

/**
 *
 * @author desmond
 */
public class SearchWebApp extends HttpServlet
{
    static String host = "localhost";
    static String user ="admin";
    static String password = "jabberw0cky";
    static int dbPort = 27017;
    public static int wsPort = 8080;
    static String webRoot = "/var/www";
    Repository repository = Repository.MONGO;
    /**
     * Safely convert a string to an integer
     * @param value the value probably an integer
     * @param def the default if it is not
     * @return the value or the default
     */
    private int getInteger( String value, int def )
    {
        int res = def;
        try
        {
            res = Integer.parseInt(value);
        }
        catch ( NumberFormatException e )
        {
        }
        return res;
    }
    /**
     * Safely convert a string to a Repository enum
     * @param value the value probably a repo type
     * @param def the default if it is not
     * @return the value or the default
     */
    private Repository getRepository( String value, Repository def )
    {
        Repository res = def;
        try
        {
            res = Repository.valueOf(value);
        }
        catch ( IllegalArgumentException e )
        {
        }
        return res;
    }
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, java.io.IOException
    {
        try
        {
            String method = req.getMethod();
            String target = req.getRequestURI();
            if ( !Connector.isOpen() )
            {
                Enumeration params = 
                    getServletConfig().getInitParameterNames();
                while (params.hasMoreElements()) 
                {
                    String param = (String) params.nextElement();
                    String value = 
                        getServletConfig().getInitParameter(param);
                    if ( param.equals("webRoot") )
                        webRoot = value;
                    else if ( param.equals("dbPort") )
                        dbPort = getInteger(value,27017);
                    else if (param.equals("wsPort"))
                        wsPort= getInteger(value,8080);
                    else if ( param.equals("username") )
                        user = value;
                    else if ( param.equals("password") )
                        password = value;
                    else if ( param.equals("repository") )
                        repository = getRepository(value,Repository.MONGO);
                    else if ( param.equals("indexRoot") )
                        JettyServer.indexRoot = value;
                    else if ( param.equals("host") )
                        host = value;
                }
                Connector.init( repository, user, 
                    password, host, dbPort, wsPort, webRoot );
            }
            target = Utils.pop( target );
            SearchHandler handler;
            String service = Utils.first(target);
            if ( service.equals(Service.BUILD)||service.equals(Service.FIND) )
            {
                if ( method.equals("GET") )
                    handler = new SearchGetHandler();
                else if ( method.equals("POST") )
                    handler = new SearchPostHandler();
                else
                    throw new SearchException("Unknown http method "+method);
                resp.setStatus(HttpServletResponse.SC_OK);
                handler.handle( req, resp, target );
            }
            else
                throw new SearchException("Unknown service "+service);
        }
        catch ( Exception e )
        {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            CalliopeException he = new CalliopeException( e );
            resp.setContentType("text/html");
            try 
            {
                resp.getWriter().println(
                    new CalliopeExceptionMessage(he).toString() );
            }
            catch ( Exception e2 )
            {
                e.printStackTrace( System.out );
            }
        }
    }
}