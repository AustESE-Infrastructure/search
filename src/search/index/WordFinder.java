/*
 *  NMerge is Copyright 2015 Desmond Schmidt
 * 
 *  This file is part of NMerge. NMerge is a Java library for merging 
 *  multiple versions into multi-version documents (MVDs), and for 
 *  reading, searching and comparing them.
 *
 *  NMerge is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  NMerge is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package search.index;
import edu.luc.nmerge.mvd.Pair;
import search.exception.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;
import java.util.HashSet;

/**
 * Navigate an MVD looking for words
 * @author desmond
 */
public class WordFinder
{
    /** local copy of MVDs pairs list - read only */
    ArrayList<Pair> pairs;
    int mvdPosition;
    Map<String,ArrayList<Location>> map;
    int docId;
    String lang;
    String projid;
    HashSet<String> stopwords;
    Hyphenator hyphenator;
    WordFinder( ArrayList<Pair> pairs, Map<String,ArrayList<Location>> map, 
        HashSet<String> sw, String lang, String projid, int docId ) throws SearchException
    {
        try
        {
            this.pairs = pairs;
            this.map = map;
            this.docId = docId;
            this.stopwords = sw;
            this.lang = lang;
            this.projid = projid;
            this.hyphenator = new Hyphenator( projid, lang );
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * is one bitset a subset of the other?
     * @param v1 the subset
     * @param v2 the set it may be inside
     * @return true if it is else false
     */
    boolean isSubsetOf( BitSet v1, BitSet v2 )
    {
        for (int i=v1.nextSetBit(0); i>=0; i=v1.nextSetBit(i+1))
		{
			if ( !v1.get(i) )
			{
				return false;
			}
		}
        return true;
    }
    /**
     * Navigate an MVD finding all words and stuff them in an index
     * @return the number of word-locations found
     */
    public int find( BitSet bs ) throws Exception
    {
	    WordSearchState inactive = null;
		WordSearchState active = null;
        this.mvdPosition = 0;
        int nWords = 0;
        inactive = new WordSearchState( bs, this );
        for ( int i=0;i<pairs.size();i++ )
	    {
		    Pair temp = pairs.get( i );
		    // move all elements from active to inactive
		    if ( inactive == null )
			    inactive = active;
		    else
			    inactive.append( active );
		    active = null;
		    // move matching SearchStates into active
		    WordSearchState s = inactive;
		    while ( s != null )
		    {
			    WordSearchState sequential = s.following;
			    if ( s.v.intersects(temp.versions) )
			    {
                    char[] chars = temp.getChars();
                    if ( chars.length>0 && s.willBreak(chars[0]) )
                        if ( s.outputWord() )
                            nWords++;
                    WordSearchState child = s.split(temp.versions);
				    if ( active == null )
					    active = child;
				    else
					    active.append( child );
				    if ( s.v.isEmpty() )
                        inactive = inactive.remove( s );
			    }
			    s = sequential;
		    }
		    // now process each char of the pair
		    if ( active != null )
		    {
			    char[] data = temp.getChars();
			    for ( int j=0;j<data.length;j++ )
			    {
				    WordSearchState ss = active;
				    while ( ss != null )
				    {
					    if ( ss.update(data[j]) )
                            nWords++;
					    ss = ss.following;
				    }
				    // now prune the active list
				    WordSearchState s1 = active;
				    while ( s1 != null )
                    {
                        WordSearchState s2 = s1.following;
                        while ( s2 != null )
                        {
                            WordSearchState sequential = s2.following;
                            if ( isSubsetOf(s1.v,temp.versions) 
                                && isSubsetOf(s2.v,temp.versions) )
                            {
                                s1.merge( s2 );
                                active.remove( s2 );
                            }
                            s2 = sequential;
                        }
                        s1 = s1.following;
                    }
                    this.mvdPosition++;
			    }
		    }
	    }
        return nWords;
    }
}
