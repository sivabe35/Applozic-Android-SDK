package com.applozic.mobicomkit.api.attachment;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.applozic.mobicomkit.api.HttpRequestUtils;
import com.applozic.mobicomkit.api.MobiComKitClientService;

import com.applozic.mobicomkit.api.conversation.Message;
import com.applozic.mobicomkit.api.conversation.database.MessageDatabaseService;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.commons.image.ImageUtils;
import com.applozic.mobicommons.file.FileUtils;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by devashish on 26/12/14.
 */
public class FileClientService extends MobiComKitClientService {

    //Todo: Make the base folder configurable using either strings.xml or properties file
    public static final String MOBI_COM_IMAGES_FOLDER = "/image";
    public static final String MOBI_COM_VIDEOS_FOLDER = "/video";
    public static final String MOBI_COM_CONTACT_FOLDER= "/contact";
    public static final String MOBI_COM_OTHER_FILES_FOLDER = "/other";
    public static final String MOBI_COM_THUMBNAIL_SUFIX = "/.Thumbnail";
    public static final String FILE_UPLOAD_URL = "/rest/ws/aws/file/url";
    public static final String IMAGE_DIR = "image";
    private static final String TAG = "FileClientService";
    private HttpRequestUtils httpRequestUtils;
    private static final String MAIN_FOLDER_META_DATA = "main_folder_name";

    public FileClientService(Context context) {
        super(context);
        this.httpRequestUtils = new HttpRequestUtils(context);
    }

    public String getFileUploadUrl() {
        return FILE_BASE_URL + FILE_UPLOAD_URL;
    }

