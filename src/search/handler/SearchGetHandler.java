/*
 * This file is part of Search.
 *
 *  Search is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Search is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Search.  If not, see <http://www.gnu.org/licenses/>.
 *  (c) copyright Desmond Schmidt 2015
 */

package search.handler;

import search.cache.HitCache;
import calliope.core.Utils;
import calliope.core.constants.Database;
import calliope.core.constants.JSONKeys;
import calliope.core.database.*;
import search.constants.Service;
import search.constants.Params;
import search.exception.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import search.index.Index;
import search.index.Query;
import search.index.Match;
import search.format.Formatter;
import mvd.cache.MVDCache;
import search.index.Progress;
import edu.luc.nmerge.mvd.MVD;
import java.util.ArrayList;
import java.util.BitSet;
import org.json.simple.*;
import mvd.cache.CacheEntry;
import search.index.LiteralQuery;
/**
 * Get a Search document from the database
 * @author desmond
 */
public class SearchGetHandler extends SearchHandler
{
    String language = "english";
    static int hitsPerPage = 20;
    int firstHit;
    private static String getGroup( String version1 )
    {
        String[] parts = version1.split("/");
        String group = "";
        if ( parts.length > 0 )
        {
            StringBuilder sb = new StringBuilder();
            for ( int i=0;i<parts.length-1;i++ )
            {
                sb.append("/");
                sb.append(parts[i]);
            }
            group = sb.toString();
        }
        return group;
    }
    private static String getShortName( String version1 )
    {
        int index = version1.lastIndexOf("/");
        if ( index == -1 )
            return version1;
        else
            return version1.substring(index+1);
    }
    /**
     * convert selections to an array of positions
     * @param selections a comma-delimited list of mvd positions
     * @return an int array of positions derived therefrom
     */
    private static int[] getPositions( String selections )
    {
        int[] positions;
        String[] parts = selections.split(",");
        ArrayList<Integer> list = new ArrayList<Integer>();
        for ( int i=0;i<parts.length;i++ )
        {
            if ( parts[i].length()>0 )
                list.add( Integer.parseInt(parts[i]) );
        }
        positions = new int[list.size()];
        for ( int i=0;i<list.size();i++ )
            positions[i] = list.get(i);
        return positions;
    }
    /**
     * Get the version positions in an MVD
     * @param docid the document identifier
     * @param selections the selections or MVD positions as a comma-delimited string
     * @param version1 the first version
     * @return a JSONArray of version-positions
     * @throws SearchException 
     */
    static JSONArray getVPositions( String docid, String selections, 
        String version1 ) throws SearchException
    {
        MVD mvd;
        JSONArray jArr = new JSONArray();
        int[] positions = getPositions(selections);
        try
        {
            CacheEntry ce = MVDCache.load( Database.CORTEX, docid );
            if ( ce.mvd != null )
            {
                String group = getGroup(version1);
                String shortName = getShortName( version1 );
                int vid = ce.mvd.getVersionByNameAndGroup( shortName, group );
                if ( vid != 0 )
                {
                    int[] vPositions = Formatter.getVPositions( positions, 
                        ce.mvd, vid );
                    for ( int i=0;i<vPositions.length;i++ )
                        jArr.add( vPositions[i] );
                }
                else
                    throw new SearchException("version "+version1+" of "
                        +docid+" not found");
            }
            else
            {
                for ( int i=0;i<positions.length;i++ )
                    jArr.add( positions[i] );
            }
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
        return jArr;
    }
    public void handle(HttpServletRequest request,
            HttpServletResponse response, String urn) throws SearchException 
    {
        response.setCharacterEncoding("UTF-8");
        try 
        {
            String first = Utils.first(urn);
            String projid = request.getParameter(Params.DOCID);
            if ( !first.equals(Service.LIST) && (projid == null || projid.length()==0) )
                throw new Exception("Missing project id for search");
            else if ( first.equals(Service.BUILD) )
            {
                response.setContentType("text/plain");
                Index ind = new Index(projid);
                Progress pg = new Progress(response.getWriter());
                ind.build(pg);
                ind.save();
                response.getWriter().flush();
                String log = ind.getLog();
                System.out.println(log);
                response.getWriter().print(log);
            }
            else if ( first.equals(Service.FIND) )
            {
                String firstHitStr = request.getParameter(Params.FIRSTHIT);
                if ( firstHitStr != null )
                    firstHit = Integer.parseInt(firstHitStr);
                String queryStr = request.getParameter(Params.QUERY);
                if ( queryStr != null && queryStr.length()>0 )
                {
                    String key = HitCache.getKey(projid+queryStr); 
                    String hits;
                    if ( HitCache.exists(key) )
                    {
                        hits = HitCache.retrieve( key );
                    }
                    else
                    {
                        Index ind = Index.load(projid);
                        String lang = search.index.Utils.languageFromProjid(projid);
                        Query q = Query.parse(queryStr,lang);
                        Match[] matches = ind.find( q );
                        if ( q instanceof LiteralQuery )
                        {
                            ArrayList<Match> mList = new ArrayList<Match>();
                            LiteralQuery lq = (LiteralQuery)q;
                            for ( int i=0;i<matches.length;i++ )
                            {
                                Match m = matches[i];
                                if ( m.canBeLiteral() )
                                {
                                    CacheEntry ce = MVDCache.load( Database.CORTEX, 
                                        ind.getDocid(m.docId) );
                                    String firstTerm = m.firstTerm();
                                    BitSet bs;
                                    if ( ce.mvd != null )
                                        bs = ce.mvd.find(lq.original,
                                            m.firstPositionOfTerm(firstTerm),
                                            firstTerm);
                                    else
                                    {
                                        bs = new BitSet();
                                        bs.set(1);
                                    }
                                    int v = bs.nextSetBit(0);
                                    if ( v > 0 )
                                    {
                                        m.setFirstVersion(v);
                                        if ( !mList.contains(m) )
                                            mList.add( m );
                                    }
                                }
                            }
                            Match[] mArray = new Match[mList.size()];
                            mList.toArray(mArray);
                            matches = mArray;
                        }
                        // convert hits to JSON format with context
                        Formatter f = new Formatter(ind);
                        hits = f.matchesToHits( matches );
                        HitCache.store( hits, key );
                    }
                    JSONArray page = (JSONArray)JSONValue.parse(hits);
                    JSONObject res = new JSONObject();
                    JSONArray jHits = new JSONArray();
                    res.put( "hits", jHits );
                    res.put( "firstHit", firstHit );
                    res.put( "totalHits", page.size() );
                    res.put( "numHits", page.size()-firstHit );
                    res.put( "hitsPerPage", hitsPerPage );
                    for ( int i=firstHit;i<hitsPerPage&&i<page.size()-firstHit;i++ )
                        jHits.add( page.get(i) );
                    response.setContentType("application/json");
                    response.getWriter().println( res.toJSONString() );
                }
                else
                    throw new Exception("No search expression");
            }
            else if ( first.equals(Service.VOFFSETS) )
            {
                // docid not projid
                String docid = projid;
                // MVD offsets
                String selections = request.getParameter(Params.SELECTIONS);
                // version to fetch
                String version1 = request.getParameter(Params.VERSION1);
                //System.out.println("Selections="+selections+" version1="+version1);
                if ( docid != null && selections != null && version1 != null )
                {
                    JSONArray jArr = getVPositions( docid, selections, version1 );
                    //System.out.println("vpositions="+jArr.toJSONString());
                    response.setContentType("application/json");
                    response.getWriter().println( jArr.toJSONString() );
                }
                else
                    throw new Exception("docid, selections or version1 param missing");
            }
            else if ( first.equals(Service.LIST) )
            {
                Connection conn = Connector.getConnection();
                JSONArray jArray = new JSONArray();
                String[] indices = conn.listCollection(Database.INDICES);
                for ( int i=0;i<indices.length;i++ )
                {
                    JSONObject jObj = new JSONObject();
                    jObj.put(JSONKeys.DOCID, indices[i] );
                    String md = conn.getFromDb(Database.PROJECTS, indices[i] );
                    if ( md != null )
                    {
                        JSONObject mdObj = (JSONObject)JSONValue.parse(md);
                        if ( mdObj.containsKey(JSONKeys.AUTHOR) )
                            jObj.put( JSONKeys.AUTHOR,mdObj.get(JSONKeys.AUTHOR));
                        if ( mdObj.containsKey(JSONKeys.WORK) )
                            jObj.put( JSONKeys.WORK,mdObj.get(JSONKeys.WORK));
                    }
                    jArray.add( jObj );
                }
                response.setContentType("application/json");
                response.getWriter().println( jArray.toJSONString() );
            }
            else
                throw new Exception("Unknown GET service "+first);
        } 
        catch (Exception e) 
        {
             throw new SearchException(e);
        }
    }
    static String stringifyArray( int[] arr )
    {
        StringBuilder sb = new StringBuilder();
        for ( int i=0;i<arr.length;i++ )
        {
            sb.append(arr[i]);
            if ( i<arr.length-1 )
                sb.append(",");
        }
        return sb.toString();
    }
//    public static void main(String[] args )
//    {
//        try
//        {
//            Connector.init( Repository.MONGO, "admin", 
//            "jabberw0cky", "localhost", "calliope", 27017, 8080, "/var/www" );
//            Index ind = new Index( "english/conrad/nostromo" );
//            Match[] res1 = ind.find( new LiteralQuery("vertical ravines","en") );
//            MVD mvd = MVDCache.load("english/conrad/nostromo/1/1");
//            for ( int i=0;i<res1.length;i++ )
//            {
//                BitSet bs = Formatter.getMatchVersions(res1[i].positions, mvd );
//                for (int v = bs.nextSetBit(0); v >= 0; v = bs.nextSetBit(v+1)) 
//                {
//                    JSONArray jArr = getVPositions( 
//                        "english/conrad/nostromo/1/1",
//                        stringifyArray(res1[i].positions), 
//                        mvd.getVersionId((short)v) );
//                    System.out.println(jArr.toJSONString()+" (version "+v+")");
//                }
//            }
//        }
//        catch ( Exception e )
//        {
//        }   
//    }
}
