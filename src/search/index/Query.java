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
 * A factory class for more specific forms of query
 * @author desmond
 */
public class Query {
    protected String[] terms;
    public static Query parse( String queryStr, String lang )
    {
        if ( Utils.isQuoted(queryStr) )
            return new LiteralQuery(queryStr,lang);
        else
            return new BooleanQuery(queryStr,lang);
    }
    /**
     * Convert plain text to a basic AND query
     */
    static String toAndQuery( String queryStr )
    {
        String[] parts = queryStr.split(" ");
        StringBuilder sb = new StringBuilder();
        for ( int i=0;i<parts.length;i++ )
        {
            sb.append( parts[i] );
            if ( i < parts.length-1 )
                sb.append(" AND ");
        }
        return sb.toString();
    }
}
