package org.kurento.tutorial.one2manycall;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.AmazonServiceException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.File;

public class Uploader extends Thread {

    private final String _sPath;
    Uploader(String sPath)
    {
        super("upload thread");

        _sPath = sPath.substring(7);//remove file://;
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
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        }

        System.out.println("Upload complete" );
    }

}


