package com.nfjs.helloworldas;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;

public class MyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri pdfUri = intent.getData();
            if (pdfUri != null) {
                copyAndRenamePdf(pdfUri);
            }
        }
        
        finish(); 
    }

    // Helper method to extract the original filename from the Uri
    private String getOriginalFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "unknown.pdf";
    }

    private void copyAndRenamePdf(Uri sourceUri) {
        try {
            // 1. Get original name
            String originalName = getOriginalFileName(sourceUri);
            String newFileName = originalName;

            // 2. Remove the first 25 characters (with a safety check so it doesn't crash on short names)
            if (newFileName.length() > 28) {
                newFileName = newFileName.substring(28);
            } else {
                // If it's shorter than 25 characters, just prepend "saved_" so it doesn't crash
                newFileName = "saved_" + newFileName;
            }

            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            
            // 3. Save directly to the Downloads folder
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri destUri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
            
            if (destUri != null) {
                try (OutputStream outputStream = getContentResolver().openOutputStream(destUri);
                     InputStream inputStream = getContentResolver().openInputStream(sourceUri)) {
                     
                    if (outputStream != null && inputStream != null) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                }
                Toast.makeText(this, "Saved to Downloads: " + newFileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to copy PDF", Toast.LENGTH_SHORT).show();
        }
    }
}
