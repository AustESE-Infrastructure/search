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
import org.json.simple.*;
import calliope.core.database.*;
import search.exception.*;
import calliope.core.constants.JSONKeys;
import edu.luc.nmerge.mvd.MVDFile;
import edu.luc.nmerge.mvd.MVD;
import edu.luc.nmerge.mvd.Pair;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
/**
 * Format hits for consumption
 * @author desmond
 */
public class Formatter 
{
    Index index;
    String projid;
    HashMap<String,MVD> cache;
    static final int BACK_CONTEXT_NWORDS = 3;
    static final int CONTEXT_NWORDS = 100;
    public Formatter( Index ind )
    {
        this.index = ind;
        this.cache = new HashMap<String,MVD>();
    }
    /**
     * Convert a set of matches to an array of JSON formatted summaries
     * @param matches the array of raw matches
     * @return a JSON document containing all formatted matches
     */
    public String matchesToHits( Match[] matches ) throws FormatException
    {
        try
        {
            JSONArray hits = new JSONArray();
            for ( int i=0;i<matches.length;i++ )
            {
                if ( verifyMatch(matches[i]) )
                {
                    JSONObject json = matchToHit(matches[i]);
                    hits.add( json );
                }
            }
            return hits.toJSONString();
        }
        catch ( Exception e )
        {
            throw new FormatException(e);
        }
    }
    /**
     * Move forward a certain number of words
     * @param data the character data from one version
     * @param pos the start-position of the first match word
     * @param nWords the number of words to move forward
     * @return 
     */
    int moveForward( char[] data, int pos, int nWords )
    {
        int state = 0;
        int count = 0;
        for ( int i=pos;i<data.length;i++ )
        {
            switch ( state )
            {
                case 0: // recognising text
                    if ( Character.isWhitespace(data[i]) )
                        state = 1;
                    break;
                case 1: // recognising whitespace
                    if ( !Character.isWhitespace(data[i]) )
                    {
                        if ( count < nWords )
                        {
                            count++;
                            state = 0;
                        }
                        else
                        {
                            state = 2;
                            pos = i;
                        }
                    }
                    break;
            }
            if ( state == 2 )
                break;
        }
        return pos;
    }
    /**
     * Move back a certain number of words
     * @param data the character data from one version
     * @param pos the start-position of the first match word
     * @param nWords the number of words to move back
     * @return 
     */
    int moveBack( char[] data, int pos, int nWords )
    {
        int state = 0;
        int count = 0;
        while ( pos<data.length && !Character.isWhitespace(data[pos]) )
            pos++;
        for ( int i=pos;i>=0;i-- )
        {
            switch ( state )
            {
                case 0: // recognising text
                    if ( Character.isWhitespace(data[i]) )
                        state = 1;
                    break;
                case 1: // recognising whitespace
                    if ( !Character.isWhitespace(data[i]) )
                    {
                        if ( count < nWords )
                        {
                            count++;
                            state = 0;
                        }
                        else
                        {
                            state = 2;
                            pos = i;
                        }
                    }
                    break;
            }
            if ( state == 2 )
                break;
        }
        return pos;
    }
    /**
     * Get the document title
     * @param docid its docid
     * @return its title or the original docid
     * @throws SearchException 
     */
    String getTitle( String docid ) throws SearchException
    {
        try
        {
            String title = null;
            Connection conn = Connector.getConnection();
            String md = conn.getFromDb(Database.METADATA,docid);
            if ( md != null )
            {
                JSONObject mdObj = (JSONObject)JSONValue.parse(md);
                if ( mdObj.containsKey(JSONKeys.TITLE) )
                    title = (String)mdObj.get(JSONKeys.TITLE);
            }
            if ( title == null )
            {
                String cortex = conn.getFromDb(Database.CORTEX, docid );
                JSONObject cortexObj = (JSONObject)JSONValue.parse(cortex);
                if ( cortexObj.containsKey(JSONKeys.TITLE) )
                    title = (String)cortexObj.get(JSONKeys.TITLE);
            }
            if ( title == null )
                title = docid;
            return title;
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * Remove hyphens at line-end if preceded by a letter
     * @param sb the string containing the hyphenated text
     * @return unhyphenated text
     */
    String dehyphenate( StringBuilder sb )
    {
        StringBuilder sb2 = new StringBuilder();
        int state = 0;
        for ( int i=0;i<sb.length();i++ )
        {
            char token = sb.charAt(i);
            switch ( state )
            {
                case 0: // looking for letter
                    if ( Character.isLetter(token) )
                        state = 1;
                    sb2.append(token);
                    break;
                case 1:     // seen letter
                    if ( token=='-' )
                        state = 2;
                    else if ( !Character.isLetter(token) )
                    {
                        state = 0;
                        sb2.append(token);
                    }
                    else
                        sb2.append(token);
                    break;
                case 2: // seen letter then hyphen
                    if ( Character.isWhitespace(token) )
                        state = 3;
                    else 
                    {
                        if ( Character.isLetter(token) )
                            state = 1;
                        else
                            state = 0;
                        sb2.append('-');
                        sb2.append(token);
                    }
                    break;
                case 3: // seen letter,hyphen,space
                    if ( Character.isLetter(token) )
                    {
                        sb2.append(token);
                        state = 1;
                    }
                    else if ( !Character.isWhitespace(token) )
                    {
                        sb2.append(token);
                        state = 0;
                    }
                    break;
            }
        }
        return sb2.toString();
    }
    JSONObject matchToHit( Match match ) throws SearchException
    {
        String docid = index.getDocid( match.docId );
        MVD mvd = loadMVD(docid);
        BitSet bs = getMatchVersions( match, mvd );
        int firstVersion = bs.nextSetBit(0);
        char[] data = mvd.getVersion(firstVersion);
        int[] vPositions = getMatchPositions( match, mvd, firstVersion );
        int start = moveBack( data, vPositions[0], BACK_CONTEXT_NWORDS );
        int end = moveForward( data, start, CONTEXT_NWORDS );
        String context = new String(data,start,end-start);
        StringBuilder sb = new StringBuilder();
        sb.append("<p class=\"hit\">");
        int len = (end-start)+1;
        int prev = 0;
        for ( int i=0;i<vPositions.length;i++ )
        {
            int actual = vPositions[i]-start;
            if ( actual < len )
            {
                sb.append( context.substring(prev,actual) );
                prev = actual+match.terms[i].length();
                sb.append("<span class=\"match\">");
                sb.append(context.substring(actual,prev));
                sb.append("</span>");
            }
        }
        sb.append("</p>");
        String hitText = dehyphenate( sb );
        JSONObject jObj = new JSONObject();
        jObj.put(JSONKeys.BODY,hitText);
        jObj.put(JSONKeys.TITLE,getTitle(docid));
        return jObj;
    }
    private MVD loadMVD( String docid ) throws SearchException
    {
        try
        {
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
    BitSet getMatchVersions( Match match, MVD mvd )
    {
        int pairIndex = 0;
        int start = 0;
        ArrayList<Pair> pairs = mvd.getPairs();
        BitSet bs = new BitSet();
        Pair p = pairs.get(0);
        for ( int i=0;i<match.positions.length;i++ )
        {
            int pos = match.positions[i];
            while ( start < pos )
            {
                int len = p.length();
                if ( len+start > pos )
                    break;
                else
                {
                    p = pairs.get(pairIndex++);
                    start += len;
                }
            }
            if ( i==0 )
                bs.or( p.versions );
            else
                bs.and( p.versions );
        }
        return bs;
    }
    /**
     * Get the positions of the terms in the first version they share
     * @param match the match consosting of several terms
     * @param mvd the mvd they are found in
     * @param version the version to follow
     * @return an array of character positions in that specific version
     */
    int[] getMatchPositions( Match match, MVD mvd, int version )
    {
        Pair p;
        int j = 0;
        int pos = 0;
        int vPos = 0;
        int[] vPositions = new int[match.positions.length];
        ArrayList<Pair> pairs = mvd.getPairs();
        for ( int i=0;i<pairs.size();i++ )
        {
            p = pairs.get(i);
            if ( pos+p.length() > match.positions[j] )
                vPositions[j++] = vPos+(match.positions[j]-pos);
            if ( p.versions.nextSetBit(version)==version )
                vPos += p.length();
            pos += p.length();
        }
        return vPositions;
    }
    /**
     * Check that a match's terms are all in at least 1 version
     * @param match the match to check
     * @return true if it was OK else false
     * @throws SearchException 
     */
    private boolean verifyMatch( Match match ) throws SearchException
    {
        String docid = index.getDocid( match.docId );
        MVD mvd = loadMVD(docid);
        Pair p;
        ArrayList<Pair> pairs = mvd.getPairs();
        if ( pairs.size()> 0 )
        {
            BitSet bs = getMatchVersions( match, mvd );
            // check match constraints if any
            if ( !bs.isEmpty() )
            {
                if ( match.type == MatchType.BOOLEAN )
                    return true;
                else if ( match.type == MatchType.LITERAL )
                {
                    // terms must be close together
                    int firstVersion = bs.nextSetBit(0);
                    int[] vPositions = getMatchPositions( match, mvd, 
                        firstVersion );
                    for ( int i=1;i<vPositions.length;i++ )
                    {
                        int dist = vPositions[i]-vPositions[i-1]
                            +match.terms[i-1].length();
                        if ( dist > LiteralQuery.MAX_DISTANCE )
                            return false;
                    }
                    return true;
                }
                else    // shouldn't happen
                    return false;
            }
        }
        return false;
    }
    public static void main(String[] args )
    {
        String test = 
        "   On the other side, what seems to be an isolated\n"+
        "patch of blue mist floats lightly on the glare of the\n"+
        "horizon. This is the peninsula of Azuera, a wild chaos\n"+
        "of sharp rocks and stony levels cut about by vertical\n"+
        "ravines. It lies far out to sea like a rough head of\n"+
        "stone stretched from a green-clad coast at the end of\n"+
        "a slender neck of sand covered with thickets of thorny\n"+
        "scrub. Utterly waterless, for the rainfall runs off at\n"+
        "once on all sides into the sea, it has not soil enough,\n"+
        "it is said, to grow a single blade of grass--as if it were\n"+
        "blighted by a curse. The poor, associating by an ob-\n"+
        "scure instinct of consolation the ideas of evil and\n"+
        "wealth, will tell you that it is deadly because of its\n"+
        "forbidden treasures. The common folk of the neigh-\n"+
        "borhood, peons of the estancias, vaqueros of the sea-\n"+
        "board plains, tame Indians coming miles to market\n"+
        "with a bundle of sugar-cane or a basket of maize worth\n"+
        "about threepence, are well aware that heaps of shin-\n"+
        "ing gold lie in the gloom of the deep precipices cleav-\n"+
        "ing the stony levels of Azuera. Tradition has it that\n"+
        "many adventurers of olden time had perished in the\n"+
        "search. The story goes also that within men's memory\n"+
        "two wandering sailors--Americanos, perhaps, but\n"+
        "gringos of some sort for certain--talked over a gam-\n"+
        "bling, good-for-nothing mozo, and the three stole a\n";
        Formatter f = new Formatter( null );
        System.out.println(f.dehyphenate(new StringBuilder(test)));
    }
}
