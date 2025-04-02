package grpc.server.services;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class FileStore {
    private final String fileFolder;

    public FileStore(String fileFolder) {
        this.fileFolder = fileFolder;
    }

    public String saveFile(String fileName, ByteArrayOutputStream fileData) throws IOException {
        String filePath = fileFolder + fileName;
        FileOutputStream fileOutputStream = new FileOutputStream(filePath);
        fileData.writeTo(fileOutputStream);
        fileOutputStream.close();

        PrismGrpcLogger.getLogger().info("File saved to: " + filePath);
        return filePath;
    }

}
