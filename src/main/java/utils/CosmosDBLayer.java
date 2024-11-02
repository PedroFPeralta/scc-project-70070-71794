package utils;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class CosmosDBLayer {
	private static final String CONNECTION_URL = "https://cosmos70070northeurope.documents.azure.com:443/"; // replace with your own
	private static final String DB_KEY = "u9d2bwdJtw12C6mRwtjvWBw0XeSxb41NqUPXON6mKir7aq4phn2ukBshRHn2dF2i5GQoTYhT8KjAACDb0j612g==";
	private static final String DB_NAME = "cosmosdb70070";
	private static final String CONTAINER = "users";
	
	private static CosmosDBLayer instance;

	public static synchronized CosmosDBLayer getInstance() {
		if( instance != null)
			return instance;

		CosmosClient client = new CosmosClientBuilder()
		         .endpoint(CONNECTION_URL)
		         .key(DB_KEY)
		         //.directMode()
		         .gatewayMode()		
		         // replace by .directMode() for better performance
		         .consistencyLevel(ConsistencyLevel.SESSION)
		         .connectionSharingAcrossClientsEnabled(true)
		         .contentResponseOnWriteEnabled(true)
		         .buildClient();
		instance = new CosmosDBLayer( client);
		return instance;
		
	}
	
	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer container;
	
	public CosmosDBLayer(CosmosClient client) {
		this.client = client;
	}
	
	private synchronized void init() {
		if( db != null)
			return;
		db = client.getDatabase(DB_NAME);
		container = db.getContainer(CONTAINER);
	}

	public void close() {
		client.close();
	}
	
	public <T> Result<T> getOne(String id, Class<T> clazz) {
		return tryCatch( () -> container.readItem(id, new PartitionKey(id), clazz).getItem());
	}
	
	public <T> Result<?> deleteOne(T obj) {
		return tryCatch( () -> container.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
	}
	
	public <T> Result<T> updateOne(T obj) {
		return tryCatch( () -> container.upsertItem(obj).getItem());
	}
	
	public <T> Result<T> insertOne( T obj) {
		return tryCatch( () -> container.createItem(obj).getItem());
	}
	
	public <T> List<T> sql(Class<T> clazz, String queryStr) {
		CosmosPagedIterable<T> res = container.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
		return res.stream().collect(Collectors.toList());
	}
	
	<T> Result<T> tryCatch( Supplier<T> supplierFunc) {
		try {
			init();
			return Result.ok(supplierFunc.get());			
		} catch( CosmosException ce ) {
			//ce.printStackTrace();
			return Result.error ( errorCodeFromStatus(ce.getStatusCode() ));		
		} catch( Exception x ) {
			x.printStackTrace();
			return Result.error( ErrorCode.INTERNAL_ERROR);						
		}
	}
	
	static ErrorCode errorCodeFromStatus( int status ) {
		return switch( status ) {
		case 200 -> ErrorCode.OK;
		case 404 -> ErrorCode.NOT_FOUND;
		case 409 -> ErrorCode.CONFLICT;
		default -> ErrorCode.INTERNAL_ERROR;
		};
	}
}
