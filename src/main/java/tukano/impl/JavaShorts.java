package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static utils.DB.getOne;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import utils.JSON;
import utils.RedisCache;
import redis.clients.jedis.Jedis;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	
	private static Shorts instance;

	
	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}
	
	private JavaShorts() {}
	
	private HashMap<String, Short> shortsDB = new HashMap<>();
	private HashMap<String, List<String>> userShortsDB = new HashMap<>();
	private HashMap<String, Set<String>> followersDB = new HashMap<>();
	private HashMap<String, Set<String>> likesDB = new HashMap<>();
	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {
			
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId); 
			var shrt = new Short(shortId, userId, blobUrl);

			shortsDB.put(shortId, shrt);
			userShortsDB.computeIfAbsent(userId, k -> new ArrayList<>()).add(shortId);
			
			
			return Result.ok(shrt);

		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if ( shortId == null )
			return error(BAD_REQUEST);

		var shrt = shortsDB.get(shortId);
		if (shrt == null)
			return error(Result.ErrorCode.NOT_FOUND);
		var likes = likesDB.getOrDefault(shortId, Collections.emptySet()).size();

		return Result.ok(shrt.copyWithLikes_And_Token(likes));	
		
	}

	
	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));
		
		return errorOrResult( getShort(shortId), shrt -> {
			
			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
				shortsDB.remove(shortId);
				userShortsDB.getOrDefault(user.getUserId(), new ArrayList<>()).remove(shortId);
				likesDB.remove(shortId);
				return Result.ok();

			});	
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		return errorOrValue(okUser(userId), user -> new ArrayList<>(userShortsDB.getOrDefault(userId, Collections.emptyList())));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

		
		return errorOrResult(okUser(userId1, password), user -> {
			followersDB.computeIfAbsent(userId2, f -> new HashSet<>());

			if (isFollowing) 
				followersDB.get(userId2).add(userId1);
			else
				followersDB.get(userId2).remove(userId1);
			return Result.ok();		
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		return errorOrValue(okUser(userId, password), user -> new ArrayList<>(followersDB.getOrDefault(userId, Collections.emptySet())));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		return errorOrResult(getShort(shortId), shrt -> {
			likesDB.computeIfAbsent(shortId, l -> new HashSet<>());
			if (isLiked)
				likesDB.get(shortId).add(userId);
			else 
				likesDB.get(shortId).remove(userId);
			return Result.ok();		
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {

			return Result.ok(new ArrayList<>(likesDB.getOrDefault(shortId, Collections.emptySet())));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		return errorOrValue(okUser(userId, password), user -> {
			Set<String> feed = new HashSet<>(userShortsDB.getOrDefault(userId, Collections.emptyList()));
			followersDB.getOrDefault(userId, Collections.emptySet()).forEach(followee -> 
				feed.addAll(userShortsDB.getOrDefault(followee, Collections.emptyList())));
			return new ArrayList<>(feed);	

		});	
	}
		
	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}
	
	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);

		return errorOrResult(okUser(userId, password), user -> {
			List<String> deleteShorts = userShortsDB.getOrDefault(userId, new ArrayList<>());
			for (String shortId : deleteShorts){
				shortsDB.remove(shortId);
				likesDB.remove(shortId);
			}
			
			userShortsDB.remove(userId);
			for (String followee : followersDB.keySet())
				followersDB.get(followee).remove(userId);
			followersDB.remove(userId);
			
			for (String shortId : likesDB.keySet())
				likesDB.get(shortId).remove(userId);
			return Result.ok();
		});
	}

	private Result<Void> deleteCascade(String deleteFollowsQuery) {
		List<Short> followsList = DB.sql(deleteFollowsQuery, Short.class);
		for (Short follow : followsList) {
			Result<Short> deleteFollowRes = DB.deleteOne(follow);
			if (deleteFollowRes.error() != null) {
				return Result.error(deleteFollowRes.error());
			}
		}
		return null;
	}

}
