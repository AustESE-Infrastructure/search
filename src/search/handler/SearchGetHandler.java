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

import calliope.core.Utils;
import search.constants.Service;
import search.constants.Params;
import search.exception.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import search.index.Index;
import search.index.Query;
import search.index.Match;
import search.index.Formatter;
import org.json.simple.*;

/**
 * Get a Search document from the database
 * @author desmond
 */
public class SearchGetHandler extends SearchHandler
{
    String language = "english";
    int hitsPerPage;
    int firstHit;
    public void handle(HttpServletRequest request,
            HttpServletResponse response, String urn) throws SearchException 
    {
        response.setCharacterEncoding("UTF-8");
        try 
        {
            String first = Utils.first(urn);
            String projid = request.getParameter(Params.DOCID);
            if ( projid == null || projid.length()==0 )
                throw new Exception("Missing project id for search");
            else if ( first.equals(Service.BUILD) )
            {
                response.setContentType("text/plain");
                Index ind = new Index(projid);
                ind.save();
                response.getWriter().println(ind.getLog());
            }
            else if ( first.equals(Service.FIND) )
            {
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
                        // convert hits to JSON format with context
                        Formatter f = new Formatter(ind);
                        hits = f.matchesToHits( matches );
                        HitCache.store( key, hits );
                    }
                    JSONArray page = (JSONArray)JSONValue.parse(hits);
                    JSONArray res = new JSONArray();
                    for ( int i=firstHit;i<hitsPerPage&&i<page.size()-firstHit;i++ )
                        res.add( page.get(i) );
                    response.getWriter().println( res.toJSONString() );
                }
                else
                    throw new Exception("No search expression");
            }
            else
                throw new Exception("Unknown GET service "+first);
        } 
        catch (Exception e) 
        {
             throw new SearchException(e);
        }
    }
}
