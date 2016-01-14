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
import java.io.PrintWriter;

/**
 * Provide a kind of callback facility to report progress
 * @author desmond
 */
public class Progress
{
    int total;
    int amount;
    int lastValue;
    int last;
    int interval;
    PrintWriter pw;
    public Progress( PrintWriter pw )
    {
        this.pw = pw;
        this.interval = 5;
        this.last = 0;
    }
    /**
     * Set the total we are working towards
     * @param total the total value when finished
     */
    public void setTotal( int total )
    {
        this.total = total;
    }
    public boolean finished()
    {
        return this.amount==this.total;
    }
    /**
     * Update the progress
     */
    public void update( int value ) 
    {
        amount += value;
        int diff = (amount-last)*100/total;
        if ( diff >= interval || amount == total )
        {
            last = amount;
            pw.println( amount*100/total );
            pw.flush();
        }
    }
}
