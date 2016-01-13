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
package search.exception;

/**
 * A format exception
 * @author desmond
 */
public class FormatException extends SearchException 
{
    /**
     * Create a general MMLException from scratch
     * @param message the message it is to bear
     */
    public FormatException( String message )
    {
        super( message );
    }
    /**
     * Wrapper for another exception
     * @param e the other exception
     */
    public FormatException( Exception e )
    {
        super( e );
    }
}
