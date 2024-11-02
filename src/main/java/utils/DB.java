package utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.azure.cosmos.CosmosDatabase;
import org.hibernate.Session;

import tukano.api.Result;

public class DB {

	public static <T> List<T> sql(String query, Class<T> clazz) {
		return CosmosDBLayer.getInstance().sql(clazz, query);
	}
	
	public static <T> List<T> sql(Class<T> clazz, String fmt, Object ... args) {
		return CosmosDBLayer.getInstance().sql(clazz, String.format(fmt, args));
	}
	
	public static <T> Result<T> getOne(String id, Class<T> clazz) {
		return CosmosDBLayer.getInstance().getOne(id, clazz);
	}
	
	public static <T> Result<T> deleteOne(T obj) {
		return (Result<T>) CosmosDBLayer.getInstance().deleteOne(obj);
	}
	
	public static <T> Result<T> updateOne(T obj) {
		return CosmosDBLayer.getInstance().updateOne(obj);
	}
	
	public static <T> Result<T> insertOne( T obj) {
		return Result.errorOrValue(CosmosDBLayer.getInstance().insertOne(obj), obj);
	}
	
	public static <T> Result<T> transaction( Consumer<CosmosDatabase> c) {
		return CosmosDBLayer.getInstance().execute( c::accept );
	}
	
	public static <T> Result<T> transaction( Function<CosmosDatabase, Result<T>> func) {
		return CosmosDBLayer.getInstance().execute( func );
	}
}
