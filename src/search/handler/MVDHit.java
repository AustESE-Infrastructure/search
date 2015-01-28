/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package search.handler;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import search.exception.SearchException;
import java.util.HashMap;
import java.util.ArrayList;
/**
 *
 * @author desmond
 */
public class MVDHit {
    public String docid;
    public String[] vids;
    public String database;
    /**
     * Create an MVDHit
     * @param docid its docid
     * @param vids an array of version ids or null
     * @param database the database it came from
     */
    protected MVDHit( String docid, String[] vids, String database )
    {
        this.docid = docid;
        this.vids = vids;
        this.database = database;
    }
    /**
     * Build a list of MVD hits in the same order as the raw hits
     * @param searcher the searcher for the relevant index
     * @param hits the array of raw hits, many per docid
     * @return an array of MVDHits
     * @throws SearchException 
     */
    public static MVDHit[] build( IndexSearcher searcher, ScoreDoc[] hits )
        throws SearchException
    {
        try
        {
            HashMap<String,ArrayList<String>> map = 
                new HashMap<String,ArrayList<String>>();
            for ( int i=0;i<hits.length;i++ )
            {
                Document doc = searcher.doc(hits[i].doc);
                String docid = doc.get("docid");
                if ( docid != null )
                {
                    String vid = doc.get("vid");
                    if ( map.containsKey(docid) )
                    {
                        ArrayList<String> vids = null;
                        vids = map.get(docid);
                        if ( vids == null && vid != null )
                            vids = new ArrayList<String>();
                        if ( vid != null )
                            vids.add( vid );
                        map.put( docid, vids);
                    }
                    else
                    {
                        ArrayList<String> vids = null;
                        if ( vid != null )
                        {
                            vids = new ArrayList<String>();
                            vids.add( vid );
                        }
                        map.put( docid, vids );
                    }
                }
            }
            int j = 0;
            MVDHit[] mvdHits = new MVDHit[map.size()];
            for ( int i=0;i<hits.length;i++ )
            {
                Document doc = searcher.doc(hits[i].doc);
                String database = doc.get("database");
                String docid = doc.get( "docid" );
                if ( map.containsKey(docid) )
                {
                    ArrayList<String> vidList = map.get(docid);
                    String[] vids = null;
                    if ( vidList != null )
                    {
                        vids = new String[vidList.size()];
                        vidList.toArray(vids);
                    }
                    map.remove(docid);
                    mvdHits[j++] = new MVDHit( docid, vids, database );
                }
            }
            return mvdHits;
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
}