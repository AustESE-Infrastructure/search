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
import edu.luc.nmerge.mvd.Location;
import edu.luc.nmerge.mvd.Base64;
import calliope.core.constants.Formats;
import calliope.core.database.Repository;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
/**
 * An index for searching MVDs etc.
 * @author desmond
 */
public class Index implements Serializable {
    private static final long serialVersionUID = 7526472295622776147L;
    /** list of docids: index = document id */
    ArrayList<String> docs;
    HashMap<String,ArrayList<Location>> map;
    ArrayList<String> documents;
    transient StringBuilder log;
    String projid;
    /**
     * Given a project docid find all resources to be indexed
     * @param projid 
     */
    public Index( String projid ) throws SearchException
    {
        this.projid = projid;
        documents = new ArrayList<String>();
        this.log = new StringBuilder();
        // 1. find all cortexs that have that projid as prefix
        try
        {
            Connection conn = Connector.getConnection();
            String[] docids = conn.listDocuments(Database.CORTEX,projid+"/.*",
                JSONKeys.DOCID);
            map = new HashMap<String,ArrayList<Location>>();
            String lang = Utils.languageFromProjid(projid);
            HashSet sw = Utils.getStopwords(lang);
            for ( int i=0;i<docids.length;i++ )
            {
                String bson = conn.getFromDb(Database.CORTEX,docids[i] );
                JSONObject jObj = (JSONObject)JSONValue.parse( bson );
                String format = (String)jObj.get(JSONKeys.FORMAT);
                if ( format != null && format.equals(Formats.MVD_TEXT) )
                {
                    String mvdText = (String)jObj.get(JSONKeys.BODY);
                    MVD mvd = MVDFile.internalise( mvdText );
                    int nWords = mvd.indexWords( map, sw, i );
                    documents.add( docids[i]);
                    log.append("Indexed ");
                    log.append( nWords );
                    log.append(" words from ");
                    log.append( docids[i] );
                    log.append("\n");
                }
                // NB also handle plain text formats
                else
                    System.out.println("Warning: ignored format "+format);
            }
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * Find all the locations in documents where the search term occurs
     * @param query the query to search for
     * @return an array of matches
     */
    public Match[] find( Query query ) 
    {
        Match[] res;
        ArrayList<Match> hits = new ArrayList<Match>();
        ArrayList locs = map.get(query.terms[0]);
        for ( int i=0;i<locs.size();i++ )
            hits.add( new Match((Location)locs.get(i),MatchType.fromQuery(query)) );
        for ( int i=1;i<query.terms.length;i++ )
        {
            locs = map.get(query.terms[i]);
            ArrayList<Match> newHits = new ArrayList();
            for ( int j=0;j<hits.size();j++ )
            {
                Match hit = (Match)hits.get(j);
                for ( int k=0;k<locs.size();k++ )
                {
                    Location l = (Location)locs.get(k);
                    boolean useful = false;
                    if ( query instanceof LiteralQuery )
                        useful = hit.testLiteral(l);
                    else if ( query instanceof BooleanQuery )
                        useful = hit.testBoolean(l);
                    if ( useful )
                    {
                        hit.addTerm(l,query.terms[i]);
                        newHits.add(hit);
                    }
                }
            }
            // reduce
            hits = newHits;
            if ( hits.size()==0 )
                break;
        }
        res = new Match[hits.size()];
        hits.toArray(res);
        return res;
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
            JSONObject jObj = (JSONObject)JSONValue.parse(bson);
            byte[] data = Base64.decode( (String)jObj.get(JSONKeys.BODY) );
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInputStream in = new ObjectInputStream( bis );
            Index ind = (Index)in.readObject();
            in.close();
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
            ind2.find( new LiteralQuery("vertical ravines","en") );
        }
        catch ( Exception e )
        {
            e.fillInStackTrace();
            e.printStackTrace();
        }
    }
}
