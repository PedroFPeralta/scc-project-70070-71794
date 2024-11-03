package utils;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class CosmosDBLayer {
	private static final String CONNECTION_URL = "https://cosmos70070northeurope.documents.azure.com:443/"; // replace with your own
	private static final String DB_KEY = "u9d2bwdJtw12C6mRwtjvWBw0XeSxb41NqUPXON6mKir7aq4phn2ukBshRHn2dF2i5GQoTYhT8KjAACDb0j612g==";
	private static final String DB_NAME = "cosmosdb70070";
	
	private static CosmosDBLayer instance;
	private CosmosClient client;
	private CosmosDatabase cosmosDatabase;


	private CosmosDBLayer() {
		try {
			client = new CosmosClientBuilder()
					.endpoint(CONNECTION_URL)
					.key(DB_KEY)
					//.directMode()
					.gatewayMode()
					.consistencyLevel(ConsistencyLevel.SESSION)
					.connectionSharingAcrossClientsEnabled(true)
					.contentResponseOnWriteEnabled(true)
					.buildClient();

			cosmosDatabase = client.getDatabase(DB_NAME);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	synchronized public static CosmosDBLayer getInstance() {
		if (instance == null)
			instance = new CosmosDBLayer();
		return instance;
	}


	public void close() {
		client.close();
	}
	
	public <T> Result<T> getOne(String id, Class<T> clazz) {
		CosmosContainer auxContainer = getContainer(clazz);
		return tryCatch( () -> auxContainer.readItem(id, new PartitionKey(id), clazz).getItem());
	}
	
	public <T> Result<?> deleteOne(T obj) {
		CosmosContainer cosmosContainer = getContainer(obj.getClass());
		return tryCatch( () -> cosmosContainer.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
	}
	
	public <T> Result<T> updateOne(T obj) {
		CosmosContainer cosmosContainer = getContainer(obj.getClass());

		return tryCatch( () -> cosmosContainer.upsertItem(obj).getItem());
	}
	
	public <T> Result<T> insertOne( T obj) {
		try {
			CosmosContainer cosmosContainer = getContainer(obj.getClass());
			cosmosContainer.createItem(obj);
			return Result.ok();
		} catch (Exception e) {
			e.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}
	}
	
	public <T> List<T> sql(Class<T> clazz, String queryStr) {
		CosmosContainer cosmosContainer = getContainer(clazz);
		CosmosPagedIterable<T> res = cosmosContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
		return res.stream().collect(Collectors.toList());
	}

	public <T> Result<T> execute(Consumer<CosmosDatabase> proc) {
		try {
			proc.accept(cosmosDatabase);
			return Result.ok();
		} catch (Exception e) {
			e.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}
	}

	public <T> Result<T> execute(Function<CosmosDatabase, Result<T>> func) {
		try {
			return func.apply(cosmosDatabase);
		} catch (Exception e) {
			e.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}
	}
	
	<T> Result<T> tryCatch( Supplier<T> supplierFunc) {
		try {
			//init();
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

	private CosmosContainer getContainer(Class<?> clazz) {
		String containerName;

		switch (clazz.getSimpleName()) {
			case "User" -> containerName = "users";
			case "Short" -> containerName = "shorts";
			default -> throw new IllegalArgumentException("Unknown class: " + clazz.getName());
		}

		return cosmosDatabase.getContainer(containerName);
	}
}
