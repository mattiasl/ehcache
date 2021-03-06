/**
 *  Copyright 2003-2006 Greg Luck
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.constructs.web.filter;

import com.meterware.httpunit.HttpInternalErrorException;
import net.sf.ehcache.constructs.web.AbstractWebTest;

/**
 * {@link Filter#doFilter} captures all exceptions passed up to it from subclasses.
 *
 * In a web application, because of the vaguaries of HTTP and networking, there are often errors
 * that happen near continously. We want to filter these out.
 *
 *
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @version $Id$
 */
public class FilterTest extends AbstractWebTest {

    /**
     * The GzipFilter filter definition is set up to do special logging for NullPointerException
     * Check that debug level logging is used. This needs to be done manually at present
     */
    public void testHandlingOfNullPointerException() throws Exception {
        try {
            getResponseFromAcceptGzipRequest("/errors/NullPointerException.jsp");
            fail();
        } catch (HttpInternalErrorException e) {
            //expected
        }
    }

    /**
     * The GzipFilter's init-params are not set up for IllegalArgumentException.
     * Check that the default behaviour applies. This needs to be done manually at present.
     */
    public void testHandlingOfIllegalArgumentException() throws Exception {
        try {
            getResponseFromAcceptGzipRequest("/errors/IllegalArgumentException.jsp");
            fail();
        } catch (HttpInternalErrorException e) {
            //expected
        }
    }
}
