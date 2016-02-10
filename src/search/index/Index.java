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
package search.index;
import search.format.Formatter;
import calliope.core.constants.Database;
import search.exception.SearchException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import calliope.core.database.*;
import calliope.core.constants.JSONKeys;
import org.json.simple.*;
import edu.luc.nmerge.mvd.MVDFile;
import edu.luc.nmerge.mvd.MVD;
import edu.luc.nmerge.mvd.Base64;
import calliope.core.constants.Formats;
import calliope.core.database.Repository;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.BitSet;
import java.util.Map;
import java.util.Collection;
/**
 * An index for searching MVDs etc.
 * @author desmond
 */
public class Index implements Serializable {
    private static final long serialVersionUID = 7526472295622776147L;
    /** list of docids: index = document id */
    HashMap<String,Locations> map;
    ArrayList<String> documents;
    transient StringBuilder log;
    String projid;
    String lang;
    HashSet sw;
    /**
     * Given a project docid find all resources to be indexed
     * @param projid 
     */
    public Index( String projid ) throws SearchException
    {
        this.projid = projid;
        this.documents = new ArrayList<String>();
        this.log = new StringBuilder();
        // 1. find all cortexs that have that projid as prefix
        try
        {
            map = new HashMap<String,Locations>();
            lang = Utils.languageFromProjid(projid);
            sw = Utils.getStopwords(lang);
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * Build the index one file at a time
     * @param pg the progress object to record progress
     * @throws SearchException 
     */
    public void build( Progress pg ) throws SearchException
    {
        try
        {
            Connection conn = Connector.getConnection();
            String[] docids = conn.listDocuments(Database.CORTEX,projid+"/.*",
                JSONKeys.DOCID);
            int totalWords = 0;
            pg.setTotal(docids.length);
            for ( int i=0;i<docids.length;i++ )
            {
                String bson = conn.getFromDb(Database.CORTEX,docids[i] );
                JSONObject jObj = (JSONObject)JSONValue.parse( bson );
                String format = (String)jObj.get(JSONKeys.FORMAT);
                if ( format != null )
                {
                    int nWords = 0;
                    String text = (String)jObj.get(JSONKeys.BODY);
                    if ( format.equals(Formats.MVD_TEXT) )
                    {
                        MVD mvd = MVDFile.internalise( text );
                        nWords = indexMVDWords( mvd, map, sw, lang, 
                            documents.size() );
                    }
                    else if ( format.equals(Formats.TEXT) )
                    {
                        TextWordFinder twf = new TextWordFinder( text, map, 
                            sw, lang, documents.size() );
                        nWords = twf.find();
                    }
                    if ( nWords > 0 )
                    {
                        documents.add( docids[i] );
                        
//                        log.append("Indexed ");
//                        log.append( nWords );
//                        log.append(" words from ");
//                        log.append( docids[i] );
//                        log.append("\n");
                        totalWords += nWords;
                    }
                }
                // NB also handle plain text formats
                else
                    log.append("Warning: ignored format "+format+" in "
                        +docids[i]+"\n");
                pg.update( 1 );
            }
            log.append("Indexed "+totalWords+" total words in "+documents.size()
                +" documents\n");
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * Find the locations in documents where all the search terms occur
     * @param query the query to search for
     * @return an array of matches or null
     */
    public Match[] find( Query query ) 
    {
        Match[] res=new Match[0];
        Locations locs = map.get(query.terms[0].toLowerCase());
        if ( locs != null )
        {
            // merge into one hit per document
            HashMap<Integer,Match> hitMap = new HashMap<Integer,Match>();
            // all hits must contain first term
            for ( int i=0;i<locs.size();i++ )
            {
                Location loc = locs.location(i);
                if ( hitMap.containsKey(loc.docId) )
                {
                    Match hit = hitMap.get(loc.docId);
                    hit.addTerm(loc,query.terms[0]);
                }
                else
                    hitMap.put(loc.docId, new Match(loc,query.terms[0],
                        MatchType.fromQuery(query)) );
            }
            // add other terms if they occur in the same documents
            for ( int i=1;i<query.terms.length;i++ )
            {
                locs = map.get(query.terms[i].toLowerCase());
                for ( int k=0;k<locs.size();k++ )
                {
                    Location l = (Location)locs.location(k);
                    // is this term in an already found document?
                    Match hit = hitMap.get(l.docId);
                    if ( hit != null )
                        hit.addTerm(l,query.terms[i]);
                }
            }
            Collection<Match> coll = hitMap.values();
            res = new Match[coll.size()];
            coll.toArray(res);
        }
        return res;
    }
    /**
     * Index all the words of an MVD
     * @param mvd the mvd to index
     * @param map the map where to store the words and their locations
     * @param sw a list of stopwords
     * @param lang a ISO 2-letter language code
     * @param docId the document identifier starting at 0
     * @return the number of indexed words
     */
    int indexMVDWords( MVD mvd, Map<String,Locations> map, 
        HashSet<String> sw, String lang, int docId )
    {
        try
        {
            int mvdVersions = mvd.numVersions();
            if ( mvdVersions>0 )
            {
                WordFinder wf = new WordFinder( mvd.getPairs(), map, sw, 
                    lang, projid, docId );
                BitSet bs = new BitSet();
				for ( int i=1;i<=mvdVersions;i++ )
                    bs.set( i );
                return wf.find(bs);
            }
        }
        catch ( Exception e )
        {
            System.out.println(e.getMessage());
        }
        return 0;
    }
    /**
     * Save the index in the database
     * @throws SearchException 
     */
    public void save() throws SearchException
    {
        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream( bos );
            out.writeObject( this );
            out.close();
            byte[] data = bos.toByteArray();
            String b64Data = Base64.encodeBytes( data );
            JSONObject jObj = new JSONObject();
            jObj.put( JSONKeys.BODY, b64Data );
            Connection conn = Connector.getConnection();
            conn.putToDb( Database.INDICES, this.projid, jObj.toJSONString() );
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * Load the index into memory for searching
     */
    public static Index load( String projid ) throws SearchException
    {
        try
        {
            Connection conn = Connector.getConnection();
            String bson = conn.getFromDb( Database.INDICES, projid );
            //System.out.println("about to parse index");
            JSONObject jObj = (JSONObject)JSONValue.parse(bson);
            //System.out.println("About to decode base 64");
            byte[] data = Base64.decode( (String)jObj.get(JSONKeys.BODY) );
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInputStream in = new ObjectInputStream( bis );
            // System.out.println("About to read in index as object");
            Index ind = (Index)in.readObject();
            in.close();
            //System.out.println("About to close connection");
            return ind;
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    public String getLog()
    {
        return this.log.toString();
    }
    /**
     * Get the database string docid from its numeric index
     * @param docId the index into the documents array
     */
    public String getDocid( int docId )
    {
        return this.documents.get( docId );
    }
    public static void main(String[] args )
    {
        try
        {
            Connector.init( Repository.MONGO, "admin", 
            "jabberw0cky", "localhost", "calliope", 27017, 8080, "/var/www" );
            Index ind = new Index( "english/conrad/nostromo" );
            System.out.println(ind.getLog());
            ind.save();
            Index ind2 = Index.load("english/conrad/nostromo");
            Match[] res1 = ind2.find( new LiteralQuery("vertical ravines","en") );
            Match[] res2 = ind2.find( new BooleanQuery("estancias sugar-cane","en") );
            Formatter f = new Formatter(ind);
            if ( res1 != null && res1.length>0 )
            {
                String json = f.matchesToHits( res1 );
                System.out.println( json );
            }
            else
                System.out.println("vertical ravines not found");
            if ( res2 != null && res2.length>0 )
            {
                String json = f.matchesToHits( res2 );
                System.out.println( json );
            }
            else
                System.out.println("estancias suger-cane query not found");
        }
        catch ( Exception e )
        {
            e.fillInStackTrace();
            e.printStackTrace();
        }
    }
}
