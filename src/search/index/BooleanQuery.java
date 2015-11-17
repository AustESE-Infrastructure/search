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
import java.util.ArrayList;
/**
 * Build a simple boolean (AND only) query
 * @author desmond
 */
public class BooleanQuery extends Query
{
    BooleanQuery( String queryStr, String lang )
    {
        String[] parts = queryStr.split(" ");
        ArrayList<String> list = new ArrayList<String>();
        for ( int i=0;i<parts.length;i++ )
        {
            if ( parts[i].length()>0 && !Utils.isStopWord(parts[i],lang) )
                list.add( parts[i] );
        }
        this.terms = new String[list.size()];
        list.toArray(this.terms);
    }
}
