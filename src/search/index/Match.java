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
/**
 * a Hit being a full match in some document
 * @author desmond
 */
public class Match 
{
    /** document id in index */
    int docId;  
    /** the matched terms in order as found */
    String[] terms;
    /** positions of terms in doc */
    int[] positions;
    /** the score or edit distance of the matched words */
    float score;
    /** number of characters allowed between words for literal matches */
    static final int literalSlop = 10;
    /** the type of the match derived from query */
    public MatchType type;
    /**
     * Create an initial match from a single location
     * @param loc the location
     * @param word the first term
     * @param type the type of the match
     */
    public Match( Location loc, String word, MatchType type )
    {
        this.positions = new int[1];
        this.terms = new String[1];
        this.terms[0] = word;
        this.docId = loc.docId;
        this.positions[0] = loc.pos;
        this.score = 100.0f;
        this.type = type;
    }
    /**
     * Test if this location could follow us under literal constraints
     * @param loc the location
     * @return true if loc fits after us, else false
     */
    boolean testLiteral( Location loc )
    {
        // d'oh! must be same document
        if ( loc.docId == this.docId )
        {
            return loc.pos <= this.positions[this.terms.length-1]
                +literalSlop+this.terms[this.terms.length-1].length();
        }
        return false;
    }
    /**
     * Test if this location could follow us under boolean (AND) constraints
     * @param loc the location
     * @return true if loc fits after us, else false
     */
    boolean testBoolean( Location loc )
    {
        return this.docId == loc.docId;
    }
    public boolean equals( Object other )
    {
        if ( other instanceof Match )
        {
            Match oMatch = (Match) other;
            if ( oMatch.terms.length == this.terms.length )
            {
                for ( int i=0;i<terms.length;i++ )
                    if ( !this.terms[i].equals(oMatch.terms[i]) )
                        return false;
                // check if the terms all overlap
                for ( int i=0;i<positions.length;i++ )
                {
                    int iStart = this.positions[i];
                    int oStart = oMatch.positions[i];
                    int iEnd = iStart+this.terms[i].length();
                    int oEnd = oStart+oMatch.terms[i].length();
                    if ( iEnd < oStart || iStart > oEnd )
                        return false;
                }
                // all terms overlap and are equal
                return true;
            }
            else
                return false;
        }
        else
            return false;
    }
    /**
     * Recalculate the score 
     */
    private void recalcScore()
    {
        int[] distances = new int[terms.length-1];
        for ( int i=0;i<terms.length-1;i++ )
        {
            int dist = Integer.MAX_VALUE;
            // find the closest term
            for ( int j=0;j<terms.length;j++ )
            {
                if ( i != j )
                {
                    if ( positions[j] > positions[i] )
                    {
                        int endi = terms[i].length()+positions[i];
                        int startj = positions[j];
                        int delta = startj-endi;
                        if ( delta < dist )
                            dist = delta;
                    }
                    else if ( positions[j] < positions[i] )
                    {
                        int endj = terms[j].length()+positions[j];
                        int starti = positions[i];
                        int delta = starti-endj;
                        if ( delta < dist )
                            dist = delta;
                    }
                }
            }
            // so now dist is the minimum distance between terms for term i
            distances[i] = dist;
        }
        // compute total distance btween matches
        int totalDist = 0;
        for ( int i=0;i<distances.length;i++ )
            totalDist += distances[i];
        // compute total match range
        int minPos = this.positions[0];
        int maxEnd = this.positions[0]+this.terms[0].length();
        for ( int i=1;i<terms.length;i++ )
        {
            if ( this.positions[i] < minPos )
                minPos = this.positions[i];
            int end = this.positions[i]+this.terms[i].length();
            if ( end>maxEnd )
                maxEnd = end;
        }
        // score is a 100 less the fraction of the distance between terms  
        // over the total distance covered by the terms. So for 0 distance  
        // score will be 100 and for maximally separated terms the score will 
        // be close to 0.
        this.score = 100.0f-totalDist*100/(maxEnd-minPos);
    }
    /**
     * We're expanding by one term: recalc the score and resize everything
     * @param loc the new location to follow the current ones
     * @param word the new word whose position is loc
     */
    void addTerm( Location loc, String word )
    {
        int[] tempPositions = new int[this.positions.length+1];
        String[] tempTerms = new String[this.terms.length+1];
        for ( int i=0;i<this.positions.length;i++ )
            tempPositions[i] = this.positions[i];
        for ( int i=0;i<this.terms.length;i++ )
            tempTerms[i] = this.terms[i];
        this.positions = tempPositions;
        this.terms = tempTerms;
        this.positions[this.positions.length-1] = loc.pos;
        this.terms[this.terms.length-1] = word;
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
        for ( int i=0;i<terms.length;i++ )
        {
            sb.append(terms[i]);
            if ( i < terms.length-1)
                sb.append(",");
        }
        sb.append(", positions: ");
        for ( int i=0;i<terms.length;i++ )
        {
            sb.append(positions[i]);
            if ( i < positions.length-1)
                sb.append(",");
        }
        sb.append(", score: ");
        sb.append(score);
        return sb.toString();
    }
}
