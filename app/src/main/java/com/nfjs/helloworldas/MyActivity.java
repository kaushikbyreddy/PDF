package com.nfjs.helloworldas;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;

public class MyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Check for the special "All Files Access" permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please allow 'All Files Access' so the app can read folder.txt", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                finish();
                return; // Stop here until permission is granted
            }
        }

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri pdfUri = intent.getData();
            if (pdfUri != null) {
                processPdf(pdfUri);
            }
        }
        
        finish(); 
    }

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

    private void processPdf(Uri sourceUri) {
        try {
            String subFolder = "";
            int count = 1;

            // Look for folder.txt in the main Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File configFile = new File(downloadsDir, "folder.txt");

            // 2. Read folder.txt or create it if missing
            if (configFile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
                    String line1 = br.readLine();
                    if (line1 != null) subFolder = line1.trim();
                    
                    String line2 = br.readLine();
                    if (line2 != null) {
                        try {
                            count = Integer.parseInt(line2.trim());
                        } catch (NumberFormatException e) {
                            count = 1; // Default to 1 if it gets corrupted
                        }
                    }
                }
            } else {
                // Creates a template file for you so you can edit it later
                try (FileWriter fw = new FileWriter(configFile)) {
                    fw.write("/GATE/C/\n01\n");
                }
                subFolder = "/GATE/C/";
            }

            // Format count to always have a leading zero if under 10
            String currentCountStr = String.format("%02d", count);
            
            // Clean up the folder path so Android doesn't crash on extra slashes
            String cleanSubFolder = subFolder;
            if (cleanSubFolder.startsWith("/")) cleanSubFolder = cleanSubFolder.substring(1);
            if (cleanSubFolder.endsWith("/")) cleanSubFolder = cleanSubFolder.substring(0, cleanSubFolder.length() - 1);

            // 3. Process the file name
            String originalName = getOriginalFileName(sourceUri);
            String newFileName = originalName;

            if (newFileName.length() > 28) {
                newFileName = newFileName.substring(28);
            } else {
                newFileName = "saved_" + newFileName;
            }
            
            // Prepend the counter
            newFileName = currentCountStr + "_" + newFileName;

            // 4. Save to the new destination
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            
            String destPath = Environment.DIRECTORY_DOWNLOADS;
            if (!cleanSubFolder.isEmpty()) {
                destPath += "/" + cleanSubFolder;
            }
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, destPath);

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
                Toast.makeText(this, "Saved to " + destPath + " as " + newFileName, Toast.LENGTH_SHORT).show();
                
                // 5. Increment the count and write it back to folder.txt
                int nextCount = count + 1;
                String nextCountStr = String.format("%02d", nextCount);
                try (FileWriter fw = new FileWriter(configFile)) {
                    fw.write(subFolder + "\n" + nextCountStr + "\n");
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