    public static File getFilePath(String fileName, Context context, String contentType, boolean isThumbnail) {
        File filePath;
        File dir;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            String folder = "/" + Utils.getMetaDataValue(context, MAIN_FOLDER_META_DATA) + MOBI_COM_OTHER_FILES_FOLDER;

            if (contentType.startsWith("image")) {
                folder = "/" + Utils.getMetaDataValue(context, MAIN_FOLDER_META_DATA) + MOBI_COM_IMAGES_FOLDER;
            } else if (contentType.startsWith("video")) {
                folder = "/" + Utils.getMetaDataValue(context, MAIN_FOLDER_META_DATA) + MOBI_COM_VIDEOS_FOLDER;
            } else if(contentType.equalsIgnoreCase("text/x-vCard")){
                folder = "/" + Utils.getMetaDataValue(context, MAIN_FOLDER_META_DATA) + MOBI_COM_CONTACT_FOLDER;
            }
            if (isThumbnail) {
                folder = folder + MOBI_COM_THUMBNAIL_SUFIX;
            }
            dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + folder);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } else {
            ContextWrapper cw = new ContextWrapper(context);
            // path to /data/data/yourapp/app_data/imageDir
            dir = cw.getDir(IMAGE_DIR, Context.MODE_PRIVATE);
        }
        // Create image name
        //String extention = "." + contentType.substring(contentType.indexOf("/") + 1);
        filePath = new File(dir, fileName);
        return filePath;
    }

    public static String saveImageToInternalStorage(Bitmap bitmapImage, String fileName, Context context, String contentType) {
        File filePath = getFilePath(fileName, context, contentType, true);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filePath);
            // Use the compress method on the BitMap object to write image to the OutputStream
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return filePath.getAbsolutePath();
    }

    public static File getFilePath(String fileName, Context context, String contentType) {
        return getFilePath(fileName, context, contentType, false);
    }

    public Bitmap loadThumbnailImage(Context context, FileMeta fileMeta, int reqWidth, int reqHeight) {
        try {
            Bitmap attachedImage = null;
            String thumbnailUrl = fileMeta.getThumbnailUrl();
            String contentType = fileMeta.getContentType();
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            // Todo get the file format from server and append
            String imageName = fileMeta.getBlobKeyString() + "." + FileUtils.getFileFormat(fileMeta.getName());
            String imageLocalPath = getFilePath(imageName, context, fileMeta.getContentType(), true).getAbsolutePath();
            if (imageLocalPath != null) {
                try {
                    attachedImage = BitmapFactory.decodeFile(imageLocalPath);
                } catch (Exception ex) {
                    Log.e(TAG, "File not found on local storage: " + ex.getMessage());
                }
            }
            if (attachedImage == null) {
                HttpURLConnection connection = new MobiComKitClientService(context).openHttpConnection(thumbnailUrl);
                if (connection.getResponseCode() == 200) {
                    // attachedImage = BitmapFactory.decodeStream(connection.getInputStream(),null,options);
                    attachedImage = BitmapFactory.decodeStream(connection.getInputStream());
                    imageLocalPath = saveImageToInternalStorage(attachedImage, imageName, context, contentType);

                } else {
                    Log.w(TAG, "Download is failed response code is ...." + connection.getResponseCode());
                    return null;
                }
            }
            // Calculate inSampleSize
            options.inSampleSize = ImageUtils.calculateInSampleSize(options, 200, reqHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            attachedImage = BitmapFactory.decodeFile(imageLocalPath, options);
            return attachedImage;
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            Log.e(TAG, "File not found on server: " + ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "Exception fetching file from server: " + ex.getMessage());
        }

        return null;
    }

    /**
     *
     * @param message
     */

    public void loadContactsvCard(Message message) {
        File file = null;
        try {
            InputStream inputStream = null;
            FileMeta fileMeta = message.getFileMetas();
            String contentType = fileMeta.getContentType();
            HttpURLConnection connection;
            String fileName = fileMeta.getName();
            file = FileClientService.getFilePath(fileName, context, contentType);
            if (!file.exists()) {
                connection = new MobiComKitClientService(context).openHttpConnection(new MobiComKitClientService(context).getFileUrl() + fileMeta.getBlobKeyString());
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                } else {
                    //TODO: Error Handling...
                    Log.i(TAG, "Got Error response while uploading file : " + connection.getResponseCode());
                    return;
                }

                OutputStream output = new FileOutputStream(file);
                byte data[] = new byte[1024];
                int count=0;
                while ((count = inputStream.read(data)) != -1) {
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                inputStream.close();
            }
            //Todo: Fix this, so that attach package can be moved to mobicom mobicom.
            new MessageDatabaseService(context).updateInternalFilePath(message.getKeyString(), file.getAbsolutePath());

            ArrayList<String> arrayList = new ArrayList<String>();
            arrayList.add(file.getAbsolutePath());
            message.setFilePaths(arrayList);

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            Log.e(TAG, "File not found on server");
        } catch (Exception ex) {
            //If partial file got created delete it, we try to download it again
            if (file != null && file.exists()) {
                Log.i(TAG, " Exception occured while downloading :" + file.getAbsolutePath());
                file.delete();
            }
            ex.printStackTrace();
            Log.e(TAG, "Exception fetching file from server");
        }
    }


    public Bitmap loadMessageImage(Context context, String url) {
        try {
            Bitmap attachedImage = null;

            if (attachedImage == null) {
                InputStream in = new java.net.URL(url).openStream();
                if (in != null) {
                    attachedImage = BitmapFactory.decodeStream(in);
                }
            }
            return attachedImage;
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            Log.e(TAG, "File not found on server: " + ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "Exception fetching file from server: " + ex.getMessage());
        }

        return null;
    }

    public String uploadBlobImage(String path) throws UnsupportedEncodingException {
        try{
            ApplozicMultipartUtility multipart = new ApplozicMultipartUtility(getUploadKey(),"UTF-8",context);
            multipart.addFilePart("files[]", new File(path));
            return multipart.getResponse();
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public String getUploadKey() {
        return httpRequestUtils.getResponse(getCredentials(), getFileUploadUrl() + "?" + new Date().getTime(), "text/plain", "text/plain");
    }
}
