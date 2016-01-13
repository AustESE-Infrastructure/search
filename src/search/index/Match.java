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
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;
import java.util.BitSet;
import edu.luc.nmerge.mvd.MVD;
/**
 * a Hit being a full match in some document
 * @author desmond
 */
public class Match 
{
    /** document id in index */
    public int docId;  
    /** terms to positions map */
    HashMap<String,ArrayList<Integer>> map;
    /** the score or edit distance of the matched words */
    float score;
    /** number of characters allowed between words for literal matches */
    static final int literalSlop = 10;
    /** the type of the match derived from query */
    public MatchType type;
    /** the version to follow or 0 to ignore this */
    int firstVersion;
    ArrayList<String> terms;
    /**
     * Create an initial match from a single location
     * @param loc the location
     * @param word the first term
     * @param type the type of the match
     */
    public Match( Location loc, String word, MatchType type )
    {
        this.map = new HashMap<String,ArrayList<Integer>>();
        ArrayList<Integer> list = new ArrayList<Integer>();
        terms = new ArrayList<String>();
        terms.add(word);
        list.add( loc.pos );
        map.put( word, list );
        this.score = 100.0f;
        this.type = type;
        this.docId = loc.docId;
    }
    /**
     * Check if all terms are in the correct order
     * @return true if it MAY be literal
     */
    public boolean canBeLiteral()
    {
        boolean globalOK = true;
        for ( int i=0;i<terms.size()-1;i++ )
        {
            String term1 = terms.get(i);
            String term2 = terms.get(i+1);
            ArrayList<Integer> list1 = map.get(term1);
            ArrayList<Integer> list2 = map.get(term2);
            boolean termOK = false;
            for ( int j=0;j<list1.size();j++ )
            {
                int pos1 = list1.get(j);
                for ( int k=0;k<list2.size();k++ )
                {
                    int pos2 = list2.get(k);
                    if ( pos2 > pos1 )
                    {
                        termOK = true;
                        break;
                    }
                    if ( termOK )
                        break;
                }
            }
            if ( !termOK )
            {
                globalOK = false;
                break;
            }
        }
        return globalOK;
    }
    /**
     * Set the first version to a specific value, not the default
     * @param firstVersion the version to display the hit in
     */
    public void setFirstVersion( int firstVersion )
    {
        this.firstVersion = firstVersion;
    }
    /**
     * Get the first version to a specific value, not the default
     * @return the version to display the hit in or 0
     */
    public int getFirstVersion( )
    {
        return this.firstVersion;
    }
    /**
     * Check if we are equal to another match - pretty unlikely
     */
    public boolean equals( Object other )
    {
        if ( other instanceof Match )
        {
            Match oMatch = (Match) other;
            
            if ( oMatch.terms.size() == this.terms.size() )
            {
                for ( int i=0;i<terms.size();i++ )
                {
                    String term = terms.get(i);
                    String oTerm = oMatch.terms.get(i);
                    if ( !term.equals(oTerm) )
                        return false;
                    ArrayList<Integer> list1 = map.get(term);
                    ArrayList<Integer> list2 = oMatch.map.get(term);
                    if ( list1.size()!=list2.size() )
                        return false;
                    for ( int j=0;j<list1.size();j++ )
                    {
                        if ( !list1.get(j).equals(list2.get(j)) )
                            return false;
                    }
                }
                // all terms overlap and are equal
                return true;
            }
        }
        return false;
    }
    /**
     * Recalculate the score 
     */
    private void recalcScore()
    {
        int[] distances = new int[terms.size()-1];
        for ( int i=0;i<terms.size()-1;i++ )
        {
            int dist = Integer.MAX_VALUE;
            // find the closest term
            for ( int j=0;j<terms.size();j++ )
            {
                if ( i != j )
                {
                    ArrayList<Integer> listi = map.get(terms.get(i));
                    ArrayList<Integer> listj = map.get(terms.get(j));
                    // this costs O(N log N) for each term
                    for ( int k=0;k<listi.size();k++ )
                    {
                        int val1 = listi.get(k);
                        int index = getIndex(listj,val1);
                        int val2;
                        if ( index!=-1 )
                            val2 = listj.get(index);
                        else
                        {
                            index = 0;
                            val2 = listj.get(0);
                        }
                        int diff = Math.abs(val1-val2);
                        if ( index != 0 && index != listj.size()-1 )
                        {
                            int index2 = index+1;
                            int diff2 = Math.abs(val1-listj.get(index2));
                            if ( diff2 < diff )
                                diff = diff2;
                        }
                        if ( diff < dist )
                            dist = diff;
                    }
                }
            }
            // so now dist is the minimum distance between terms for term i
            distances[i] = dist;
        }
        // compute total distance between matches
        int totalDist = 0;
        for ( int i=0;i<distances.length;i++ )
            totalDist += distances[i];
        // compute total match range
        ArrayList<Integer> list1 = map.get(terms.get(0));
        ArrayList<Integer> list2 = map.get(terms.get(terms.size()-1));
        int minPos = list1.get(0);
        int maxEnd = list2.get(list2.size()-1);
        // score is a 100 less the fraction of the distance between terms  
        // over the total distance covered by the terms. So for 0 distance  
        // score will be 100 and for maximally separated terms the score will 
        // be close to 0.
        this.score = 100.0f-totalDist*100/(maxEnd-minPos);
    }
    /**
     * Get the first term
     * @return 
     */
    public String firstTerm()
    {
        return terms.get(0);
    }
    /**
     * Get the versions shared by the match
     * @param mvd the mvd we are found in
     * @return the set of versions the match is found in 
     */
    public BitSet getVersions( MVD mvd )
    {
        BitSet bs = new BitSet();
        for ( int i=0;i<terms.size();i++ )
        {
            String term = terms.get(i);
            ArrayList<Integer> list = map.get(terms.get(i));
            BitSet versions = new BitSet();
            for ( int j=0;j<list.size();j++ )
            {
                int pos = list.get(j);
                BitSet bs2 = mvd.find( term, pos, term );
                versions.or( bs2 );
            }
            if ( bs.cardinality()==0 )
                bs.or( versions );
            else
                bs.and( versions );
        }
        return bs;
    }
    /**
     * Get all positions in a single array
     * @return an array of ints
     */
    public int[] getPositions()
    {
        int count = 0;
        for ( int i=0;i<terms.size();i++ )
        {
            ArrayList<Integer> list = map.get(terms.get(i));
            count += list.size();
        }
        int[] positions = new int[count];
        int k=0;
        for ( int i=0;i<terms.size();i++ )
        {
            ArrayList<Integer> list = map.get(terms.get(i));
            for ( int j=0;j<list.size();j++ )
                positions[k++] = list.get(j);
        }
        return positions;
    }
    public int numTerms()
    {
        return terms.size();
    }
    public String getTerm( int index )
    {
        return terms.get(index);
    }
    /**
     * Get all positions in a single array
     * @return i the index of the term
     * @return an array of positions for that term
     */
    public int[] getTermPositions( int i )
    {
        int[] positions;
        ArrayList<Integer> list = map.get(terms.get(i));
        if ( list != null )
        {
            positions = new int[list.size()];
            for ( int j=0;j<positions.length;j++ )
                positions[j] = list.get(j);
        }
        else    // handle error softly
            positions = new int[0];
        return positions;
    }
    /**
     * Get the first position of the term
     * @return an int
     */
    public int firstPositionOfTerm( String term )
    {
        ArrayList<Integer> list = map.get( term );
        if ( list != null )
            return list.get(0);
        else
            return -1;
    }
    /**
     * Get the index of the greatest list item less than a value
     * @param list the list
     * @param value the value to look for
     * @return the index of the biggest item less than value
     */
    private int getIndex( ArrayList<Integer> list, int value )
    {
        int top = 0;
        int bot = list.size()-1;
        int mid=0;
        while ( top <= bot )
        {
            mid = (top+bot)/2; // NB integer arithmetic
            if ( value < list.get(mid) )
            {
                if ( mid == 0 )
                    // value < than first item
                    return -1;  
                else
                    bot = mid-1;
            }
            else    // value >= list[mid]
            {
                if ( mid == list.size()-1 )
                    // value is >= last item
                    break;
                else if ( value >= list.get(mid+1) )
                    top = mid+1;
                else // list[mid] must be biggest <= value
                    break;
            }
        }
        return mid;
    } 
    /**
     * Find an item in a list. This is binary search O(log N)
     * @param list the sorted list 
     * @param value the value to find
     * @return true if it was there 
     */
    private boolean find( ArrayList<Integer> list, int value )
    {
        int top = 0;
        int bot = list.size()-1;
        while ( top <= bot )
        {
            int mid = (top+bot)/2;
            int midVal = list.get(mid);
            if ( value < midVal )
                bot = mid-1;
            else if ( value > midVal )
                top = mid+1;
            else
                return true;
        }
        return false;
    }
    /**
     * We're expanding by one term: recalc the score and resize everything
     * @param loc the new location to follow the current ones
     * @param word the new word whose position is loc
     */
    void addTerm( Location loc, String word )
    {
        ArrayList<Integer> list = map.get(word);
        if ( list == null )
        {
            list = new ArrayList<Integer>();
            list.add(loc.pos);
            map.put( word, list );
            terms.add(word);
        }
        else if ( !find(list,loc.pos) )
        {
            // keep list sorted
            int index = getIndex(list,loc.pos);
            list.add( index+1, loc.pos );
        }
        recalcScore();
    }
    /**
     * For debugging
     * @return a string representation of this match
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "doc: ");
        sb.append( docId );
        sb.append( ", terms: ");
        Set<String> keys = map.keySet();
        Iterator<String> iter = keys.iterator();
        while ( iter.hasNext() )
        {
            String key = iter.next();
            sb.append(key);
            ArrayList<Integer> list = map.get(key);
            sb.append(", positions: ");
            for ( int i=0;i<list.size();i++ )
            {
                sb.append(list.get(i));
                if ( i < list.size()-1)
                    sb.append(",");
            }
            if ( iter.hasNext() )
                sb.append("; ");
        }
        sb.append("; score: ");
        sb.append(score);
        return sb.toString();
    }
}
