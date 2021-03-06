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

 package org.apache.ranger.services.hbase.client;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.Map.Entry;

import javax.security.auth.Subject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.ranger.plugin.client.BaseClient;
import org.apache.ranger.plugin.client.HadoopException;

import com.google.protobuf.ServiceException;

public class HBaseClient extends BaseClient {

	private static final Log LOG 			 = LogFactory.getLog(HBaseClient.class) ;

	private static Subject subj 			 = null;

  private Configuration conf;

  private static List<String> rangerInternalPropertyKeys = Arrays.asList("username",
    "password", "keytabfile");

	public HBaseClient(String serivceName,Map<String,String> connectionProp) {

		super(serivceName, addDefaultHBaseProp(connectionProp)) ;
    conf = HBaseConfiguration.create() ;

    Set<String> rangerInternalPropertyKeys = getConfigHolder().getRangerInternalPropertyKeys();
    for (Map.Entry<String, String> entry: connectionProperties.entrySet())  {
      String key = entry.getKey();
      String value = entry.getValue();
      if (rangerInternalPropertyKeys.contains(key)) {
        // skip
      }  else {
        conf.set(key, value);
      }
    }

	}
	
	//TODO: temporary solution - to be added to the UI for HBase 
	private static Map<String,String> addDefaultHBaseProp(Map<String,String> connectionProp) {
		if (connectionProp != null) {
			String param = "zookeeper.znode.parent" ;
			String unsecuredPath = "/hbase-unsecure" ;
			String authParam = "hadoop.security.authorization" ;
			
			String ret = connectionProp.get(param) ;
			LOG.info("HBase connection has [" + param + "] with value [" + ret + "]");
			if (ret == null) {
				ret = connectionProp.get(authParam) ;
				LOG.info("HBase connection has [" + authParam + "] with value [" + ret + "]");
				if (ret != null && ret.trim().equalsIgnoreCase("false")) {
					LOG.info("HBase connection is resetting [" + param + "] with value [" + unsecuredPath + "]");
					connectionProp.put(param, unsecuredPath) ;
				}
			}
		}
		return connectionProp;
	}
	
	public static HashMap<String, Object> testConnection(String dataSource,
			Map<String, String> configs) {

		HashMap<String, Object> responseData = new HashMap<String, Object>();
		final String errMsg = " You can still save the repository and start creating "
				+ "policies, but you would not be able to use autocomplete for "
				+ "resource names. Check xa_portal.log for more info.";
		boolean connectivityStatus = false;

		HBaseClient connectionObj = new HBaseClient(dataSource,
										configs);
		if (connectionObj != null) {
			connectivityStatus = connectionObj.getHBaseStatus();
		}
		
		if (connectivityStatus) {
			String successMsg = "TestConnection Successful";
			generateResponseDataMap(connectivityStatus, successMsg, successMsg,
					null, null, responseData);
		} else {
			String failureMsg = "Unable to retrieve any databases using given parameters.";
			generateResponseDataMap(connectivityStatus, failureMsg, failureMsg
					+ errMsg, null, null, responseData);
		}
		return responseData;
	}
	
