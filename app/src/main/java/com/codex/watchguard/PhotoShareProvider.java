package com.codex.watchguard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class PhotoShareProvider extends ContentProvider {
    static final String AUTHORITY_SUFFIX = ".photo-share";

    static Uri uriFor(Context context, String path) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath("photo")
                .appendQueryParameter("path", path == null ? "" : path)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        File file = safeFile(uri);
        if (file == null) {
            return "image/jpeg";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    name.substring(dot + 1).toLowerCase()
            );
            if (type != null) {
                return type;
            }
        }
        return "image/jpeg";
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        File file = safeFile(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                values[i] = file == null ? "photo.jpg" : file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                values[i] = file == null ? 0L : file.length();
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.contains("w")) {
            throw new FileNotFoundException("read only");
        }
        File file = safeFile(uri);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("photo missing");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File safeFile(Uri uri) {
        if (uri == null || getContext() == null) {
            return null;
        }
        String path = uri.getQueryParameter("path");
        if (path == null || path.length() == 0) {
            return null;
        }
        try {
            File file = new File(path).getCanonicalFile();
            if (isUnderAllowedRoot(file)) {
                return file;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private boolean isUnderAllowedRoot(File file) throws IOException {
        Context context = getContext();
        if (context == null || file == null) {
            return false;
        }
        File dcimCamera = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "Camera"
        );
        File externalPictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File privatePictures = new File(context.getFilesDir(), "Pictures");
        return isUnder(file, dcimCamera)
                || isUnder(file, externalPictures)
                || isUnder(file, privatePictures);
    }

    private boolean isUnder(File file, File root) throws IOException {
        if (file == null || root == null) {
            return false;
        }
        String filePath = file.getCanonicalPath();
        String rootPath = root.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }
}
