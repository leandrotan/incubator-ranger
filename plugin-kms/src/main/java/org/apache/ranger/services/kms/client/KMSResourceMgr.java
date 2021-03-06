/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.services.kms.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.ranger.plugin.service.ResourceLookupContext;

public class KMSResourceMgr {
	public static final 	Logger 	LOG 		= Logger.getLogger(KMSResourceMgr.class);
	private static final 	String  KMSKEY	= "keyname";
	
	public static HashMap<String, Object> validateConfig(String serviceName, Map<String, String> configs) throws Exception {
		HashMap<String, Object> ret = null;
		
		if(LOG.isDebugEnabled()) {
			LOG.debug("==> KMSResourceMgr.validateConfig ServiceName: "+ serviceName + "Configs" + configs ) ;
		}	
		
		try {
			ret = KMSClient.testConnection(serviceName, configs);
		} catch (Exception e) {
			LOG.error("<== KMSResourceMgr.validateConfig Error: " + e) ;
		  throw e;
		}
		
		if(LOG.isDebugEnabled()) {
			LOG.debug("<== KMSResourceMgr.validateConfig Result : "+ ret  ) ;
		}	
		return ret;
	}
	
    public static List<String> getKMSResources(String serviceName, Map<String, String> configs,ResourceLookupContext context) {
        String 		 userInput 				  = context.getUserInput();
		Map<String, List<String>> resourceMap = context.getResources();
	    List<String> 		resultList        = null;
		List<String> 		kmsKeyList 	  = null;
		String  			kmsKeyName     = null;
		
		if ( resourceMap != null && !resourceMap.isEmpty() && resourceMap.get(KMSKEY) != null ) {
			kmsKeyName = userInput;
			kmsKeyList = resourceMap.get(KMSKEY); 
		} else {
			kmsKeyName = userInput;
		}
		
		
        if (configs == null || configs.isEmpty()) {
                LOG.error("Connection Config is empty");
        } else {
                
                String url 		= configs.get("provider");
                String username = configs.get("username");
                String password = configs.get("password");
                resultList = getKMSResource(url, username, password,kmsKeyName,kmsKeyList) ;
        }
        return resultList ;
    }

    public static List<String> getKMSResource(String url, String username, String password,String kmsKeyName, List<String> kmsKeyList) {
        final KMSClient KMSClient = KMSConnectionMgr.getKMSClient(url, username, password);
        List<String> topologyList = KMSClient.getKeyList(kmsKeyName, kmsKeyList);
        return topologyList;
    }    
}
