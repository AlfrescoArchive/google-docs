/**
 * Copyright (C) 2005-2015 Alfresco Software Limited.
 * 
 * This file is part of Alfresco
 * 
 * Alfresco is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with Alfresco. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.alfresco.integrations.web.evaluator;


import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.web.evaluator.BaseEvaluator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;


public class IsMimetypeEvaluator
    extends BaseEvaluator
{
    private static final Log log = LogFactory.getLog(IsMimetypeEvaluator.class);

    private String           accessor;


    public void setMimetypes(String accessor)
    {
        this.accessor = accessor;
    }


    @Override
    public boolean evaluate(JSONObject jsonObject)
    {
        JSONObject importFormats = (JSONObject)getJSONValue(getMetadata(), accessor);

        try
        {
            JSONObject node = (JSONObject)jsonObject.get("node");
            if (node == null)
            {
                return false;
            }
            else
            {
                String mimetype = (String)node.get("mimetype");
                if (mimetype == null || !importFormats.containsKey(mimetype))
                {
                    log.debug("NodeRef: " + node.get("nodeRef") + "; Mimetype not supported: " + mimetype);
                    return false;
                }
                log.debug("NodeRef: " + node.get("nodeRef") + "; Mimetype supported: " + mimetype);
            }
        }
        catch (Exception err)
        {
            throw new AlfrescoRuntimeException("Failed to run action evaluator: " + err.getMessage());
        }

        return true;
    }

}
