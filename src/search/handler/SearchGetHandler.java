/*
 * This file is part of Project.
 *
 *  Project is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Project is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Project.  If not, see <http://www.gnu.org/licenses/>.
 *  (c) copyright Desmond Schmidt 2014
 */

package search.handler;

import calliope.core.Utils;
import search.constants.Service;
import search.exception.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * Get a Search document from the database
 * @author desmond
 */
public class SearchGetHandler extends SearchHandler
{
    public void handle(HttpServletRequest request,
            HttpServletResponse response, String urn) throws SearchException 
    {
        response.setCharacterEncoding("UTF-8");
        try 
        {
            String first = Utils.first(urn);
            if ( first.equals(Service.BUILD) )
            {
                PrintWriter writer = response.getWriter();
                BuildIndex.rebuild(writer);
            }
            else
                throw new Exception("Unknown GET service "+first);
        } 
        catch (Exception e) 
        {
             throw new SearchException(e);
        }
    }   
}
