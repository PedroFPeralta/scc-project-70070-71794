package tukano.impl.storage;


import java.util.Base64;
import java.util.function.Consumer;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;

import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;


import tukano.api.Result;
import utils.Hash;

public class BlobStorageImpl implements BlobStorage{

    //TODO: check if this contant need to be from a external file
    private static final String BLOBS_CONTAINER_NAME = "shorts";

    //TODO: pass this secret to external file
    String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=sto70070northeurope;AccountKey=i4CoNkgZ+TCU7FoVCTwuv9LDbhmN5oO0FBOLBPdUs3oYT1K4sMpJboZmR1xyZZ6Dx1TVNWdE2lOF+AStK0X8Nw==;EndpointSuffix=core.windows.net";


    public BlobStorageImpl() {

    }

    @Override
    public Result<Void> write(String path, byte[] bytes ){
        if (path == null || path.length() == 0 || bytes == null) {
            return error(BAD_REQUEST);
        }

        try {
        BinaryData data = BinaryData.fromBytes(bytes);

        BlobContainerClient containerClient = new BlobContainerClientBuilder()
														.connectionString(storageConnectionString)
														.containerName(BLOBS_CONTAINER_NAME)
														.buildClient();

        BlobClient blob = containerClient.getBlobClient(path);

        // Validate if blob exists in Blob storage
        if (blob.exists()) {
            BlobProperties properties = blob.getProperties();

            byte[] hashBytes = Hash.sha256(bytes);
            String newContentHash = Base64.getEncoder().encodeToString(hashBytes);

            // Verify if both have the same content hash
            String existingContentHash = properties.getContentMd5() != null
                        ? Base64.getEncoder().encodeToString(properties.getContentMd5())
                        : "";

            if (newContentHash.equals(existingContentHash)) {
                System.out.println("Blob already exists with the same content.");
                return error(CONFLICT);
            } else {
                return error(CONFLICT); // TODO: verify with teacher if we can create another CONSTANT to represent a conflit with same name but diferent content
            }
        }

        blob.upload(data);

        System.out.println("Blob upload successful");

        return ok();
        } 
        catch(NullPointerException e){
            e.printStackTrace();
            return error(NOT_FOUND);
        } 
          catch (BlobStorageException e) {
            e.printStackTrace();
            return error(NOT_FOUND);
        } 
          catch( Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }
		
	public Result<Void> delete(String path){
        throw new UnsupportedOperationException();
    }
	
	public Result<byte[]> read(String path){
        throw new UnsupportedOperationException();
    }

	public Result<Void> read(String path, Consumer<byte[]> sink){
        throw new UnsupportedOperationException();
    }
    
}
