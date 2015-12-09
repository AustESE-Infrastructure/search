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
import edu.luc.nmerge.mvd.MVD;
import edu.luc.nmerge.mvd.Pair;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Set;
import java.util.Iterator;
import search.index.Index;
import search.index.Match;
import search.cache.MVDCache;
/**
 * Format hits into HTML for consumption
 * @author desmond
 */
public class Formatter 
{
    Index index;
    String projid;
    MVDCache cache;
    static int MAX_BODY_LENGTH = 512;
    /** number of words of context per term */
    static final int MAX_DISPLAY_TERMS = 5;
    public Formatter( Index ind )
    {
        this.index = ind;
        this.cache = new MVDCache();
    }
    /**
     * Merge hits from different documents.
     * @param hits the original per hit list of hits
     * @return an array based on one set of hits per document
     */
    JSONArray mergeHits( JSONArray hits )
    {
        HashMap<String,JSONObject> table = new HashMap<String,JSONObject>();
        for ( int i=0;i<hits.size();i++ )
        {
            JSONObject hit = (JSONObject)hits.get(i);
            String docid = (String)hit.get(JSONKeys.DOCID);
            if ( !table.containsKey(docid) )
                table.put( docid, hit );
            else
            {
                JSONObject old = table.get( docid );
                String oldBody = (String) old.get(JSONKeys.BODY);
                if ( oldBody.length() < MAX_BODY_LENGTH )
                {
                    String newBody = (String)hit.get(JSONKeys.BODY);
                    if ( newBody.startsWith("<p class=\"hit\">") )
                        newBody = newBody.substring(17);
                    if ( oldBody.endsWith("</p>") )
                        oldBody = oldBody.substring(0,oldBody.length()-4);
                    oldBody += newBody;
                    old.put(JSONKeys.BODY,oldBody);
                }
                JSONArray positions = (JSONArray) old.get(JSONKeys.POSITIONS);
                JSONArray newPositions = (JSONArray) hit.get(JSONKeys.POSITIONS);
                for ( int j=0;j<newPositions.size();j++ )
                    positions.add(newPositions.get(j));
            }
        }
        Set<String> keys = table.keySet();
        Iterator<String> iter = keys.iterator();
        JSONArray newDoc = new JSONArray();
        while ( iter.hasNext() )
        {
            newDoc.add( table.get(iter.next()) );
        }
        return newDoc;
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
            hits = mergeHits( hits );
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
    JSONArray toArrayList( int[] arr )
    {
        JSONArray list = new JSONArray();
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
        BitSet bs = getMatchVersions( match.getPositions(), mvd );
        int firstVersion = match.getFirstVersion();
        if ( firstVersion==0 )
            firstVersion = bs.nextSetBit(0);
        char[] data = mvd.getVersion(firstVersion);
        StringBuilder sb = new StringBuilder();
        if ( suppress )
            sb.append("<p class=\"suppress-hit\">... ");
        else
            sb.append("<p class=\"hit\">... ");
        HitSpan hs = null;
        for ( int i=0;i<MAX_DISPLAY_TERMS&&i<match.numTerms();i++ )
        {
            int[] vPositions = getVPositions( match.getTermPositions(i), 
                mvd, firstVersion );
            if ( hs == null )
                hs = new HitSpan( data, match.getTerm(i), vPositions[0] );
            else if ( !hs.wants(match.getTerm(i),vPositions[0]) )
            {
                sb.append( hs.toString() );
                hs = new HitSpan( data, match.getTerm(i), vPositions[0] );
            }
            else
                hs.add( match.getTerm(i), vPositions[0] );
        }
        if ( hs != null )
            sb.append( hs.toString() );
        sb.append("</p>");
        String hitText = dehyphenate( sb );
        JSONObject jObj = new JSONObject();
        jObj.put(JSONKeys.BODY,hitText);
        jObj.put(JSONKeys.DOCID,docid);
        jObj.put(JSONKeys.POSITIONS,toArrayList(match.getPositions()));
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
        Position[] matchPositions = new Position[positions.length];
        for ( int i=0;i<sorted.length;i++ )
        {
            int pos = sorted[i];
            while ( start < pos )
            {
                Pair p = pairs.get(pairIndex);
                int len = p.length();
                if ( len+start > pos )
                    break;
                else
                    start += len;
                pairIndex++;
            }
            matchPositions[i] = new Position(pairs,pairIndex,pos-start,pos);
            if ( i==0 )
                bs.or( matchPositions[i].getVersions() );
            else 
            {
                if ( matchPositions[i].overlaps(matchPositions[i-1]) )
                    matchPositions[i].getVersions().or(matchPositions[i-1].getVersions());
                bs.and( matchPositions[i].getVersions() );
            }
        }
        return bs;
    }
    /**
     * Remove all - values from the array of version positions
     * @param oldVPositions an array of vPositions and unset (-1) values
     * @return a new array possibly smaller with no unset elements
     */
    private static int[] compactVPositions( int[] oldVPositions )
    {
        int count = 0;
        for ( int i=0;i<oldVPositions.length;i++ )
            if ( oldVPositions[i]!= -1 )
                count++;
        int[] newVPositions = new int[count];
        for ( int j=0,i=0;i<oldVPositions.length;i++ )
            if ( oldVPositions[i] != -1 )
                newVPositions[j++] = oldVPositions[i];
        return newVPositions;
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
        int j = 0;
        int[] vPositions = new int[mvdPositions.length];
        Arrays.sort(mvdPositions);
        for ( int i=0;i<vPositions.length;i++ )
            vPositions[i] = -1;
        ArrayList<Pair> pairs = mvd.getPairs();
        for ( int i=0;i<pairs.size();i++ )
        {
            p = pairs.get(i);
            while (j<mvdPositions.length && pos+p.length() > mvdPositions[j] )
            {
                if ( p.versions.nextSetBit(version)==version )
                    vPositions[j] = vPos+(mvdPositions[j]-pos);
                else
                    System.out.println("Skipping position "+mvdPositions[j]);
                j++;
            }
            if ( j == mvdPositions.length )
                break;
            if ( p.versions.nextSetBit(version)==version )
                vPos += p.length();
            pos += p.length();
        }
        vPositions = compactVPositions(vPositions);
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
        BitSet bs = getMatchVersions( match.getPositions(), mvd );
        return !bs.isEmpty();
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
