package com.labizy.services.login.dao.manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.labizy.services.login.dao.util.DatabaseConnection;
import com.labizy.services.login.exceptions.DataIntegrityException;
import com.labizy.services.login.exceptions.DataNotFoundException;
import com.labizy.services.login.exceptions.DatabaseConnectionException;
import com.labizy.services.login.exceptions.QueryExecutionException;
import com.labizy.services.login.exceptions.UniqueKeyViolationException;
import com.labizy.services.login.utils.CommonUtils;
import com.labizy.services.login.utils.EncryptionDecryptionUtils;

public class UserLoginDaoManager {
	private static Logger logger = LoggerFactory.getLogger("com.labizy.services.login.AppLogger");
	
	private CommonUtils commonUtils;
	private String databaseName;
	private UserProfileDaoManager userProfileDaoManager;
	
	public void setCommonUtils(CommonUtils commonUtils) {
		this.commonUtils = commonUtils;
	}
	
	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public void setUserProfileDaoManager(UserProfileDaoManager userProfileDaoManager) {
		this.userProfileDaoManager = userProfileDaoManager;
	}

	private void insertOauthRecords(String clientId, boolean isGuest, String oauthToken, String oauthTokenType, Connection connection)  
			throws DataNotFoundException, QueryExecutionException, DatabaseConnectionException {

		if(logger.isDebugEnabled()){
			logger.debug("Inside {}", "UserLoginDaoManager.deleteOauthRecords(String)");
		}
		
		boolean isNewConnection = (connection == null);
		PreparedStatement preparedStatement = null;
		
		try{
			if(isNewConnection){
				connection = DatabaseConnection.getDatabaseConnection(databaseName);
				connection.setAutoCommit(false);
			}
			
			java.sql.Timestamp currentTimestamp = commonUtils.getCurrentDateTimeAsSqlTimestamp();
			
			String sqlQuery = "INSERT INTO user_oauth_tb (user_id, oauth_token, oauth_token_issued_on, is_guest, token_type) "
									+ "VALUES (?, ?, ?, ?, ?)";

			preparedStatement = connection.prepareStatement(sqlQuery);
			
			preparedStatement.setNString(1, clientId);
			preparedStatement.setNString(2, oauthToken);
			preparedStatement.setTimestamp(3, currentTimestamp);
			preparedStatement.setBoolean(4, isGuest);
			preparedStatement.setNString(5, oauthTokenType);
			
			preparedStatement.execute();
			preparedStatement.close();
			
			sqlQuery = "INSERT INTO user_activity_tb (user_id, last_logged_in, deleted_on, suspended_on, locked_on, comments) "
							+ "VALUES (?, ?, ?, ?, ?, ?)";

			preparedStatement = connection.prepareStatement(sqlQuery);

			preparedStatement.setNString(1, clientId);
			preparedStatement.setTimestamp(2, currentTimestamp);
			preparedStatement.setNull(3, java.sql.Types.DATE);
			preparedStatement.setNull(4, java.sql.Types.DATE);
			preparedStatement.setNull(5, java.sql.Types.DATE);
			preparedStatement.setNString(6, null);
			
			preparedStatement.execute();
			
			if(isNewConnection){
				connection.commit();
			}
		}catch(SQLException e){
			if(isNewConnection){
				try{
					connection.rollback();
				} catch (SQLException e1) {
					logger.warn(e1.getMessage());
				}
				throw new QueryExecutionException(e);
			}
		}finally{
			try {
				preparedStatement.close();
				if(isNewConnection){
					connection.close();
				}
			} catch (SQLException e) {
				logger.warn(e.getMessage());
			}
			preparedStatement = null;
			if(isNewConnection){
				connection = null;
			}
		}
	}

	private void deleteOauthRecords(String clientId, Connection connection)  
						throws DataNotFoundException, QueryExecutionException, DatabaseConnectionException {

		if(logger.isDebugEnabled()){
			logger.debug("Inside {}", "UserLoginDaoManager.deleteOauthRecords(String)");
		}
		
		boolean isNewConnection = (connection == null);
		PreparedStatement preparedStatement = null;
		
		try{
			if(isNewConnection){
				connection = DatabaseConnection.getDatabaseConnection(databaseName);
				connection.setAutoCommit(false);
			}
			
			String sqlQuery = "DELETE FROM user_activity_tb WHERE user_id = ?";
			
			preparedStatement = connection.prepareStatement(sqlQuery);
			
			preparedStatement.setNString(1, clientId);
			
			preparedStatement.execute();
			preparedStatement.close();
			
			sqlQuery = "DELETE FROM user_oauth_tb WHERE user_id = ?";
			preparedStatement = connection.prepareStatement(sqlQuery);
			
			preparedStatement.setNString(1, clientId);
			
			preparedStatement.execute();
			
			if(isNewConnection){
				connection.commit();
			}
		}catch(SQLException e){
			if(isNewConnection){
				try{
					connection.rollback();
				} catch (SQLException e1) {
					logger.warn(e1.getMessage());
				}
				throw new QueryExecutionException(e);
			}
		}finally{
			try {
				preparedStatement.close();
				if(isNewConnection){
					connection.close();
				}
			} catch (SQLException e) {
				logger.warn(e.getMessage());
			}
			preparedStatement = null;
			if(isNewConnection){
				connection = null;
			}
		}
	}
	