	public boolean getHBaseStatus() {
		boolean hbaseStatus = false;
		subj = getLoginSubject();
		final String errMsg = " You can still save the repository and start creating "
				+ "policies, but you would not be able to use autocomplete for "
				+ "resource names. Check xa_portal.log for more info.";
		if (subj != null) {
			try {

				hbaseStatus = Subject.doAs(subj, new PrivilegedAction<Boolean>() {
					@Override
					public Boolean run() {
						Boolean hbaseStatus1 = false;
						try {
						    LOG.info("getHBaseStatus: creating default Hbase configuration");

							LOG.info("getHBaseStatus: setting config values from client");
							setClientConfigValues(conf);						
						    LOG.info("getHBaseStatus: checking HbaseAvailability with the new config");
							HBaseAdmin.checkHBaseAvailable(conf);					
						    LOG.info("getHBaseStatus: no exception: HbaseAvailability true");
							hbaseStatus1 = true;
						} catch (ZooKeeperConnectionException zce) {
							String msgDesc = "getHBaseStatus: Unable to connect to `ZooKeeper` "
									+ "using given config parameters.";
							HadoopException hdpException = new HadoopException(msgDesc, zce);
							hdpException.generateResponseDataMap(false, getMessage(zce),
									msgDesc + errMsg, null, null);
							
							LOG.error(msgDesc + zce) ;
							throw hdpException;
							
						} catch (MasterNotRunningException mnre) {
							String msgDesc = "getHBaseStatus: Looks like `Master` is not running, "
									+ "so couldn't check that running HBase is available or not, "
									+ "Please try again later.";
							HadoopException hdpException = new HadoopException(
									msgDesc, mnre);
							hdpException.generateResponseDataMap(false,
									getMessage(mnre), msgDesc + errMsg,
									null, null);
							LOG.error(msgDesc + mnre) ;
							throw hdpException;

						} catch (ServiceException se) {
							String msgDesc = "getHBaseStatus: Unable to check availability of "
									+ "Hbase environment [" + getConfigHolder().getDatasourceName() + "].";
							HadoopException hdpException = new HadoopException(msgDesc, se);
							hdpException.generateResponseDataMap(false, getMessage(se),
									msgDesc + errMsg, null, null);
							LOG.error(msgDesc + se);
							throw hdpException;
							
						} catch(IOException io) {
							String msgDesc = "getHBaseStatus: Unable to check availability of"
									+ " Hbase environment [" + getConfigHolder().getDatasourceName() + "].";
							HadoopException hdpException = new HadoopException(msgDesc, io);
							hdpException.generateResponseDataMap(false, getMessage(io),
									msgDesc + errMsg, null, null);
							LOG.error(msgDesc + io) ;
							throw hdpException;
							
						}  catch (Throwable e) {
							String msgDesc = "getHBaseStatus: Unable to check availability of"
									+ " Hbase environment [" + getConfigHolder().getDatasourceName() + "].";
							LOG.error(msgDesc + e);
							hbaseStatus1 = false;
							HadoopException hdpException = new HadoopException(msgDesc, e);
							hdpException.generateResponseDataMap(false, getMessage(e),
									msgDesc + errMsg, null, null);
							throw hdpException;
						}
						return hbaseStatus1;
					}
				}) ;
			} catch (SecurityException se) {
				String msgDesc = "getHBaseStatus: Unable to connect to HBase Server instance ";
				HadoopException hdpException = new HadoopException(msgDesc, se);
				hdpException.generateResponseDataMap(false, getMessage(se),
						msgDesc + errMsg, null, null);
				LOG.error(msgDesc + se) ;
				throw hdpException;
			}
		} else {
			LOG.error("getHBaseStatus: secure login not done, subject is null");
		}
		
		return hbaseStatus;
	}
	
	private void setClientConfigValues(Configuration conf) {
		if (this.connectionProperties == null) return;
		Iterator<Entry<String, String>> i =  this.connectionProperties.entrySet().iterator();
		while (i.hasNext()) {
			Entry<String, String> e = i.next();
			String v = conf.get(e.getKey());
			if (v != null && !v.equalsIgnoreCase(e.getValue())) {
				conf.set(e.getKey(), e.getValue());
			}
		}		
	}

