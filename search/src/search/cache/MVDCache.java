/*
 * This file is part of Search.
 *
 *  Search is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  earch is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Search.  If not, see <http://www.gnu.org/licenses/>.
 *  (c) copyright Desmond Schmidt 2015
 */
package search.cache;

import calliope.core.constants.Database;
import calliope.core.constants.JSONKeys;
import calliope.core.database.Connection;
import calliope.core.database.Connector;
import edu.luc.nmerge.mvd.MVD;
import edu.luc.nmerge.mvd.MVDFile;
import java.util.HashMap;
import java.util.Date;
import java.util.Calendar;
import java.util.Set;
import java.util.Iterator;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import search.exception.SearchException;

/**
 * Cache recent MVDs for quick retrieval
 * @author desmond
 */
public class MVDCache extends HashMap<String,MVD> 
{
    static int MAX_SIZE = 30;
    private static MVDCache cache;
    HashMap<String,Date> times;
    public MVDCache()
    {
        times = new HashMap<String,Date>();
    }
    /**
     * Override put to check for full cache
     * @param key the docid
     * @param mvd the mvd object already loaded
     * @return the previous value or null
     */
    public MVD put( String docid, MVD mvd )
    {
        Calendar c = Calendar.getInstance();
        if ( size() == MAX_SIZE )
        {
            // find oldest
            Set<String> keys = times.keySet();
            Iterator<String> iter = keys.iterator();
            String oldestKey=null;
            Date oldest = null;
            while ( iter.hasNext() )
            {
                String key = iter.next();
                Date date = times.get( key );
                if ( oldest == null || date.compareTo(oldest)< 0 )
                {
                    oldest = date;
                    oldestKey = key;
                }
            }
            if ( oldest != null )
            {
                times.remove(oldestKey);
                this.remove(oldestKey);
            }
        }
        super.put(docid,mvd);
        times.put(docid,c.getTime());
        return null;
    }
    /**
     * Get an MVD and save it in a cache in case we need it later
     * @param docid the document id for the MVD to be used for retrieval
     * @return an MVD object
     * @throws SearchException 
     */
    public static MVD load( String docid ) throws SearchException
    {
        try
        {
            if ( cache == null )
                cache = new MVDCache();
            if ( cache.containsKey(docid) )
                return cache.get(docid);
            else
            {
                MVD mvd = null;
                Connection conn = Connector.getConnection();
                String bson = conn.getFromDb( Database.CORTEX, docid );
                if ( bson != null )
                {
                    JSONObject jObj = (JSONObject)JSONValue.parse(bson);
                    String body = (String)jObj.get(JSONKeys.BODY);
                    mvd = MVDFile.internalise(body);
                    cache.put( docid, mvd );
                }
                return mvd;
            }
        }
        catch ( Exception e )
        {
            throw new SearchException( e );
        }
    }
}