	public Map<String, String> validateToken(String clientId, String oauthToken, boolean applyValidationRules) 
			throws DataNotFoundException, QueryExecutionException, DatabaseConnectionException {

		if(logger.isDebugEnabled()){
			logger.debug("Inside {}", "UserLoginDaoManager.validateToken(String, String)");
		}
		
		Map<String, String> result = null;
		Connection connection = DatabaseConnection.getDatabaseConnection(databaseName);
		PreparedStatement preparedStatement = null;
		
		try{
			String sqlQuery = null;
			
			if(StringUtils.isEmpty(clientId)){
				sqlQuery = "SELECT user_tb.user_id AS user_id, oauth_token, oauth_token_issued_on, "
									+ "is_guest_user, token_type, email_id, password, status, last_logged_in, "
									+ "suspended_on, locked_on, comments, is_real_user, is_internal_user "
									+ "FROM user_tb, user_oauth_tb, user_activity_tb "
									+ "WHERE user_tb.user_id = user_oauth_tb.user_id "
									+ "AND user_tb.user_id = user_activity_tb.user_id "
									+ "AND (STATUS IS NULL OR STATUS <> 'DELETED') "
									+ "AND oauth_token = ? ";
			}else{
				sqlQuery = "SELECT user_tb.user_id AS user_id, oauth_token, oauth_token_issued_on, "
									+ "is_guest_user, token_type, email_id, password, status, last_logged_in, "
									+ "suspended_on, locked_on, comments, is_real_user, is_internal_user "
									+ "FROM user_tb, user_oauth_tb, user_activity_tb "
									+ "WHERE user_tb.user_id = user_oauth_tb.user_id "
									+ "AND user_tb.user_id = user_activity_tb.user_id "
									+ "AND (STATUS IS NULL OR STATUS <> 'DELETED') "
									+ "AND user_tb.user_id = ? ";
			}
			
			preparedStatement = connection.prepareStatement(sqlQuery);

			if(StringUtils.isEmpty(clientId)){
				preparedStatement.setNString(1, oauthToken);
			}else{
				preparedStatement.setNString(1, clientId);
			}	
			
			ResultSet rs = preparedStatement.executeQuery();
			
			result = new HashMap<String, String>();
			while(rs.next()){
				clientId = rs.getNString("user_id");
				result.put("clientId", clientId);
				result.put("userId", clientId);
				
				oauthToken = rs.getNString("oauth_token");
				result.put("oauthToken", oauthToken);
				
				String tokenType = rs.getNString("token_type");
				result.put("tokenType", tokenType);

				java.sql.Timestamp oauthTokenIssuedOnTS = rs.getTimestamp("oauth_token_issued_on");
				result.put("oauthTokenIssuedOn", commonUtils.getTimestampAsDateString(oauthTokenIssuedOnTS, false));
				
				boolean isGuestUser = rs.getBoolean("is_guest_user");
				result.put("isGuestUser", Boolean.toString(isGuestUser));
				
				String emailId = rs.getNString("email_id");
				result.put("emailId", emailId);
				
				String password = rs.getNString("password");
				result.put("password", password);

				String status = rs.getNString("status");
				result.put("status", status);

				String comments = rs.getNString("comments");
				result.put("comments", comments);

				java.sql.Timestamp lastLoggedInTS = rs.getTimestamp("last_logged_in");
				result.put("lastLoggedIn", commonUtils.getTimestampAsDateString(lastLoggedInTS, false));
				
				java.sql.Timestamp suspendedOnTS = rs.getTimestamp("suspended_on");
				result.put("suspendedOn", commonUtils.getTimestampAsDateString(suspendedOnTS, false));
				
				java.sql.Timestamp lockedOnTS = rs.getTimestamp("locked_on");
				result.put("lockedOn", commonUtils.getTimestampAsDateString(lockedOnTS, false));
				
				boolean isRealUser = rs.getBoolean("is_real_user");
				result.put("isRealUser", Boolean.toString(isRealUser));

				boolean isInternalUser = rs.getBoolean("is_internal_user");
				result.put("isInternalUser", Boolean.toString(isInternalUser));
				
				break;
			}
			
			if(result.isEmpty()){
				throw new DataNotFoundException("User token is not valid/expired. The user needs to login/re-login..");
			}
			
			if(applyValidationRules){
				//Some futuristic work here.
			}
		}catch (SQLException e){
			logger.error(e.getMessage());
			throw new QueryExecutionException(e);
		}finally{
			try {
				preparedStatement.close();
				connection.close();
			} catch (SQLException e) {
				logger.warn(e.getMessage());
			}
			preparedStatement = null;
			connection = null;
		}
		
		return result; 
	}
	