	public List<String> getTableList(final String tableNameMatching, final List<String> existingTableList ) {
		List<String> ret = null ;
		final String errMsg = " You can still save the repository and start creating "
				+ "policies, but you would not be able to use autocomplete for "
				+ "resource names. Check xa_portal.log for more info.";
		
		subj = getLoginSubject();
		
		if (subj != null) {
				ret = Subject.doAs(subj, new PrivilegedAction<List<String>>() {
		
					@Override
					public List<String> run() {
						
						List<String> tableList = new ArrayList<String>() ;
						HBaseAdmin admin = null ;
						try {
							LOG.info("getTableList: creating default Hbase configuration");
							Configuration conf = HBaseConfiguration.create() ;	// dilli
							LOG.info("getTableList: setting config values from client");
							setClientConfigValues(conf);						
						    LOG.info("getTableList: checking HbaseAvailability with the new config");
							HBaseAdmin.checkHBaseAvailable(conf);					
						    LOG.info("getTableList: no exception: HbaseAvailability true");
							admin = new HBaseAdmin(conf) ;
							for (HTableDescriptor htd : admin.listTables(tableNameMatching)) {
								if (htd == null) {
									LOG.error("getTableList: null HTableDescription received from HBaseAdmin.listTables");
								} else {
									String tableName = htd.getNameAsString();
									if (existingTableList != null && existingTableList.contains(tableName)) {
										continue;
									} else {
										tableList.add(htd.getNameAsString());
									}
								}
							}
						} catch (ZooKeeperConnectionException zce) {
							String msgDesc = "getTableList: Unable to connect to `ZooKeeper` "
									+ "using given config parameters.";
							HadoopException hdpException = new HadoopException(msgDesc, zce);
							hdpException.generateResponseDataMap(false, getMessage(zce),
									msgDesc + errMsg, null, null);
							LOG.error(msgDesc + zce) ;
							throw hdpException;
							
						} catch (MasterNotRunningException mnre) {
							String msgDesc = "getTableList: Looks like `Master` is not running, "
									+ "so couldn't check that running HBase is available or not, "
									+ "Please try again later.";
							HadoopException hdpException = new HadoopException(
									msgDesc, mnre);
							hdpException.generateResponseDataMap(false,
									getMessage(mnre), msgDesc + errMsg,
									null, null);
							LOG.error(msgDesc + mnre) ;
							throw hdpException;

						}  catch(IOException io) {
							String msgDesc = "getTableList: Unable to get HBase table List for [repository:"
									+ getConfigHolder().getDatasourceName() + ",table-match:" 
									+ tableNameMatching + "].";
							HadoopException hdpException = new HadoopException(msgDesc, io);
							hdpException.generateResponseDataMap(false, getMessage(io),
									msgDesc + errMsg, null, null);
							LOG.error(msgDesc + io) ;
							throw hdpException;
						}   catch (Throwable e) {
							String msgDesc = "getTableList : Unable to get HBase table List for [repository:"
									+ getConfigHolder().getDatasourceName() + ",table-match:" 
									+ tableNameMatching + "].";
							LOG.error(msgDesc + e);
							HadoopException hdpException = new HadoopException(msgDesc, e);
							hdpException.generateResponseDataMap(false, getMessage(e),
									msgDesc + errMsg, null, null);
							throw hdpException;
						}
						finally {
							if (admin != null) {
								try {
									admin.close() ;
								} catch (IOException e) {
									LOG.error("Unable to close HBase connection [" + getConfigHolder().getDatasourceName() + "]", e);
								}
							}
						}
						return tableList ;
					}
					
				}) ;
		}
		return ret ;
	}
	
	
	public List<String> getColumnFamilyList(final String columnFamilyMatching, final List<String> tableList,final List<String> existingColumnFamilies) {
		List<String> ret = null ;
		final String errMsg = " You can still save the repository and start creating "
				+ "policies, but you would not be able to use autocomplete for "
				+ "resource names. Check xa_portal.log for more info.";
		
		subj = getLoginSubject();
		if (subj != null) {
			try {

				ret = Subject.doAs(subj, new PrivilegedAction<List<String>>() {
					String tblName = null;
					@Override
					public List<String> run() {
						List<String> colfList = new ArrayList<String>() ;
						HBaseAdmin admin = null ;
						try {
							LOG.info("getColumnFamilyList: creating default Hbase configuration");
							Configuration conf = HBaseConfiguration.create() ;		// dilli
							LOG.info("getColumnFamilyList: setting config values from client");
							setClientConfigValues(conf);						
						    LOG.info("getColumnFamilyList: checking HbaseAvailability with the new config");
							HBaseAdmin.checkHBaseAvailable(conf);					
						    LOG.info("getColumnFamilyList: no exception: HbaseAvailability true");
							admin = new HBaseAdmin(conf) ;
							if (tableList != null) {
								for (String tableName: tableList) {
									tblName = tableName;
									HTableDescriptor htd = admin.getTableDescriptor(tblName.getBytes()) ;
									if (htd != null) {
										for (HColumnDescriptor hcd : htd.getColumnFamilies()) {
											String colf = hcd.getNameAsString() ;
											if (colf.matches(columnFamilyMatching)) {
											    if (existingColumnFamilies != null && existingColumnFamilies.contains(colf)) {
													continue;
												} else {
													colfList.add(colf);
												}
													
											}
										}
									}
								}
							}
						}  catch (ZooKeeperConnectionException zce) {
							String msgDesc = "getColumnFamilyList: Unable to connect to `ZooKeeper` "
									+ "using given config parameters.";
							HadoopException hdpException = new HadoopException(msgDesc, zce);
							hdpException.generateResponseDataMap(false, getMessage(zce),
									msgDesc + errMsg, null, null);
							LOG.error(msgDesc + zce) ;
							throw hdpException;
							
						} catch (MasterNotRunningException mnre) {
							String msgDesc = "getColumnFamilyList: Looks like `Master` is not running, "
									+ "so couldn't check that running HBase is available or not, "
									+ "Please try again later.";
							HadoopException hdpException = new HadoopException(
									msgDesc, mnre);
							hdpException.generateResponseDataMap(false,
									getMessage(mnre), msgDesc + errMsg,
									null, null);
							LOG.error(msgDesc + mnre) ;
							throw hdpException;

						}  catch(IOException io) {
							String msgDesc = "getColumnFamilyList: Unable to get HBase ColumnFamilyList for "
									+ "[repository:" +getConfigHolder().getDatasourceName() + ",table:" + tblName
									+ ", table-match:" + columnFamilyMatching + "] ";
							HadoopException hdpException = new HadoopException(msgDesc, io);
							hdpException.generateResponseDataMap(false, getMessage(io),
									msgDesc + errMsg, null, null);
							LOG.error(msgDesc + io) ;
							throw hdpException; 
						} catch (SecurityException se) {
								String msgDesc = "getColumnFamilyList: Unable to get HBase ColumnFamilyList for "
										+ "[repository:" +getConfigHolder().getDatasourceName() + ",table:" + tblName
										+ ", table-match:" + columnFamilyMatching + "] ";
								HadoopException hdpException = new HadoopException(msgDesc, se);
								hdpException.generateResponseDataMap(false, getMessage(se),
										msgDesc + errMsg, null, null);
								LOG.error(msgDesc + se) ;
								throw hdpException;							
							
						}  catch (Throwable e) {
							String msgDesc = "getColumnFamilyList: Unable to get HBase ColumnFamilyList for "
									+ "[repository:" +getConfigHolder().getDatasourceName() + ",table:" + tblName
									+ ", table-match:" + columnFamilyMatching + "] ";
							LOG.error(msgDesc);
							HadoopException hdpException = new HadoopException(msgDesc, e);
							hdpException.generateResponseDataMap(false, getMessage(e),
									msgDesc + errMsg, null, null);
							LOG.error(msgDesc + e) ;
							throw hdpException;
						}
						finally {
							if (admin != null) {
								try {
									admin.close() ;
								} catch (IOException e) {
									LOG.error("Unable to close HBase connection [" + getConfigHolder().getDatasourceName() + "]", e);
								}
							}
						}
						return colfList ;
					}
					
				}) ;
			} catch (SecurityException se) {
				String msgDesc = "getColumnFamilyList: Unable to connect to HBase Server instance ";
				HadoopException hdpException = new HadoopException(msgDesc, se);
				hdpException.generateResponseDataMap(false, getMessage(se),
						msgDesc + errMsg, null, null);
				LOG.error(msgDesc + se) ;
				throw hdpException;
			}
		}
		return ret ;
	}

}

