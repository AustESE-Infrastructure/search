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
 * Types of match
 * @author desmond
 */
public enum MatchType {
    LITERAL,
    BOOLEAN,
    OTHER;
    public static MatchType fromQuery( Query query )
    {
        if ( query instanceof BooleanQuery )
            return BOOLEAN;
        else if ( query instanceof LiteralQuery )
            return LITERAL;
        else
            return OTHER;
    }
}
