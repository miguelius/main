/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.lareferencia.xoai.services.api.xoai;

import com.lyncode.xoai.dataprovider.services.api.RepositoryConfiguration;
import org.lareferencia.xoai.services.api.context.ContextServiceException;

public interface IdentifyResolver {
    RepositoryConfiguration getIdentify() throws ContextServiceException;
}
