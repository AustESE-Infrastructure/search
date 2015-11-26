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
package search.format;
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
import java.util.Arrays;
import search.index.Index;
import search.index.LiteralQuery;
import search.index.Match;
import search.index.MatchType;
import search.cache.MVDCache;
/**
 * Format hits for consumption
 * @author desmond
 */
public class Formatter 
{
    Index index;
    String projid;
    MVDCache cache;
    /** number of words of context per term */
    static final int MAX_DISPLAY_TERMS = 5;
    public Formatter( Index ind )
    {
        this.index = ind;
        this.cache = new MVDCache();
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
                    boolean suppress = isSuppressed( matches, i );
                    JSONObject json = matchToHit(matches[i],suppress);
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
     * Do we suppress this match because it is the same as another?
     * @param index the index of the match to test
     * @param matches the other matches
     * @return true if it is in effect identical to another match
     */
    boolean isSuppressed( Match[] matches, int index )
    {
        for ( int i=0;i<matches.length;i++ )
        {
            // otherwise they will suppress eeach other
            if ( index != i && index > i )  
            {
                if ( matches[i].equals(matches[index]) )
                    return true;
            }
        }
        return false;
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
                if ( mdObj.containsKey(JSONKeys.SECTION) )
                    title += " "+mdObj.get(JSONKeys.SECTION);
                if ( mdObj.containsKey(JSONKeys.SUBSECTION) )
                    title += ", "+mdObj.get(JSONKeys.SUBSECTION);
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
    /**
     * Convert an int array to an array list
     * @param arr the int array
     * @return the array list
     */
    ArrayList toArrayList( int[] arr )
    {
        ArrayList list = new ArrayList<Integer>();
        for ( int i=0;i<arr.length;i++ )
            list.add( arr[i]);
        return list;
    }
    /**
     * Convert an abstract hit into a HTML string for display
     * @param match the match to convert
     * @param suppress: format it to be invisible
     * @return a single hit formatted in basic HTML
     * @throws SearchException 
     */
    JSONObject matchToHit( Match match, boolean suppress ) throws SearchException
    {
        String docid = index.getDocid( match.docId );
        MVD mvd = MVDCache.load(docid);
        BitSet bs = getMatchVersions( match.positions, mvd );
        int firstVersion = bs.nextSetBit(0);
        char[] data = mvd.getVersion(firstVersion);
        int[] vPositions = getVPositions( match.positions, mvd, firstVersion );
        StringBuilder sb = new StringBuilder();
        if ( suppress )
            sb.append("<p class=\"suppress-hit\">... ");
        else
            sb.append("<p class=\"hit\">... ");
        HitSpan hs = null;
        for ( int i=0;i<MAX_DISPLAY_TERMS&&i<vPositions.length;i++ )
        {
            if ( hs == null )
                hs = new HitSpan( data, match.terms[i], vPositions[i] );
            else if ( !hs.wants(match.terms[i],vPositions[i]) )
            {
                sb.append( hs.toString() );
                hs = new HitSpan( data, match.terms[i], vPositions[i] );
            }
            else
                hs.add( match.terms[i], vPositions[i] );
        }
        if ( hs != null )
            sb.append( hs.toString() );
        sb.append("</p>");
        String hitText = dehyphenate( sb );
        JSONObject jObj = new JSONObject();
        jObj.put(JSONKeys.BODY,hitText);
        jObj.put(JSONKeys.DOCID,docid);
        jObj.put(JSONKeys.POSITIONS,toArrayList(match.positions));
        jObj.put(JSONKeys.VERSION1,mvd.getVersionId((short)firstVersion));
        jObj.put(JSONKeys.TITLE,getTitle(docid));
        return jObj;
    }
    /**
     * Get the versions shared by the match from the MVD raw offsets
     * @param positions the positions of the match
     * @param mvd the mvd to search in
     * @return the set of versions shared by the match terms
     */
    public static BitSet getMatchVersions( int[] positions, MVD mvd )
    {
        int pairIndex = 0;
        int start = 0;
        ArrayList<Pair> pairs = mvd.getPairs();
        BitSet bs = new BitSet();
        int[] sorted = new int[positions.length];
        System.arraycopy(positions, 0, sorted, 0, positions.length );
        Arrays.sort( sorted );
        Pair p=null;
        for ( int i=0;i<sorted.length;i++ )
        {
            int pos = sorted[i];
            while ( start < pos )
            {
                p = pairs.get(pairIndex++);
                int len = p.length();
                if ( len+start > pos )
                    break;
                else if ( len+start==pos )
                {
                    p = pairs.get(pairIndex);
                    break;
                }
                else
                    start += len;
            }
            if ( p != null )
            {
                if ( i==0 )
                    bs.or( p.versions );
                else
                    bs.and( p.versions );
            }
        }
        return bs;
    }
    /**
     * Get the positions of the terms in the first version they share
     * @param mvdPositions an array of global MVD positions
     * @param mvd the mvd they are found in
     * @param version the version to follow
     * @return an array of character positions in that specific version
     */
    public static int[] getVPositions( int[] mvdPositions, MVD mvd, int version )
    {
        Pair p;
        int pos = 0;
        int vPos = 0;
        int[] vPositions = new int[mvdPositions.length];
        int least = Integer.MAX_VALUE;
        for ( int i=0;i<vPositions.length;i++ )
        {
            if ( mvdPositions[i]<least )
                least = mvdPositions[i];
            vPositions[i] = -1;
        }
        ArrayList<Pair> pairs = mvd.getPairs();
        for ( int i=0;i<pairs.size();i++ )
        {
            p = pairs.get(i);
            if ( pos+p.length() >= least )
            {
                boolean all = true;
                for ( int j=0;j<vPositions.length;j++ )
                {
                    if ( vPositions[j] == -1 && pos+p.length() > mvdPositions[j] )
                        vPositions[j] = vPos+(mvdPositions[j]-pos);
                    if ( vPositions[j] == -1 )
                        all = false;
                }
                if ( all )
                    break;
            }
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
        MVD mvd = MVDCache.load(docid);
        Pair p;
        ArrayList<Pair> pairs = mvd.getPairs();
        if ( pairs.size()> 0 )
        {
            BitSet bs = getMatchVersions( match.positions, mvd );
            // check match constraints if any
            if ( !bs.isEmpty() )
            {
                if ( match.type == MatchType.BOOLEAN )
                    return true;
                else if ( match.type == MatchType.LITERAL )
                {
                    // terms must be close together
                    int firstVersion = bs.nextSetBit(0);
                    int[] vPositions = getVPositions( match.positions, mvd, 
                        firstVersion );
                    for ( int i=1;i<vPositions.length;i++ )
                    {
                        int dist = vPositions[i]-(vPositions[i-1]
                            +match.terms[i-1].length());
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
