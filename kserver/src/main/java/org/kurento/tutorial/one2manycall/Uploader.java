package org.kurento.tutorial.one2manycall;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.AmazonServiceException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.File;
import java.net.*;

public class Uploader extends Thread {

    private final String _sPath;
    private final String _sEsServerPath;
    private final String _sUser;
    private final String _sMatch;
    Uploader(String sPath, String sEsServerPath)
    {
        super("upload thread");

        _sPath = sPath.substring(7);//remove file://;
        _sEsServerPath = sEsServerPath;
        _sUser = "";
        _sMatch = "";
        start();
    }

    @Override
    public void run() {
        super.run();

        final AmazonS3 s3 = new AmazonS3Client();
        try {
            Path p = Paths.get(_sPath);
            String file = p.getFileName().toString();
            //String filePath = p.toAbsolutePath().toString();
            s3.putObject("electronicstars-bucket", file, new File(_sPath));

            URI uri = new URI(_sEsServerPath);
            ChatClientEndpoint client = new ChatClientEndpoint(uri);
            String sMessage = "{\"type\":\"fileUploaded\", \"filename\":\""+ file +"\", \"user\":\"" + _sUser +"\", \"match\":\"" + _sMatch + "\"}";
            client.sendMessage(sMessage);

        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        } catch (URISyntaxException e) {
            System.exit(1);
        }



        System.out.println("Upload complete" );


    }

}


