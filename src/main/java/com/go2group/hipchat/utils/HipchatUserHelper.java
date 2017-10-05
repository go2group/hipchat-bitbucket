package com.go2group.hipchat.utils;

import java.util.HashMap;
import java.util.Map;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.go2group.hipchat.components.HipChatProxyClient;
import com.go2group.hipchat.components.HipChatProxyClient.JSONString;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class HipchatUserHelper {

	private final HipChatProxyClient hipChatProxyClient;
	private final AuthenticationContext authenticationContext;
	
	public HipchatUserHelper(HipChatProxyClient hipChatProxyClient, AuthenticationContext authenticationContext) {

		this.hipChatProxyClient = hipChatProxyClient;
		this.authenticationContext = authenticationContext;
	}
	
	public String getHipchatUserIdForLoggedInUser(){
		JSONString jsonString = hipChatProxyClient.getUsers();

		//Parse it
		JsonObject jsonObject = new JsonParser().parse(jsonString.toString()).getAsJsonObject();
		
		//Take array of users
		JsonArray jsonArray = jsonObject.getAsJsonArray("users");
		
		//Collect the users in this map
		Map<String,String> emailUserIdMap = new HashMap<String,String>(); 
		
		//Iterate them
		for (int i=0; i<jsonArray.size(); i++){
			JsonObject jsonUserObject = jsonArray.get(i).getAsJsonObject();
			emailUserIdMap.put(jsonUserObject.get("email").getAsString(), jsonUserObject.get("user_id").getAsString());
		}
		
		//Now take the logged in user in stash
		ApplicationUser user = authenticationContext.getCurrentUser();
		
		if (user != null){ //Safe check
			String mappedId = emailUserIdMap.get(user.getEmailAddress());
			if (mappedId != null){
				return mappedId;
			}else{
				return "-1";
			}
		}else{
			return "-1";
		}
	}
}