	public Map<String, String> issueToken(String emailId, String password)
			throws UniqueKeyViolationException, DataIntegrityException, QueryExecutionException, DatabaseConnectionException {
		
		if(logger.isDebugEnabled()){
			logger.debug("Inside {}", "UserProfileDaoManager.issueToken(String, String)");
		}
		
		Map<String, String> oauthResultMap = null;
		
		String userId = null;
		boolean isRealUser = false;
		boolean isGuestUser = false;
		
		try{
			Map<String, String> result = userProfileDaoManager.getUserId(emailId, password);
			userId = result.get("userId");
			isRealUser = Boolean.parseBoolean(result.get("isRealUser"));
			isGuestUser = Boolean.parseBoolean(result.get("isGuestUser"));
		}catch(Exception e){
			logger.error(e.getMessage());
			throw new DataIntegrityException(e);
		}
		
		Connection connection = DatabaseConnection.getDatabaseConnection(databaseName);
		
		try{
			connection.setAutoCommit(false);
			deleteOauthRecords(userId, connection);
			
			String oauthToken = commonUtils.getUniqueGeneratedId("OAUTH", ((isRealUser) ? "U" : "S"));
			String oauthTokenType = (isRealUser) ? "user" : "service";
			insertOauthRecords(userId, isGuestUser, oauthToken, oauthTokenType, connection);
			
			connection.commit();
		}catch(DataNotFoundException e){
			throw new QueryExecutionException(e);
		}catch(SQLException e){
			try{
				userId = null;
				connection.rollback();
			} catch (SQLException e1) {
				logger.warn(e1.getMessage());
			}
			throw new QueryExecutionException(e);
		}finally{
			try {
				connection.setAutoCommit(true);
				connection.close();
			} catch (SQLException e) {
				logger.warn(e.getMessage());
			}
			connection = null;
		}
		
		try {
			oauthResultMap = validateToken(userId, null, false);
		} catch (DataNotFoundException e) {
			logger.error(e.getMessage());
			throw new DataIntegrityException(e);
		}
		
		return oauthResultMap;
	}
	
	public void expireToken(String clientId, String oauthToken) 
			throws DataNotFoundException, QueryExecutionException, DatabaseConnectionException{

		if(logger.isDebugEnabled()){
			logger.debug("Inside {}", "UserProfileDaoManager.expireToken(String, String, tokenType)");
		}
		
		Connection connection = DatabaseConnection.getDatabaseConnection(databaseName);
		Statement statement = null;
		try{
			connection.setAutoCommit(false);
			Map<String, String> tokenMap = validateToken(clientId, oauthToken, false);
			
			if(StringUtils.isEmpty(clientId)){
				clientId = tokenMap.get("clientId");
			}
			deleteOauthRecords(clientId, connection);
			connection.commit();
		}catch (SQLException e){
			try {
				connection.rollback();
			} catch (SQLException e1) {
				logger.warn(e1.getMessage());
			}
			logger.error(e.getMessage());
			throw new QueryExecutionException(e);
		}finally{
			try {
				connection.setAutoCommit(true);
				statement.close();
				connection.close();
			} catch (SQLException e) {
				logger.warn(e.getMessage());
			}
			statement = null;
			connection = null;
		}
	}

	public static void main(String[] args) throws Exception{
		System.out.println("Let's start..");
		UserLoginDaoManager daoMgr = new UserLoginDaoManager();
		
		String databaseName = "labizy_user_db";
		UserProfileDaoManager userProfileDaoMgr = new UserProfileDaoManager();
		CommonUtils commonUtils = new CommonUtils();
		EncryptionDecryptionUtils encryptionDecryptionUtils = new EncryptionDecryptionUtils();
		
		daoMgr.setCommonUtils(commonUtils);
		daoMgr.setDatabaseName(databaseName);
		
		userProfileDaoMgr.setCommonUtils(commonUtils);
		userProfileDaoMgr.setDatabaseName(databaseName);
		userProfileDaoMgr.setEncryptionDecryptionUtils(encryptionDecryptionUtils);
		daoMgr.setUserProfileDaoManager(userProfileDaoMgr);
		
		//String emailId = "prashant5@labizy.com";
		String emailId = "labizy@labizy.com";
		String base64EncodedPassword = "JDNjcjN0"; //String password = "$3cr3t";
		String userId = null;
		String clientId = null;
		String status = null;
		String oauthToken = null;
		String oauthTokenType = null;
		Map<String, String> result = null;
		
		System.out.println("Calling issueToken(" + emailId + ")");
		try{
			result = daoMgr.issueToken(emailId, base64EncodedPassword);
			userId = result.get("userId");
			clientId = result.get("clientId");
			oauthToken = result.get("oauthToken");
			oauthTokenType = result.get("tokenType");
			
			System.out.println("Oauth Token : " + result);
		}catch(Exception e){
			System.err.println(e);
		}
	
		System.out.println("Calling expireToken(null, " + oauthToken + ")");
		try{
			daoMgr.expireToken(null, oauthToken);
			result = daoMgr.validateToken(clientId, null, false);
			System.out.println("Oauth Token : " + result);
		}catch(Exception e){
			result = null;
			System.out.println("Oauth Token : " + result);
		}

		System.out.println("Ok.. That's it....");
	}
}