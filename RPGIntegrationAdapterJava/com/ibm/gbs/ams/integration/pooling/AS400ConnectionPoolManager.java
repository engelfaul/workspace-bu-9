package com.ibm.gbs.ams.integration.pooling;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400ConnectionPool;
import com.ibm.as400.access.ConnectionPoolException;

public class AS400ConnectionPoolManager {

	private static final int AS400_SERVICE = AS400.COMMAND;

	private static AS400ConnectionPoolManager instance;
	AS400ConnectionPool as400ConnPool = null;

	protected AS400ConnectionPoolManager() {
		as400ConnPool = new AS400ConnectionPool();
		as400ConnPool.setThreadUsed(true);
	}

	public static AS400ConnectionPoolManager getInstance() {
		if (instance == null)
			instance = new AS400ConnectionPoolManager();
		return instance;
	}

	public AS400 getConnection(String serverName, String userName,
			String password, String minSize, String maxSize, String maintance,
			String maxInactivity, String timeInterval, String maxLifetime,
			String maxUsecount, String maxUsetime, String pretestConnection)
			throws ConnectionPoolException {

		as400ConnPool.setMaxConnections(Integer.parseInt(maxSize));
		as400ConnPool.setCleanupInterval(Long.parseLong(timeInterval));
		as400ConnPool.setMaxInactivity(Long.parseLong(maxInactivity));
		as400ConnPool.setMaxLifetime(Long.parseLong(maxLifetime));
		as400ConnPool.setMaxUseCount(Integer.parseInt(maxUsecount));
		as400ConnPool.setRunMaintenance(maintance.equals("true"));
		as400ConnPool.setMaxUseTime(Long.parseLong(maxUsetime));
		as400ConnPool.setPretestConnections(pretestConnection.equals("true"));
		
		int minConn = Integer.parseInt(minSize);
        if(minConn > 0){
        		as400ConnPool.fill(serverName, userName, password, AS400_SERVICE,
				Integer.parseInt(minSize));
        }
		return as400ConnPool.getConnection(serverName, userName, password);
	}

	public void returnConnection(AS400 connection) {
		as400ConnPool.returnConnectionToPool(connection);
	}

	@Override
	protected void finalize() throws Throwable {
		as400ConnPool.close();
		super.finalize();
	}

}
